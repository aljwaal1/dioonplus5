from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


write("app/src/main/java/com/dioonplus/app/data/Models.kt", '''package com.dioonplus.app.data

enum class PartyType { CUSTOMER, SUPPLIER }
enum class EntryType { GAVE, TOOK }
enum class DebtStatus { OPEN, PARTIAL, PAID }

data class Party(
    val id: Long,
    val name: String,
    val phone: String,
    val type: PartyType,
    val balanceCents: Long,
    val lastActivityAt: Long?,
)

data class LedgerEntry(
    val id: Long,
    val partyId: Long,
    val type: EntryType,
    val amountCents: Long,
    val note: String,
    val createdAt: Long,
    val dueAt: Long? = null,
    val parentEntryId: Long? = null,
    val paidCents: Long = 0,
) {
    val isPayment: Boolean get() = parentEntryId != null
    val remainingCents: Long get() = if (isPayment) 0 else (amountCents - paidCents).coerceAtLeast(0)
    val debtStatus: DebtStatus
        get() = when {
            isPayment || remainingCents == 0L -> DebtStatus.PAID
            paidCents > 0L -> DebtStatus.PARTIAL
            else -> DebtStatus.OPEN
        }
}

data class DueItem(
    val entryId: Long,
    val partyId: Long,
    val partyName: String,
    val partyPhone: String,
    val entryType: EntryType,
    val originalCents: Long,
    val paidCents: Long,
    val remainingCents: Long,
    val dueAt: Long,
    val note: String,
)

data class DueSummary(
    val overdueCount: Int = 0,
    val todayCount: Int = 0,
    val upcomingCount: Int = 0,
)

data class DashboardSummary(
    val receivableCents: Long = 0,
    val payableCents: Long = 0,
) {
    val netCents: Long get() = receivableCents - payableCents
}

data class ReportRow(
    val entryId: Long,
    val partyId: Long,
    val partyName: String,
    val partyPhone: String,
    val partyType: PartyType,
    val entryType: EntryType,
    val amountCents: Long,
    val note: String,
    val createdAt: Long,
)

data class DailyTotal(
    val dayStartMillis: Long,
    val gaveCents: Long,
    val tookCents: Long,
)
''')

write("app/src/main/java/com/dioonplus/app/data/LedgerDatabase.kt", '''package com.dioonplus.app.data

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
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_due ON ledger_entries(due_at)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_parent ON ledger_entries(parent_entry_id)")
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
        private const val DATABASE_VERSION = 2
    }
}
''')

write("app/src/main/java/com/dioonplus/app/reminder/DueReminderScheduler.kt", '''package com.dioonplus.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dioonplus.app.data.DueItem
import java.util.Calendar

class DueReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun reschedule(items: List<DueItem>) {
        items.forEach(::schedule)
    }

    fun schedule(item: DueItem) {
        val intent = Intent(context, DueReminderReceiver::class.java).putExtra(EXTRA_ENTRY_ID, item.entryId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            item.entryId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val reminderAt = Calendar.getInstance().apply {
            timeInMillis = item.dueAt
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val triggerAt = if (reminderAt > System.currentTimeMillis()) reminderAt else System.currentTimeMillis() + 60_000L
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    companion object { const val EXTRA_ENTRY_ID = "entry_id" }
}
''')

write("app/src/main/java/com/dioonplus/app/reminder/DueReminderReceiver.kt", '''package com.dioonplus.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dioonplus.app.MainActivity
import com.dioonplus.app.data.LedgerDatabase
import com.dioonplus.app.util.formatMoney

class DueReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getLongExtra(DueReminderScheduler.EXTRA_ENTRY_ID, -1L)
        if (entryId <= 0) return
        val item = LedgerDatabase(context).getDueItem(entryId) ?: return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "تذكيرات الديون", NotificationManager.IMPORTANCE_DEFAULT))
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val openIntent = PendingIntent.getActivity(
            context,
            entryId.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(entryId.hashCode(), NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("استحقاق دين: ${item.partyName}")
            .setContentText("المتبقي ${formatMoney(item.remainingCents)} مستحق الآن")
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build())
    }

    companion object { private const val CHANNEL_ID = "due_debt_reminders" }
}
''')

write("app/src/main/java/com/dioonplus/app/DioonAppState.kt", '''package com.dioonplus.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dioonplus.app.data.*
import com.dioonplus.app.reminder.DueReminderScheduler

class DioonAppState(context: Context) {
    private val database = LedgerDatabase(context.applicationContext)
    private val backupService = BackupService(database)
    private val reminderScheduler = DueReminderScheduler(context.applicationContext)

    var selectedPartyType by mutableStateOf(PartyType.CUSTOMER); private set
    var searchQuery by mutableStateOf(""); private set
    var selectedParty by mutableStateOf<Party?>(null); private set
    var summary by mutableStateOf(DashboardSummary()); private set
    var dueSummary by mutableStateOf(DueSummary()); private set
    var errorMessage by mutableStateOf<String?>(null); private set

    val parties = mutableStateListOf<Party>()
    val entries = mutableStateListOf<LedgerEntry>()
    val reportRows = mutableStateListOf<ReportRow>()
    val dailyTotals = mutableStateListOf<DailyTotal>()
    val dueItems = mutableStateListOf<DueItem>()

    init { refreshAll() }

    fun selectPartyType(type: PartyType) { selectedPartyType = type; refreshParties() }
    fun updateSearch(query: String) { searchQuery = query; refreshParties() }

    fun addParty(name: String, phone: String, type: PartyType): Boolean = execute {
        val id = database.addParty(name, phone, type)
        selectedPartyType = type; searchQuery = ""; refreshAll(); openParty(id)
    }
    fun updateParty(id: Long, name: String, phone: String): Boolean = execute {
        database.updateParty(id, name, phone); refreshAll(true); selectedParty = database.getParty(id)
    }
    fun deleteParty(id: Long): Boolean = execute {
        database.deleteParty(id); selectedParty = null; entries.clear(); refreshAll()
    }
    fun openParty(id: Long) { selectedParty = database.getParty(id); refreshEntries(id) }
    fun closeParty() { selectedParty = null; entries.clear(); refreshAll() }

    fun addEntry(type: EntryType, amountCents: Long, note: String, createdAt: Long, dueAt: Long?): Boolean {
        val partyId = selectedParty?.id ?: return false
        return execute { database.addEntry(partyId, type, amountCents, note, createdAt, dueAt); refreshSelectedParty(partyId); refreshAll(true) }
    }

    fun updateEntry(entryId: Long, type: EntryType, amountCents: Long, note: String, createdAt: Long, dueAt: Long?): Boolean {
        val partyId = selectedParty?.id ?: return false
        return execute { database.updateEntry(entryId, type, amountCents, note, createdAt, dueAt); refreshSelectedParty(partyId); refreshAll(true) }
    }

    fun addPayment(parentEntryId: Long, amountCents: Long, note: String, createdAt: Long): Boolean {
        val partyId = selectedParty?.id ?: return false
        return execute { database.addPayment(parentEntryId, amountCents, note, createdAt); refreshSelectedParty(partyId); refreshAll(true) }
    }

    fun settleInFull(entryId: Long): Boolean {
        val entry = database.getEntry(entryId) ?: return false
        return addPayment(entryId, entry.remainingCents, "سداد كامل", System.currentTimeMillis())
    }

    fun deleteEntry(entryId: Long): Boolean {
        val partyId = selectedParty?.id ?: return false
        return execute { database.deleteEntry(entryId); refreshSelectedParty(partyId); refreshAll(true) }
    }

    fun exportBackup(): String = backupService.exportJson()
    fun importBackup(json: String): Boolean = execute {
        backupService.importJson(json); selectedParty = null; entries.clear(); searchQuery = ""; refreshAll()
    }
    fun dismissError() { errorMessage = null }

    fun refreshAll(keepSelectedParty: Boolean = false) {
        refreshParties(); summary = database.dashboardSummary()
        reportRows.replaceWith(database.reportRows()); dailyTotals.replaceWith(database.lastDailyTotals())
        dueItems.replaceWith(database.listDueItems())
        val start = startOfToday(); val end = start + 86_400_000L
        dueSummary = DueSummary(
            overdueCount = dueItems.count { it.dueAt < start },
            todayCount = dueItems.count { it.dueAt in start until end },
            upcomingCount = dueItems.count { it.dueAt >= end },
        )
        reminderScheduler.reschedule(dueItems)
        if (keepSelectedParty) selectedParty?.id?.let(::refreshSelectedParty)
    }

    private fun startOfToday(): Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    private fun refreshParties() { parties.replaceWith(database.listParties(selectedPartyType, searchQuery)) }
    private fun refreshSelectedParty(id: Long) { selectedParty = database.getParty(id); refreshEntries(id) }
    private fun refreshEntries(id: Long) { entries.replaceWith(database.listEntries(id)) }
    private inline fun execute(block: () -> Unit): Boolean = try { errorMessage = null; block(); true } catch (e: Throwable) { errorMessage = e.message ?: "حدث خطأ غير متوقع"; false }
    private fun <T> MutableList<T>.replaceWith(items: List<T>) { clear(); addAll(items) }
}
''')

# Update app state construction and notification permission request.
path = "app/src/main/java/com/dioonplus/app/DioonPlusApp.kt"
text = read(path)
text = replace_once(text, "import androidx.compose.foundation.layout.WindowInsets\n", "import android.Manifest\nimport android.os.Build\nimport androidx.activity.compose.rememberLauncherForActivityResult\nimport androidx.activity.result.contract.ActivityResultContracts\nimport androidx.compose.foundation.layout.WindowInsets\n", "app permission imports")
text = replace_once(text, "import androidx.compose.runtime.Composable\n", "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.LaunchedEffect\n", "LaunchedEffect import")
text = replace_once(text, "import com.dioonplus.app.data.LedgerDatabase\n", "", "remove DB import")
text = replace_once(text, '''    val appState = remember(context.applicationContext) {
        DioonAppState(LedgerDatabase(context.applicationContext))
    }
''', '''    val appState = remember(context.applicationContext) {
        DioonAppState(context.applicationContext)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
''', "app state construction")
write(path, text)

# Patch manifest.
path = "app/src/main/AndroidManifest.xml"
text = read(path)
text = replace_once(text, '<manifest xmlns:android="http://schemas.android.com/apk/res/android">', '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n\n    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />', "manifest permission")
text = replace_once(text, '''        <provider
            android:name="androidx.core.content.FileProvider"''', '''        <receiver
            android:name=".reminder.DueReminderReceiver"
            android:exported="false" />

        <provider
            android:name="androidx.core.content.FileProvider"''', "manifest receiver")
write(path, text)

# Backup service supports due dates and linked payments while remaining compatible with v1 files.
write("app/src/main/java/com/dioonplus/app/data/BackupService.kt", '''package com.dioonplus.app.data

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
''')

# Replace PartyDetailsScreen with due/payment aware implementation.
write("app/src/main/java/com/dioonplus/app/ui/screens/PartyDetailsScreen.kt", '''package com.dioonplus.app.ui.screens

import android.app.DatePickerDialog
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dioonplus.app.DioonAppState
import com.dioonplus.app.data.*
import com.dioonplus.app.ui.theme.*
import com.dioonplus.app.util.CurrencySettings
import com.dioonplus.app.util.formatDateTime
import com.dioonplus.app.util.formatMoney
import com.dioonplus.app.util.parseMoneyToCents
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PartyDetailsScreen(appState: DioonAppState, party: Party, onBack: () -> Unit) {
    var addEntryType by remember { mutableStateOf<EntryType?>(null) }
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var paymentEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var fullSettlementEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var pendingDeleteEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var showEditParty by remember { mutableStateOf(false) }
    var showDeleteParty by remember { mutableStateOf(false) }
    val tone = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 45) }
    val haptic = LocalHapticFeedback.current
    DisposableEffect(Unit) { onDispose { tone.release() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(Modifier.statusBarsPadding(), color = Color.White, shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "رجوع") }
                    Column(Modifier.weight(1f)) {
                        Text(party.name, style = MaterialTheme.typography.titleLarge)
                        Text((if (party.type == PartyType.CUSTOMER) "عميل" else "مورد") + if (party.phone.isBlank()) "" else " • ${party.phone}", color = TextSecondary)
                    }
                    IconButton(onClick = { showEditParty = true }) { Icon(Icons.Outlined.Edit, "تعديل", tint = DioonBlue) }
                    IconButton(onClick = { showDeleteParty = true }) { Icon(Icons.Outlined.DeleteOutline, "حذف", tint = DebtRed) }
                }
            }
        },
        bottomBar = {
            Surface(Modifier.navigationBarsPadding(), color = Color.White, shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button({ addEntryType = EntryType.GAVE }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen), shape = RoundedCornerShape(16.dp)) { Text("أعطيت") }
                    Button({ addEntryType = EntryType.TOOK }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = DebtRed), shape = RoundedCornerShape(16.dp)) { Text("أخذت") }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { BalanceCard(party) }
            val openDebts = appState.entries.count { !it.isPayment && it.remainingCents > 0 }
            if (openDebts > 0) item { Text("الديون المفتوحة: $openDebts", color = DioonBlueDark, fontWeight = FontWeight.SemiBold) }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("سجل الحركات", style = MaterialTheme.typography.titleLarge); Text("${appState.entries.size} حركة", color = TextSecondary) } }
            if (appState.entries.isEmpty()) item { EmptyEntriesCard() }
            else items(appState.entries, key = { it.id }) { entry ->
                EntryCard(entry, onEdit = { if (!entry.isPayment) editingEntry = entry }, onDelete = { pendingDeleteEntry = entry }, onPayment = { paymentEntry = entry }, onSettle = { fullSettlementEntry = entry })
            }
        }
    }

    addEntryType?.let { type -> EntryEditorDialog(type, null, { addEntryType = null }) { selectedType, amount, note, createdAt, dueAt ->
        val cents = parseMoneyToCents(amount) ?: return@EntryEditorDialog false
        appState.addEntry(selectedType, cents, note, createdAt, dueAt).also { if (it) { tone.startTone(ToneGenerator.TONE_PROP_ACK, 120); haptic.performHapticFeedback(HapticFeedbackType.LongPress); addEntryType = null } }
    } }

    editingEntry?.let { entry -> EntryEditorDialog(entry.type, entry, { editingEntry = null }) { selectedType, amount, note, createdAt, dueAt ->
        val cents = parseMoneyToCents(amount) ?: return@EntryEditorDialog false
        appState.updateEntry(entry.id, selectedType, cents, note, createdAt, dueAt).also { if (it) editingEntry = null }
    } }

    paymentEntry?.let { entry -> PaymentDialog(entry, { paymentEntry = null }) { amount, note, date ->
        val cents = parseMoneyToCents(amount) ?: return@PaymentDialog false
        appState.addPayment(entry.id, cents, note, date).also { if (it) paymentEntry = null }
    } }

    fullSettlementEntry?.let { entry -> AlertDialog(
        onDismissRequest = { fullSettlementEntry = null },
        title = { Text("تسجيل سداد كامل؟") },
        text = { Text("سيتم تسجيل دفعة بقيمة ${formatMoney(entry.remainingCents)} وإغلاق هذا الدين مع الاحتفاظ بسجله.") },
        confirmButton = { Button({ if (appState.settleInFull(entry.id)) fullSettlementEntry = null }) { Text("تأكيد السداد") } },
        dismissButton = { TextButton({ fullSettlementEntry = null }) { Text("إلغاء") } },
    ) }

    if (showEditParty) EditPartyDialog(party, { showEditParty = false }) { name, phone -> appState.updateParty(party.id, name, phone).also { if (it) showEditParty = false } }
    if (showDeleteParty) AlertDialog(onDismissRequest = { showDeleteParty = false }, title = { Text("حذف الحساب؟") }, text = { Text("سيتم حذف الحساب وجميع حركاته ودفعاته نهائياً.") }, confirmButton = { Button({ if (appState.deleteParty(party.id)) showDeleteParty = false }, colors = ButtonDefaults.buttonColors(containerColor = DebtRed)) { Text("حذف نهائياً") } }, dismissButton = { TextButton({ showDeleteParty = false }) { Text("إلغاء") } })
    pendingDeleteEntry?.let { entry -> AlertDialog(onDismissRequest = { pendingDeleteEntry = null }, title = { Text("حذف الحركة؟") }, text = { Text(if (entry.isPayment) "سيتم حذف دفعة السداد وإعادة المبلغ إلى المتبقي." else "سيتم حذف الدين وجميع الدفعات المرتبطة به.") }, confirmButton = { Button({ if (appState.deleteEntry(entry.id)) pendingDeleteEntry = null }, colors = ButtonDefaults.buttonColors(containerColor = DebtRed)) { Text("حذف") } }, dismissButton = { TextButton({ pendingDeleteEntry = null }) { Text("إلغاء") } })
}

@Composable private fun BalanceCard(party: Party) {
    val positive = party.balanceCents >= 0; val accent = if (positive) SuccessGreen else DebtRed; val soft = if (positive) SuccessGreenSoft else DebtRedSoft
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp)) { Text("الرصيد الحالي", color = TextSecondary); Text(formatMoney(party.balanceCents), style = MaterialTheme.typography.headlineMedium, color = accent, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(8.dp)); Surface(color = soft, shape = RoundedCornerShape(12.dp)) { Text(when { party.balanceCents > 0 -> "عليه لك"; party.balanceCents < 0 -> "له عليك"; else -> "الحساب متوازن" }, Modifier.padding(12.dp), color = accent) } }
    }
}

@Composable private fun EmptyEntriesCard() { Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.ReceiptLong, null, tint = TextSecondary); Text("لا توجد حركات بعد") } } }

@Composable private fun EntryCard(entry: LedgerEntry, onEdit: () -> Unit, onDelete: () -> Unit, onPayment: () -> Unit, onSettle: () -> Unit) {
    val gave = entry.type == EntryType.GAVE; val accent = if (gave) SuccessGreen else DebtRed
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (entry.isPayment) "دفعة سداد" else if (gave) "أعطيت" else "أخذت", fontWeight = FontWeight.Bold, color = accent)
                    Text(formatDateTime(entry.createdAt), color = TextSecondary)
                    if (entry.note.isNotBlank()) Text(entry.note, color = DioonBlueDark)
                }
                Text(formatMoney(entry.amountCents), color = accent, fontWeight = FontWeight.Bold)
            }
            if (!entry.isPayment) {
                val statusText = when (entry.debtStatus) { DebtStatus.OPEN -> "غير مسدد"; DebtStatus.PARTIAL -> "مسدد جزئياً"; DebtStatus.PAID -> "مسدد بالكامل" }
                Surface(color = when (entry.debtStatus) { DebtStatus.PAID -> SuccessGreenSoft; DebtStatus.PARTIAL -> DioonBlueSoft; else -> DebtRedSoft }, shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(statusText, fontWeight = FontWeight.SemiBold)
                        Text("الأصل ${formatMoney(entry.amountCents)} • المدفوع ${formatMoney(entry.paidCents)} • المتبقي ${formatMoney(entry.remainingCents)}", style = MaterialTheme.typography.bodySmall)
                        entry.dueAt?.let { Text("الاستحقاق: ${formatDate(it)}", style = MaterialTheme.typography.bodySmall, color = dueColor(it)) }
                    }
                }
                if (entry.remainingCents > 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onPayment, Modifier.weight(1f)) { Icon(Icons.Outlined.Payments, null); Spacer(Modifier.size(5.dp)); Text("تسجيل دفعة") }
                    Button(onSettle, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)) { Text("سداد كامل") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!entry.isPayment) IconButton(onEdit) { Icon(Icons.Outlined.Edit, "تعديل", tint = DioonBlue) }
                IconButton(onDelete) { Icon(Icons.Outlined.DeleteOutline, "حذف", tint = TextSecondary) }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable private fun EntryEditorDialog(initialType: EntryType, existingEntry: LedgerEntry?, onDismiss: () -> Unit, onSave: (EntryType, String, String, Long, Long?) -> Boolean) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(existingEntry?.type ?: initialType) }; var amount by remember { mutableStateOf(existingEntry?.amountCents?.let(::centsToInput).orEmpty()) }; var note by remember { mutableStateOf(existingEntry?.note.orEmpty()) }
    var date by remember { mutableStateOf(existingEntry?.createdAt ?: System.currentTimeMillis()) }; var dueAt by remember { mutableStateOf(existingEntry?.dueAt) }; var error by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }; val keyboard = LocalSoftwareKeyboardController.current
    fun save() { error = parseMoneyToCents(amount) == null; if (!error && onSave(type, amount, note, date, dueAt)) keyboard?.hide() }
    fun pick(current: Long, maxToday: Boolean, update: (Long) -> Unit) { keyboard?.hide(); val c = Calendar.getInstance().apply { timeInMillis = current }; DatePickerDialog(context, { _, y, m, d -> update(Calendar.getInstance().apply { set(y,m,d,9,0,0); set(Calendar.MILLISECOND,0) }.timeInMillis) }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply { if (maxToday) datePicker.maxDate = System.currentTimeMillis() }.show() }
    LaunchedEffect(Unit) { requester.requestFocus(); keyboard?.show() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existingEntry == null) "إضافة حركة مالية" else "تعديل الحركة") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { EntryTypeChoice(Modifier.weight(1f), type == EntryType.GAVE, "أعطيت", SuccessGreen) { type = EntryType.GAVE }; EntryTypeChoice(Modifier.weight(1f), type == EntryType.TOOK, "أخذت", DebtRed) { type = EntryType.TOOK } }
            OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch in listOf('.',',','٫') }; error = false }, Modifier.fillMaxWidth().focusRequester(requester), label = { Text("المبلغ") }, suffix = { Text(CurrencySettings.current.symbol) }, isError = error, supportingText = { if (error) Text("أدخل مبلغاً صحيحاً") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next))
            OutlinedButton({ pick(date, true) { date = it } }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CalendarMonth, null); Text(" تاريخ الحركة: ${formatDate(date)}") }
            OutlinedButton({ pick(dueAt ?: System.currentTimeMillis(), false) { dueAt = it } }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Event, null); Text(if (dueAt == null) " إضافة تاريخ استحقاق" else " الاستحقاق: ${formatDate(dueAt!!)}") }
            if (dueAt != null) TextButton({ dueAt = null }) { Text("إزالة تاريخ الاستحقاق") }
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظة اختيارية") }, minLines = 2, keyboardActions = KeyboardActions(onDone = { save() }))
        }
    }, confirmButton = { Button({ save() }) { Text("حفظ") } }, dismissButton = { TextButton(onDismiss) { Text("إلغاء") } })
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable private fun PaymentDialog(entry: LedgerEntry, onDismiss: () -> Unit, onSave: (String, String, Long) -> Boolean) {
    var amount by remember { mutableStateOf("") }; var note by remember { mutableStateOf("دفعة سداد") }; var date by remember { mutableStateOf(System.currentTimeMillis()) }; var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current; val requester = remember { FocusRequester() }; val keyboard = LocalSoftwareKeyboardController.current
    fun save() { val cents = parseMoneyToCents(amount); error = when { cents == null -> "أدخل مبلغاً صحيحاً"; cents > entry.remainingCents -> "الدفعة أكبر من المتبقي"; else -> null }; if (error == null && onSave(amount, note, date)) keyboard?.hide() }
    LaunchedEffect(Unit) { requester.requestFocus(); keyboard?.show() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تسجيل دفعة") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("المتبقي: ${formatMoney(entry.remainingCents)}", fontWeight = FontWeight.Bold)
        OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch in listOf('.',',','٫') }; error = null }, Modifier.fillMaxWidth().focusRequester(requester), label = { Text("قيمة الدفعة") }, suffix = { Text(CurrencySettings.current.symbol) }, isError = error != null, supportingText = { error?.let { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedButton({ val c = Calendar.getInstance().apply { timeInMillis = date }; DatePickerDialog(context, { _,y,m,d -> date = Calendar.getInstance().apply { set(y,m,d) }.timeInMillis }, c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show() }, Modifier.fillMaxWidth()) { Text("تاريخ الدفعة: ${formatDate(date)}") }
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظة") })
        TextButton({ amount = centsToInput(entry.remainingCents) }) { Text("استخدام كامل المبلغ المتبقي") }
    } }, confirmButton = { Button({ save() }) { Text("حفظ الدفعة") } }, dismissButton = { TextButton(onDismiss) { Text("إلغاء") } })
}

@Composable private fun EntryTypeChoice(modifier: Modifier, selected: Boolean, title: String, color: Color, onClick: () -> Unit) { if (selected) Button(onClick, modifier, colors = ButtonDefaults.buttonColors(containerColor = color)) { Text(title) } else OutlinedButton(onClick, modifier) { Text(title) } }

@OptIn(ExperimentalComposeUiApi::class)
@Composable private fun EditPartyDialog(party: Party, onDismiss: () -> Unit, onSave: (String,String)->Boolean) {
    var name by remember { mutableStateOf(party.name) }; var phone by remember { mutableStateOf(party.phone) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تعديل بيانات الحساب") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("الاسم")}); OutlinedTextField(phone,{phone=it},Modifier.fillMaxWidth(),label={Text("رقم الهاتف")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone)) } }, confirmButton = { Button({ if(name.isNotBlank()) onSave(name,phone) }) { Text("حفظ") } }, dismissButton = { TextButton(onDismiss) { Text("إلغاء") } })
}

private fun centsToInput(cents: Long) = BigDecimal.valueOf(cents,2).stripTrailingZeros().toPlainString()
private fun formatDate(timestamp: Long) = SimpleDateFormat("d MMMM yyyy", Locale("ar")).format(Date(timestamp))
private fun dueColor(timestamp: Long): Color { val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }.timeInMillis; return if (timestamp < today) DebtRed else DioonBlueDark }
''')

# Home due overview dialog and badge.
path = "app/src/main/java/com/dioonplus/app/ui/screens/HomeScreen.kt"
text = read(path)
text = replace_once(text, "import com.dioonplus.app.data.Party\n", "import com.dioonplus.app.data.DueItem\nimport com.dioonplus.app.data.Party\n", "home due import")
text = replace_once(text, '''    var showAddDialog by remember { mutableStateOf(false) }
    var balancesVisible by remember { mutableStateOf(true) }
''', '''    var showAddDialog by remember { mutableStateOf(false) }
    var showDueDialog by remember { mutableStateOf(false) }
    var balancesVisible by remember { mutableStateOf(true) }
''', "home due state")
text = replace_once(text, "            item { HomeHeader() }", "            item { HomeHeader(appState.dueItems.size) { showDueDialog = true } }", "home header call")
text = replace_once(text, '''            item {
                TextField(''', '''            if (appState.dueItems.isNotEmpty()) {
                item { DueSummaryCard(appState.dueSummary.overdueCount, appState.dueSummary.todayCount, appState.dueSummary.upcomingCount) { showDueDialog = true } }
            }
            item {
                TextField(''', "due card insert")
text = replace_once(text, '''    if (showAddDialog) {''', '''    if (showDueDialog) {
        DueItemsDialog(
            items = appState.dueItems,
            onDismiss = { showDueDialog = false },
            onOpen = { item -> showDueDialog = false; appState.openParty(item.partyId) },
        )
    }

    if (showAddDialog) {''', "due dialog call")
text = replace_once(text, '''@Composable
private fun HomeHeader() {''', '''@Composable
private fun HomeHeader(dueCount: Int, onNotifications: () -> Unit) {''', "header signature")
text = replace_once(text, '''        IconButton(onClick = { }) {
            Icon(Icons.Outlined.NotificationsNone, contentDescription = "التنبيهات", tint = DioonBlueDark)
        }''', '''        IconButton(onClick = onNotifications) {
            BadgedBox(badge = { if (dueCount > 0) Badge { Text(dueCount.toString()) } }) {
                Icon(Icons.Outlined.NotificationsNone, contentDescription = "التنبيهات", tint = DioonBlueDark)
            }
        }''', "header notification")
text += '''

@Composable
private fun DueSummaryCard(overdue: Int, today: Int, upcoming: Int, onClick: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Text("الاستحقاقات والتذكيرات", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DueMetric(Modifier.weight(1f), "متأخر", overdue, DebtRedSoft, DebtRed)
                DueMetric(Modifier.weight(1f), "اليوم", today, DioonBlueSoft, DioonBlue)
                DueMetric(Modifier.weight(1f), "قادم", upcoming, SuccessGreenSoft, SuccessGreen)
            }
        }
    }
}

@Composable
private fun DueMetric(modifier: Modifier, label: String, count: Int, bg: Color, color: Color) {
    Surface(modifier, color = bg, shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(count.toString(), color = color, fontWeight = FontWeight.Bold); Text(label, color = color) } }
}

@Composable
private fun DueItemsDialog(items: List<DueItem>, onDismiss: () -> Unit, onOpen: (DueItem) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("الاستحقاقات") }, text = {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.entryId }) { item ->
                Surface(Modifier.fillMaxWidth().clickable { onOpen(item) }, color = Color.White, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.partyName, fontWeight = FontWeight.Bold)
                        Text("المتبقي ${formatMoney(item.remainingCents)}", color = DebtRed)
                        Text("الاستحقاق ${SimpleDateFormat("d MMM yyyy", Locale("ar")).format(Date(item.dueAt))}", color = TextSecondary)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("إغلاق") } })
}
'''
# imports required for Badge and date formatting are covered by material3 wildcard? current imports explicit, add these.
text = replace_once(text, "import androidx.compose.material3.AlertDialog\n", "import androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.Badge\nimport androidx.compose.material3.BadgedBox\n", "badge imports")
text = replace_once(text, "import com.dioonplus.app.util.formatMoney\n", "import com.dioonplus.app.util.formatMoney\nimport java.text.SimpleDateFormat\nimport java.util.Date\nimport java.util.Locale\n", "home date imports")
write(path, text)

# Bump version.
path = "app/build.gradle.kts"
text = read(path)
text = replace_once(text, '        versionCode = 8\n        versionName = "0.4.1"', '        versionCode = 9\n        versionName = "0.5.0"', "version bump")
write(path, text)

for relative in ["tools/apply_due_payments_release.py", ".github/workflows/apply-due-payments-release.yml"]:
    p = ROOT / relative
    if p.exists(): p.unlink()
