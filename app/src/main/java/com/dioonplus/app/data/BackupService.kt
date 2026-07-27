package com.dioonplus.app.data

import android.content.ContentValues
import org.json.JSONArray
import org.json.JSONObject

class BackupService(private val database: LedgerDatabase) {
    fun exportJson(): String {
        val root = JSONObject().apply {
            put("format", "dioonplus-backup")
            put("version", 1)
            put("createdAt", System.currentTimeMillis())
        }

        val parties = JSONArray()
        database.readableDatabase.rawQuery(
            "SELECT id, name, phone, type, created_at FROM parties ORDER BY id",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                parties.put(
                    JSONObject().apply {
                        put("id", cursor.getLong(cursor.getColumnIndexOrThrow("id")))
                        put("name", cursor.getString(cursor.getColumnIndexOrThrow("name")))
                        put("phone", cursor.getString(cursor.getColumnIndexOrThrow("phone")))
                        put("type", cursor.getString(cursor.getColumnIndexOrThrow("type")))
                        put("createdAt", cursor.getLong(cursor.getColumnIndexOrThrow("created_at")))
                    },
                )
            }
        }

        val entries = JSONArray()
        database.readableDatabase.rawQuery(
            "SELECT id, party_id, entry_type, amount_cents, note, created_at FROM ledger_entries ORDER BY id",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                entries.put(
                    JSONObject().apply {
                        put("id", cursor.getLong(cursor.getColumnIndexOrThrow("id")))
                        put("partyId", cursor.getLong(cursor.getColumnIndexOrThrow("party_id")))
                        put("entryType", cursor.getString(cursor.getColumnIndexOrThrow("entry_type")))
                        put("amountCents", cursor.getLong(cursor.getColumnIndexOrThrow("amount_cents")))
                        put("note", cursor.getString(cursor.getColumnIndexOrThrow("note")))
                        put("createdAt", cursor.getLong(cursor.getColumnIndexOrThrow("created_at")))
                    },
                )
            }
        }

        root.put("parties", parties)
        root.put("entries", entries)
        return root.toString(2)
    }

    fun importJson(json: String) {
        val root = JSONObject(json)
        require(root.optString("format") == "dioonplus-backup") { "الملف ليس نسخة احتياطية من ديون بلس" }
        require(root.optInt("version") == 1) { "إصدار النسخة الاحتياطية غير مدعوم" }
        val parties = root.getJSONArray("parties")
        val entries = root.getJSONArray("entries")
        require(parties.length() <= 100_000 && entries.length() <= 1_000_000) { "حجم النسخة الاحتياطية غير صالح" }

        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete("ledger_entries", null, null)
            db.delete("parties", null, null)

            for (index in 0 until parties.length()) {
                val item = parties.getJSONObject(index)
                val type = PartyType.valueOf(item.getString("type"))
                val name = item.getString("name").trim()
                require(name.isNotEmpty()) { "يوجد حساب بدون اسم" }
                val values = ContentValues().apply {
                    put("id", item.getLong("id"))
                    put("name", name)
                    put("phone", item.optString("phone"))
                    put("type", type.name)
                    put("created_at", item.getLong("createdAt"))
                }
                db.insertOrThrow("parties", null, values)
            }

            for (index in 0 until entries.length()) {
                val item = entries.getJSONObject(index)
                val entryType = EntryType.valueOf(item.getString("entryType"))
                val amountCents = item.getLong("amountCents")
                require(amountCents > 0) { "يوجد مبلغ غير صالح في النسخة" }
                val values = ContentValues().apply {
                    put("id", item.getLong("id"))
                    put("party_id", item.getLong("partyId"))
                    put("entry_type", entryType.name)
                    put("amount_cents", amountCents)
                    put("note", item.optString("note"))
                    put("created_at", item.getLong("createdAt"))
                }
                db.insertOrThrow("ledger_entries", null, values)
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
