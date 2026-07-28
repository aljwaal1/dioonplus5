package com.dioonplus.app.data

import android.content.ContentValues
import org.json.JSONArray
import org.json.JSONObject

class BackupService(private val database: LedgerDatabase) {
    fun exportJson(): String {
        val root = JSONObject().apply { put("format", "dioonplus-backup"); put("version", 2); put("createdAt", System.currentTimeMillis()) }
        val parties = JSONArray()
        database.readableDatabase.rawQuery("SELECT id,name,phone,type,created_at FROM parties ORDER BY id", null).use { c ->
            while (c.moveToNext()) parties.put(JSONObject().apply {
                put("id", c.getLong(0)); put("name", c.getString(1)); put("phone", c.getString(2)); put("type", c.getString(3)); put("createdAt", c.getLong(4))
            })
        }
        val entries = JSONArray()
        database.readableDatabase.rawQuery("SELECT id,party_id,entry_type,amount_cents,note,created_at,due_at,parent_entry_id FROM ledger_entries ORDER BY id", null).use { c ->
            while (c.moveToNext()) entries.put(JSONObject().apply {
                put("id", c.getLong(0)); put("partyId", c.getLong(1)); put("entryType", c.getString(2)); put("amountCents", c.getLong(3))
                put("note", c.getString(4)); put("createdAt", c.getLong(5))
                if (c.isNull(6)) put("dueAt", JSONObject.NULL) else put("dueAt", c.getLong(6))
                if (c.isNull(7)) put("parentEntryId", JSONObject.NULL) else put("parentEntryId", c.getLong(7))
            })
        }
        return root.put("parties", parties).put("entries", entries).toString(2)
    }

    fun importJson(json: String) {
        val root = JSONObject(json)
        require(root.optString("format") == "dioonplus-backup") { "الملف ليس نسخة احتياطية من ديون بلس" }
        val version = root.optInt("version")
        require(version == 1 || version == 2) { "إصدار النسخة الاحتياطية غير مدعوم" }
        val parties = root.getJSONArray("parties"); val entries = root.getJSONArray("entries")
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete("ledger_entries", null, null); db.delete("parties", null, null)
            for (i in 0 until parties.length()) {
                val item = parties.getJSONObject(i)
                db.insertOrThrow("parties", null, ContentValues().apply {
                    put("id", item.getLong("id")); put("name", item.getString("name").trim()); put("phone", item.optString("phone"))
                    put("type", PartyType.valueOf(item.getString("type")).name); put("created_at", item.getLong("createdAt"))
                })
            }
            for (i in 0 until entries.length()) {
                val item = entries.getJSONObject(i); val amount = item.getLong("amountCents"); require(amount > 0)
                db.insertOrThrow("ledger_entries", null, ContentValues().apply {
                    put("id", item.getLong("id")); put("party_id", item.getLong("partyId")); put("entry_type", EntryType.valueOf(item.getString("entryType")).name)
                    put("amount_cents", amount); put("note", item.optString("note")); put("created_at", item.getLong("createdAt"))
                    if (version >= 2 && !item.isNull("dueAt")) put("due_at", item.getLong("dueAt")) else putNull("due_at")
                    if (version >= 2 && !item.isNull("parentEntryId")) put("parent_entry_id", item.getLong("parentEntryId")) else putNull("parent_entry_id")
                })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
