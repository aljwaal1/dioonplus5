package com.dioonplus.app.data

import android.content.ContentValues
import org.json.JSONArray
import org.json.JSONObject

class BackupService(private val database: LedgerDatabase) {
    private data class BackupParty(
        val id: Long,
        val name: String,
        val phone: String,
        val type: PartyType,
        val createdAt: Long,
    )

    private data class BackupEntry(
        val id: Long,
        val partyId: Long,
        val entryType: EntryType,
        val amountCents: Long,
        val note: String,
        val createdAt: Long,
        val dueAt: Long?,
        val parentEntryId: Long?,
    )

    fun exportJson(): String {
        val root = JSONObject().apply {
            put("format", "dioonplus-backup")
            put("version", 3)
            put("storageFractionDigits", 3)
            put("createdAt", System.currentTimeMillis())
        }
        val parties = JSONArray()
        database.readableDatabase.rawQuery(
            "SELECT id,name,phone,type,created_at FROM parties ORDER BY id",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) parties.put(JSONObject().apply {
                put("id", cursor.getLong(0))
                put("name", cursor.getString(1))
                put("phone", cursor.getString(2))
                put("type", cursor.getString(3))
                put("createdAt", cursor.getLong(4))
            })
        }
        val entries = JSONArray()
        database.readableDatabase.rawQuery(
            "SELECT id,party_id,entry_type,amount_cents,note,created_at,due_at,parent_entry_id FROM ledger_entries ORDER BY id",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) entries.put(JSONObject().apply {
                put("id", cursor.getLong(0))
                put("partyId", cursor.getLong(1))
                put("entryType", cursor.getString(2))
                put("amountCents", cursor.getLong(3))
                put("note", cursor.getString(4))
                put("createdAt", cursor.getLong(5))
                if (cursor.isNull(6)) put("dueAt", JSONObject.NULL) else put("dueAt", cursor.getLong(6))
                if (cursor.isNull(7)) put("parentEntryId", JSONObject.NULL) else put("parentEntryId", cursor.getLong(7))
            })
        }
        return root.put("parties", parties).put("entries", entries).toString(2)
    }

    fun importJson(json: String) {
        require(json.length <= MAX_BACKUP_CHARS) { "حجم ملف النسخة الاحتياطية كبير جداً" }
        val root = JSONObject(json)
        require(root.optString("format") == "dioonplus-backup") {
            "الملف ليس نسخة احتياطية من ديون بلس"
        }
        val version = root.optInt("version")
        require(version in 1..3) { "إصدار النسخة الاحتياطية غير مدعوم" }
        val partyArray = root.getJSONArray("parties")
        val entryArray = root.getJSONArray("entries")
        require(partyArray.length() <= MAX_PARTIES && entryArray.length() <= MAX_ENTRIES) {
            "حجم النسخة الاحتياطية غير صالح"
        }

        val parties = buildList {
            val ids = HashSet<Long>()
            for (index in 0 until partyArray.length()) {
                val item = partyArray.getJSONObject(index)
                val id = item.getLong("id")
                val name = item.getString("name").trim()
                val phone = item.optString("phone").trim().takeUnless { it == "null" }.orEmpty()
                val createdAt = item.getLong("createdAt")
                require(id > 0 && ids.add(id)) { "يوجد رقم حساب مكرر أو غير صالح" }
                require(name.isNotEmpty() && name.length <= 200) { "يوجد اسم حساب غير صالح" }
                require(phone.length <= 100 && createdAt > 0) { "يوجد حساب ببيانات غير صالحة" }
                add(BackupParty(id, name, phone, PartyType.valueOf(item.getString("type")), createdAt))
            }
        }
        val partyIds = parties.mapTo(HashSet()) { it.id }

        val entries = buildList {
            val ids = HashSet<Long>()
            for (index in 0 until entryArray.length()) {
                val item = entryArray.getJSONObject(index)
                val id = item.getLong("id")
                val partyId = item.getLong("partyId")
                val rawAmount = item.getLong("amountCents")
                val amount = if (version < 3) Math.multiplyExact(rawAmount, 10L) else rawAmount
                val note = item.optString("note").takeUnless { it == "null" }.orEmpty()
                val createdAt = item.getLong("createdAt")
                val dueAt = if (version >= 2 && item.has("dueAt") && !item.isNull("dueAt")) item.getLong("dueAt") else null
                val parentId = if (version >= 2 && item.has("parentEntryId") && !item.isNull("parentEntryId")) item.getLong("parentEntryId") else null
                require(id > 0 && ids.add(id)) { "يوجد رقم حركة مكرر أو غير صالح" }
                require(partyId in partyIds) { "توجد حركة مرتبطة بحساب غير موجود" }
                require(amount > 0 && createdAt > 0 && note.length <= 10_000) { "توجد حركة ببيانات غير صالحة" }
                require(dueAt == null || dueAt > 0) { "يوجد تاريخ استحقاق غير صالح" }
                add(BackupEntry(id, partyId, EntryType.valueOf(item.getString("entryType")), amount, note, createdAt, dueAt, parentId))
            }
        }

        val entriesById = entries.associateBy { it.id }
        val paidByParent = HashMap<Long, Long>()
        entries.forEach { entry ->
            val parentId = entry.parentEntryId ?: return@forEach
            val parent = entriesById[parentId] ?: error("توجد دفعة مرتبطة بدين غير موجود")
            require(parent.parentEntryId == null) { "لا يمكن ربط دفعة بدفعة أخرى" }
            require(parent.partyId == entry.partyId) { "توجد دفعة مرتبطة بحساب مختلف" }
            require(parent.entryType != entry.entryType) { "نوع دفعة السداد غير صحيح" }
            paidByParent[parentId] = Math.addExact(paidByParent[parentId] ?: 0L, entry.amountCents)
        }
        paidByParent.forEach { (parentId, paid) ->
            require(paid <= entriesById.getValue(parentId).amountCents) {
                "إجمالي الدفعات أكبر من أصل الدين"
            }
        }

        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete("ledger_entries", null, null)
            db.delete("parties", null, null)
            parties.forEach { item ->
                db.insertOrThrow("parties", null, ContentValues().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("phone", item.phone)
                    put("type", item.type.name)
                    put("created_at", item.createdAt)
                })
            }
            entries.sortedWith(compareBy<BackupEntry> { it.parentEntryId != null }.thenBy { it.id }).forEach { item ->
                db.insertOrThrow("ledger_entries", null, ContentValues().apply {
                    put("id", item.id)
                    put("party_id", item.partyId)
                    put("entry_type", item.entryType.name)
                    put("amount_cents", item.amountCents)
                    put("note", item.note)
                    put("created_at", item.createdAt)
                    if (item.dueAt == null) putNull("due_at") else put("due_at", item.dueAt)
                    if (item.parentEntryId == null) putNull("parent_entry_id") else put("parent_entry_id", item.parentEntryId)
                })
            }
            val foreignKeyError = db.rawQuery("PRAGMA foreign_key_check", null).use { it.moveToFirst() }
            check(!foreignKeyError) { "فشل التحقق من سلامة النسخة الاحتياطية" }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        private const val MAX_BACKUP_CHARS = 50_000_000
        private const val MAX_PARTIES = 100_000
        private const val MAX_ENTRIES = 1_000_000
    }
}
