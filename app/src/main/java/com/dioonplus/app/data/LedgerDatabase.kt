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
        db.execSQL("""
            CREATE TABLE parties (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT NOT NULL DEFAULT '',
                type TEXT NOT NULL CHECK(type IN ('CUSTOMER','SUPPLIER')),
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE ledger_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                party_id INTEGER NOT NULL,
                entry_type TEXT NOT NULL CHECK(entry_type IN ('GAVE','TOOK')),
                amount_cents INTEGER NOT NULL CHECK(amount_cents > 0),
                note TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                due_at INTEGER,
                parent_entry_id INTEGER,
                FOREIGN KEY(party_id) REFERENCES parties(id) ON DELETE CASCADE,
                FOREIGN KEY(parent_entry_id) REFERENCES ledger_entries(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_parties_type_name ON parties(type, name)")
        db.execSQL("CREATE INDEX idx_entries_party_date ON ledger_entries(party_id, created_at DESC)")
        db.execSQL("CREATE INDEX idx_entries_date ON ledger_entries(created_at DESC)")
        db.execSQL("CREATE INDEX idx_entries_due ON ledger_entries(due_at)")
        db.execSQL("CREATE INDEX idx_entries_parent ON ledger_entries(parent_entry_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE ledger_entries ADD COLUMN due_at INTEGER")
            db.execSQL("ALTER TABLE ledger_entries ADD COLUMN parent_entry_id INTEGER")
        }
        if (oldVersion < 3) {
            val overflow = db.rawQuery(
                "SELECT 1 FROM ledger_entries WHERE amount_cents > ? LIMIT 1",
                arrayOf((Long.MAX_VALUE / 10L).toString()),
            ).use { it.moveToFirst() }
            check(!overflow) { "تعذر ترقية مبالغ قاعدة البيانات بأمان" }

            db.execSQL("DROP INDEX IF EXISTS idx_entries_party_date")
            db.execSQL("DROP INDEX IF EXISTS idx_entries_date")
            db.execSQL("DROP INDEX IF EXISTS idx_entries_due")
            db.execSQL("DROP INDEX IF EXISTS idx_entries_parent")
            db.execSQL("ALTER TABLE ledger_entries RENAME TO ledger_entries_old")
            db.execSQL("""
                CREATE TABLE ledger_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    party_id INTEGER NOT NULL,
                    entry_type TEXT NOT NULL CHECK(entry_type IN ('GAVE','TOOK')),
                    amount_cents INTEGER NOT NULL CHECK(amount_cents > 0),
                    note TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL,
                    due_at INTEGER,
                    parent_entry_id INTEGER,
                    FOREIGN KEY(party_id) REFERENCES parties(id) ON DELETE CASCADE,
                    FOREIGN KEY(parent_entry_id) REFERENCES ledger_entries(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO ledger_entries(
                    id, party_id, entry_type, amount_cents, note, created_at, due_at, parent_entry_id
                )
                SELECT id, party_id, entry_type, amount_cents * 10, note, created_at, due_at, parent_entry_id
                FROM ledger_entries_old
                ORDER BY CASE WHEN parent_entry_id IS NULL THEN 0 ELSE 1 END, id
            """.trimIndent())
            db.execSQL("DROP TABLE ledger_entries_old")
            db.execSQL("CREATE INDEX idx_entries_party_date ON ledger_entries(party_id, created_at DESC)")
            db.execSQL("CREATE INDEX idx_entries_date ON ledger_entries(created_at DESC)")
            db.execSQL("CREATE INDEX idx_entries_due ON ledger_entries(due_at)")
            db.execSQL("CREATE INDEX idx_entries_parent ON ledger_entries(parent_entry_id)")
            val foreignKeyError = db.rawQuery("PRAGMA foreign_key_check", null).use { it.moveToFirst() }
            check(!foreignKeyError) { "تعذر التحقق من سلامة علاقات الدفعات" }
        }
    }

    @Synchronized
    fun addParty(name: String, phone: String, type: PartyType): Long {
        val cleanName = name.trim()
        val cleanPhone = phone.trim()
        require(cleanName.isNotEmpty()) { "اسم الحساب مطلوب" }
        ensurePartyIsUnique(cleanName, cleanPhone, type)
        return writableDatabase.insertOrThrow("parties", null, ContentValues().apply {
            put("name", cleanName)
            put("phone", cleanPhone)
            put("type", type.name)
            put("created_at", System.currentTimeMillis())
        })
    }

    @Synchronized
    fun updateParty(id: Long, name: String, phone: String) {
        val existing = getParty(id) ?: error("الحساب غير موجود")
        val cleanName = name.trim()
        val cleanPhone = phone.trim()
        require(cleanName.isNotEmpty()) { "اسم الحساب مطلوب" }
        ensurePartyIsUnique(cleanName, cleanPhone, existing.type, excludeId = id)
        val changed = writableDatabase.update("parties", ContentValues().apply {
            put("name", cleanName)
            put("phone", cleanPhone)
        }, "id = ?", arrayOf(id.toString()))
        check(changed == 1) { "تعذر تعديل الحساب" }
    }

    @Synchronized
    fun deleteParty(id: Long) {
        val changed = writableDatabase.delete("parties", "id = ?", arrayOf(id.toString()))
        check(changed == 1) { "الحساب غير موجود" }
    }

    @Synchronized
    fun addEntry(
        partyId: Long,
        type: EntryType,
        amountCents: Long,
        note: String,
        createdAt: Long = System.currentTimeMillis(),
        dueAt: Long? = null,
    ): Long {
        require(amountCents > 0) { "المبلغ يجب أن يكون أكبر من صفر" }
        require(createdAt > 0L) { "تاريخ الحركة غير صحيح" }
        require(getParty(partyId) != null) { "الحساب غير موجود" }
        return writableDatabase.insertOrThrow("ledger_entries", null, ContentValues().apply {
            put("party_id", partyId)
            put("entry_type", type.name)
            put("amount_cents", amountCents)
            put("note", note.trim())
            put("created_at", createdAt)
            if (dueAt == null) putNull("due_at") else put("due_at", dueAt)
            putNull("parent_entry_id")
        })
    }

    @Synchronized
    fun updateEntry(
        entryId: Long,
        type: EntryType,
        amountCents: Long,
        note: String,
        createdAt: Long,
        dueAt: Long?,
    ) {
        require(amountCents > 0) { "المبلغ يجب أن يكون أكبر من صفر" }
        val existing = getEntry(entryId) ?: error("الحركة غير موجودة")
        require(!existing.isPayment) { "لا يمكن تعديل دفعة السداد بهذه الطريقة" }
        require(existing.paidCents == 0L || type == existing.type) {
            "لا يمكن تغيير نوع الدين بعد تسجيل دفعة"
        }
        require(amountCents >= existing.paidCents) { "المبلغ الجديد أقل من الدفعات المسجلة" }
        val changed = writableDatabase.update("ledger_entries", ContentValues().apply {
            put("entry_type", type.name)
            put("amount_cents", amountCents)
            put("note", note.trim())
            put("created_at", createdAt)
            if (dueAt == null) putNull("due_at") else put("due_at", dueAt)
        }, "id = ?", arrayOf(entryId.toString()))
        check(changed == 1) { "الحركة غير موجودة" }
    }

    @Synchronized
    fun addPayment(parentEntryId: Long, amountCents: Long, note: String, createdAt: Long): Long {
        val parent = getEntry(parentEntryId) ?: error("الدين الأصلي غير موجود")
        require(!parent.isPayment) { "لا يمكن ربط دفعة بدفعة أخرى" }
        require(amountCents > 0) { "قيمة الدفعة يجب أن تكون أكبر من صفر" }
        require(amountCents <= parent.remainingCents) { "قيمة الدفعة أكبر من المبلغ المتبقي" }
        val paymentType = if (parent.type == EntryType.GAVE) EntryType.TOOK else EntryType.GAVE
        return writableDatabase.insertOrThrow("ledger_entries", null, ContentValues().apply {
            put("party_id", parent.partyId)
            put("entry_type", paymentType.name)
            put("amount_cents", amountCents)
            put("note", note.trim().ifBlank { "دفعة سداد" })
            put("created_at", createdAt)
            putNull("due_at")
            put("parent_entry_id", parentEntryId)
        })
    }

    @Synchronized
    fun deleteEntry(entryId: Long) {
        val entry = getEntry(entryId) ?: error("الحركة غير موجودة")
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (!entry.isPayment) db.delete("ledger_entries", "parent_entry_id = ?", arrayOf(entryId.toString()))
            val changed = db.delete("ledger_entries", "id = ?", arrayOf(entryId.toString()))
            check(changed == 1) { "الحركة غير موجودة" }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getEntry(id: Long): LedgerEntry? = queryEntries("WHERE e.id = ?", arrayOf(id.toString())).firstOrNull()

    fun listEntries(partyId: Long): List<LedgerEntry> =
        queryEntries("WHERE e.party_id = ?", arrayOf(partyId.toString()))

    private fun queryEntries(whereClause: String, args: Array<String>): List<LedgerEntry> {
        val sql = """
            SELECT e.id, e.party_id, e.entry_type, e.amount_cents, e.note, e.created_at,
                   e.due_at, e.parent_entry_id,
                   CASE WHEN e.parent_entry_id IS NULL THEN
                       COALESCE((SELECT SUM(p.amount_cents) FROM ledger_entries p WHERE p.parent_entry_id = e.id), 0)
                   ELSE 0 END AS paid_cents
            FROM ledger_entries e
            $whereClause
            ORDER BY e.created_at DESC, e.id DESC
        """.trimIndent()
        return readableDatabase.rawQuery(sql, args).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toLedgerEntry()) }
        }
    }

    fun listDueItems(): List<DueItem> {
        val sql = """
            SELECT e.id, e.party_id, p.name, p.phone, e.entry_type, e.amount_cents,
                   e.note, e.due_at,
                   COALESCE((SELECT SUM(pay.amount_cents) FROM ledger_entries pay WHERE pay.parent_entry_id = e.id), 0) AS paid_cents
            FROM ledger_entries e
            INNER JOIN parties p ON p.id = e.party_id
            WHERE e.parent_entry_id IS NULL AND e.due_at IS NOT NULL
              AND e.amount_cents > COALESCE((SELECT SUM(pay.amount_cents) FROM ledger_entries pay WHERE pay.parent_entry_id = e.id), 0)
            ORDER BY e.due_at ASC, e.id ASC
        """.trimIndent()
        return readableDatabase.rawQuery(sql, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val original = cursor.getLong(cursor.getColumnIndexOrThrow("amount_cents"))
                    val paid = cursor.getLong(cursor.getColumnIndexOrThrow("paid_cents"))
                    add(DueItem(
                        entryId = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        partyId = cursor.getLong(cursor.getColumnIndexOrThrow("party_id")),
                        partyName = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        partyPhone = cursor.getString(cursor.getColumnIndexOrThrow("phone")),
                        entryType = EntryType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("entry_type"))),
                        originalCents = original,
                        paidCents = paid,
                        remainingCents = (original - paid).coerceAtLeast(0),
                        dueAt = cursor.getLong(cursor.getColumnIndexOrThrow("due_at")),
                        note = cursor.getString(cursor.getColumnIndexOrThrow("note")),
                    ))
                }
            }
        }
    }

    fun getDueItem(entryId: Long): DueItem? = listDueItems().firstOrNull { it.entryId == entryId }

    fun listParties(type: PartyType, search: String = ""): List<Party> {
        val pattern = "%${search.trim()}%"
        val sql = """
            SELECT p.id, p.name, p.phone, p.type,
                   COALESCE(SUM(CASE WHEN e.entry_type = 'GAVE' THEN e.amount_cents ELSE -e.amount_cents END), 0) AS balance_cents,
                   MAX(e.created_at) AS last_activity
            FROM parties p LEFT JOIN ledger_entries e ON e.party_id = p.id
            WHERE p.type = ? AND (p.name LIKE ? OR p.phone LIKE ?)
            GROUP BY p.id, p.name, p.phone, p.type
            ORDER BY CASE WHEN MAX(e.created_at) IS NULL THEN 1 ELSE 0 END, MAX(e.created_at) DESC, p.name COLLATE NOCASE ASC
        """.trimIndent()
        return readableDatabase.rawQuery(sql, arrayOf(type.name, pattern, pattern)).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toParty()) }
        }
    }

    fun getParty(id: Long): Party? {
        val sql = """
            SELECT p.id, p.name, p.phone, p.type,
                   COALESCE(SUM(CASE WHEN e.entry_type = 'GAVE' THEN e.amount_cents ELSE -e.amount_cents END), 0) AS balance_cents,
                   MAX(e.created_at) AS last_activity
            FROM parties p LEFT JOIN ledger_entries e ON e.party_id = p.id
            WHERE p.id = ? GROUP BY p.id, p.name, p.phone, p.type
        """.trimIndent()
        return readableDatabase.rawQuery(sql, arrayOf(id.toString())).use { if (it.moveToFirst()) it.toParty() else null }
    }

    fun dashboardSummary(): DashboardSummary {
        val sql = """
            SELECT COALESCE(SUM(CASE WHEN balance_cents > 0 THEN balance_cents ELSE 0 END), 0) receivable,
                   COALESCE(SUM(CASE WHEN balance_cents < 0 THEN -balance_cents ELSE 0 END), 0) payable
            FROM (SELECT p.id, COALESCE(SUM(CASE WHEN e.entry_type='GAVE' THEN e.amount_cents ELSE -e.amount_cents END),0) balance_cents
                  FROM parties p LEFT JOIN ledger_entries e ON e.party_id=p.id GROUP BY p.id)
        """.trimIndent()
        return readableDatabase.rawQuery(sql, null).use { cursor ->
            if (!cursor.moveToFirst()) DashboardSummary() else DashboardSummary(
                cursor.getLong(cursor.getColumnIndexOrThrow("receivable")),
                cursor.getLong(cursor.getColumnIndexOrThrow("payable")),
            )
        }
    }

    fun reportRows(): List<ReportRow> {
        val sql = """
            SELECT e.id entry_id, p.id party_id, p.name party_name, p.phone party_phone, p.type party_type,
                   e.entry_type, e.amount_cents, e.note, e.created_at
            FROM ledger_entries e INNER JOIN parties p ON p.id=e.party_id
            ORDER BY e.created_at DESC, e.id DESC
        """.trimIndent()
        return readableDatabase.rawQuery(sql, null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(ReportRow(
                cursor.getLong(cursor.getColumnIndexOrThrow("entry_id")),
                cursor.getLong(cursor.getColumnIndexOrThrow("party_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("party_name")),
                cursor.getString(cursor.getColumnIndexOrThrow("party_phone")),
                PartyType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("party_type"))),
                EntryType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("entry_type"))),
                cursor.getLong(cursor.getColumnIndexOrThrow("amount_cents")),
                cursor.getString(cursor.getColumnIndexOrThrow("note")),
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            )) }
        }
    }

    fun lastDailyTotals(dayCount: Int = 7): List<DailyTotal> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(dayCount - 1))
        }
        val firstDay = calendar.timeInMillis
        val totals = LinkedHashMap<Long, LongArray>()
        repeat(dayCount) { totals[calendar.timeInMillis] = longArrayOf(0, 0); calendar.add(Calendar.DAY_OF_YEAR, 1) }
        readableDatabase.rawQuery("SELECT entry_type, amount_cents, created_at FROM ledger_entries WHERE created_at >= ? ORDER BY created_at", arrayOf(firstDay.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val day = Calendar.getInstance().apply {
                    timeInMillis = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val bucket = totals[day] ?: continue
                val amount = cursor.getLong(cursor.getColumnIndexOrThrow("amount_cents"))
                if (EntryType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("entry_type"))) == EntryType.GAVE) bucket[0] += amount else bucket[1] += amount
            }
        }
        return totals.map { DailyTotal(it.key, it.value[0], it.value[1]) }
    }

    private fun ensurePartyIsUnique(name: String, phone: String, type: PartyType, excludeId: Long? = null) {
        val conditions = mutableListOf("type = ?")
        val args = mutableListOf(type.name)
        val duplicates = mutableListOf("name = ? COLLATE NOCASE")
        args += name
        if (phone.isNotBlank()) { duplicates += "phone = ?"; args += phone }
        conditions += "(${duplicates.joinToString(" OR ")})"
        if (excludeId != null) { conditions += "id != ?"; args += excludeId.toString() }
        val exists = readableDatabase.rawQuery("SELECT 1 FROM parties WHERE ${conditions.joinToString(" AND ")} LIMIT 1", args.toTypedArray()).use { it.moveToFirst() }
        require(!exists) { "يوجد حساب آخر بنفس الاسم أو رقم الهاتف" }
    }

    private fun Cursor.toLedgerEntry(): LedgerEntry = LedgerEntry(
        id = getLong(getColumnIndexOrThrow("id")),
        partyId = getLong(getColumnIndexOrThrow("party_id")),
        type = EntryType.valueOf(getString(getColumnIndexOrThrow("entry_type"))),
        amountCents = getLong(getColumnIndexOrThrow("amount_cents")),
        note = getString(getColumnIndexOrThrow("note")),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        dueAt = getColumnIndexOrThrow("due_at").let { if (isNull(it)) null else getLong(it) },
        parentEntryId = getColumnIndexOrThrow("parent_entry_id").let { if (isNull(it)) null else getLong(it) },
        paidCents = getLong(getColumnIndexOrThrow("paid_cents")),
    )

    private fun Cursor.toParty(): Party = Party(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        phone = getString(getColumnIndexOrThrow("phone")),
        type = PartyType.valueOf(getString(getColumnIndexOrThrow("type"))),
        balanceCents = getLong(getColumnIndexOrThrow("balance_cents")),
        lastActivityAt = getColumnIndexOrThrow("last_activity").let { if (isNull(it)) null else getLong(it) },
    )

    companion object {
        private const val DATABASE_NAME = "dioon_plus.db"
        private const val DATABASE_VERSION = 3
    }
}
