package com.dioonplus.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Calendar

class LedgerDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE parties (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT NOT NULL DEFAULT '',
                type TEXT NOT NULL CHECK(type IN ('CUSTOMER','SUPPLIER')),
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE ledger_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                party_id INTEGER NOT NULL,
                entry_type TEXT NOT NULL CHECK(entry_type IN ('GAVE','TOOK')),
                amount_cents INTEGER NOT NULL CHECK(amount_cents > 0),
                note TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                FOREIGN KEY(party_id) REFERENCES parties(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_parties_type_name ON parties(type, name)")
        db.execSQL("CREATE INDEX idx_entries_party_date ON ledger_entries(party_id, created_at DESC)")
        db.execSQL("CREATE INDEX idx_entries_date ON ledger_entries(created_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun addParty(name: String, phone: String, type: PartyType): Long {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "اسم الحساب مطلوب" }
        val values = ContentValues().apply {
            put("name", cleanName)
            put("phone", phone.trim())
            put("type", type.name)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insertOrThrow("parties", null, values)
    }

    @Synchronized
    fun updateParty(id: Long, name: String, phone: String) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "اسم الحساب مطلوب" }
        val values = ContentValues().apply {
            put("name", cleanName)
            put("phone", phone.trim())
        }
        writableDatabase.update("parties", values, "id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun deleteParty(id: Long) {
        writableDatabase.delete("parties", "id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun addEntry(
        partyId: Long,
        type: EntryType,
        amountCents: Long,
        note: String,
        createdAt: Long = System.currentTimeMillis(),
    ): Long {
        require(amountCents > 0) { "المبلغ يجب أن يكون أكبر من صفر" }
        val values = ContentValues().apply {
            put("party_id", partyId)
            put("entry_type", type.name)
            put("amount_cents", amountCents)
            put("note", note.trim())
            put("created_at", createdAt)
        }
        return writableDatabase.insertOrThrow("ledger_entries", null, values)
    }

    @Synchronized
    fun deleteEntry(entryId: Long) {
        writableDatabase.delete("ledger_entries", "id = ?", arrayOf(entryId.toString()))
    }

    fun listParties(type: PartyType, search: String = ""): List<Party> {
        val pattern = "%${search.trim()}%"
        val sql = """
            SELECT p.id, p.name, p.phone, p.type,
                   COALESCE(SUM(CASE WHEN e.entry_type = 'GAVE' THEN e.amount_cents ELSE -e.amount_cents END), 0) AS balance_cents,
                   MAX(e.created_at) AS last_activity
            FROM parties p
            LEFT JOIN ledger_entries e ON e.party_id = p.id
            WHERE p.type = ? AND (p.name LIKE ? OR p.phone LIKE ?)
            GROUP BY p.id, p.name, p.phone, p.type
            ORDER BY CASE WHEN MAX(e.created_at) IS NULL THEN 1 ELSE 0 END,
                     MAX(e.created_at) DESC,
                     p.name COLLATE NOCASE ASC
        """.trimIndent()
        return readableDatabase.rawQuery(sql, arrayOf(type.name, pattern, pattern)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toParty())
            }
        }
    }

    fun getParty(id: Long): Party? {
        val sql = """
            SELECT p.id, p.name, p.phone, p.type,
                   COALESCE(SUM(CASE WHEN e.entry_type = 'GAVE' THEN e.amount_cents ELSE -e.amount_cents END), 0) AS balance_cents,
                   MAX(e.created_at) AS last_activity
            FROM parties p
            LEFT JOIN ledger_entries e ON e.party_id = p.id
            WHERE p.id = ?
            GROUP BY p.id, p.name, p.phone, p.type
        """.trimIndent()
        return readableDatabase.rawQuery(sql, arrayOf(id.toString())).use { cursor ->
            if (cursor.moveToFirst()) cursor.toParty() else null
        }
    }

    fun listEntries(partyId: Long): List<LedgerEntry> {
        val sql = """
            SELECT id, party_id, entry_type, amount_cents, note, created_at
            FROM ledger_entries
            WHERE party_id = ?
            ORDER BY created_at DESC, id DESC
        """.trimIndent()
        return readableDatabase.rawQuery(sql, arrayOf(partyId.toString())).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        LedgerEntry(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                            partyId = cursor.getLong(cursor.getColumnIndexOrThrow("party_id")),
                            type = EntryType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("entry_type"))),
                            amountCents = cursor.getLong(cursor.getColumnIndexOrThrow("amount_cents")),
                            note = cursor.getString(cursor.getColumnIndexOrThrow("note")),
                            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        ),
                    )
                }
            }
        }
    }

    fun dashboardSummary(): DashboardSummary {
        val sql = """
            SELECT
                COALESCE(SUM(CASE WHEN balance_cents > 0 THEN balance_cents ELSE 0 END), 0) AS receivable,
                COALESCE(SUM(CASE WHEN balance_cents < 0 THEN -balance_cents ELSE 0 END), 0) AS payable
            FROM (
                SELECT p.id,
                       COALESCE(SUM(CASE WHEN e.entry_type = 'GAVE' THEN e.amount_cents ELSE -e.amount_cents END), 0) AS balance_cents
                FROM parties p
                LEFT JOIN ledger_entries e ON e.party_id = p.id
                GROUP BY p.id
            ) balances
        """.trimIndent()
        return readableDatabase.rawQuery(sql, null).use { cursor ->
            if (!cursor.moveToFirst()) DashboardSummary()
            else DashboardSummary(
                receivableCents = cursor.getLong(cursor.getColumnIndexOrThrow("receivable")),
                payableCents = cursor.getLong(cursor.getColumnIndexOrThrow("payable")),
            )
        }
    }

    fun reportRows(): List<ReportRow> {
        val sql = """
            SELECT e.id AS entry_id, p.name AS party_name, p.type AS party_type,
                   e.entry_type, e.amount_cents, e.note, e.created_at
            FROM ledger_entries e
            INNER JOIN parties p ON p.id = e.party_id
            ORDER BY e.created_at DESC, e.id DESC
        """.trimIndent()
        return readableDatabase.rawQuery(sql, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ReportRow(
                            entryId = cursor.getLong(cursor.getColumnIndexOrThrow("entry_id")),
                            partyName = cursor.getString(cursor.getColumnIndexOrThrow("party_name")),
                            partyType = PartyType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("party_type"))),
                            entryType = EntryType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("entry_type"))),
                            amountCents = cursor.getLong(cursor.getColumnIndexOrThrow("amount_cents")),
                            note = cursor.getString(cursor.getColumnIndexOrThrow("note")),
                            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        ),
                    )
                }
            }
        }
    }

    fun lastDailyTotals(dayCount: Int = 7): List<DailyTotal> {
        require(dayCount > 0)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(dayCount - 1))
        }
        val firstDay = calendar.timeInMillis
        val totals = LinkedHashMap<Long, LongArray>()
        repeat(dayCount) {
            totals[calendar.timeInMillis] = longArrayOf(0, 0)
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val sql = """
            SELECT entry_type, amount_cents, created_at
            FROM ledger_entries
            WHERE created_at >= ?
            ORDER BY created_at ASC
        """.trimIndent()
        readableDatabase.rawQuery(sql, arrayOf(firstDay.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                val day = Calendar.getInstance().apply {
                    timeInMillis = createdAt
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val bucket = totals[day] ?: continue
                val amount = cursor.getLong(cursor.getColumnIndexOrThrow("amount_cents"))
                when (EntryType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("entry_type")))) {
                    EntryType.GAVE -> bucket[0] += amount
                    EntryType.TOOK -> bucket[1] += amount
                }
            }
        }
        return totals.map { (day, values) ->
            DailyTotal(dayStartMillis = day, gaveCents = values[0], tookCents = values[1])
        }
    }

    private fun Cursor.toParty(): Party = Party(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        phone = getString(getColumnIndexOrThrow("phone")),
        type = PartyType.valueOf(getString(getColumnIndexOrThrow("type"))),
        balanceCents = getLong(getColumnIndexOrThrow("balance_cents")),
        lastActivityAt = getColumnIndexOrThrow("last_activity").let { index ->
            if (isNull(index)) null else getLong(index)
        },
    )

    companion object {
        private const val DATABASE_NAME = "dioon_plus.db"
        private const val DATABASE_VERSION = 1
    }
}
