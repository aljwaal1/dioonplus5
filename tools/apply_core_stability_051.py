from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


# Currency definitions keep the same simple selector, while internal precision follows ISO-style digits.
write(
    "app/src/main/java/com/dioonplus/app/util/CurrencyOption.kt",
    '''package com.dioonplus.app.util

/** Supported display currencies. Changing this selection never converts stored debt values. */
enum class CurrencyOption(
    val code: String,
    val arabicName: String,
    val symbol: String,
    val fractionDigits: Int,
) {
    JOD("JOD", "الدينار الأردني", "د.أ", 3),
    SAR("SAR", "الريال السعودي", "ر.س", 2),
    YER("YER", "الريال اليمني", "ر.ي", 2),
    EGP("EGP", "الجنيه المصري", "ج.م", 2),
    USD("USD", "الدولار الأمريكي", "$", 2),
    EUR("EUR", "اليورو", "€", 2),
    AED("AED", "الدرهم الإماراتي", "د.إ", 2),
    KWD("KWD", "الدينار الكويتي", "د.ك", 3),
    QAR("QAR", "الريال القطري", "ر.ق", 2),
    OMR("OMR", "الريال العُماني", "ر.ع", 3),
    BHD("BHD", "الدينار البحريني", "د.ب", 3),
    IQD("IQD", "الدينار العراقي", "د.ع", 3),
    SYP("SYP", "الليرة السورية", "ل.س", 2),
    LBP("LBP", "الليرة اللبنانية", "ل.ل", 2),
    SDG("SDG", "الجنيه السوداني", "ج.س", 2),
    LYD("LYD", "الدينار الليبي", "د.ل", 3),
    TND("TND", "الدينار التونسي", "د.ت", 3),
    DZD("DZD", "الدينار الجزائري", "د.ج", 2),
    MAD("MAD", "الدرهم المغربي", "د.م", 2),
    TRY("TRY", "الليرة التركية", "₺", 2),
    GBP("GBP", "الجنيه الإسترليني", "£", 2),
    SEK("SEK", "الكرونة السويدية", "kr", 2),
    ;

    val displayName: String get() = "$arabicName ($code)"

    companion object {
        fun fromCode(code: String?): CurrencyOption =
            entries.firstOrNull { it.code == code } ?: JOD
    }
}

object CurrencySettings {
    @Volatile
    var current: CurrencyOption = CurrencyOption.JOD
}
''',
)

write(
    "app/src/main/java/com/dioonplus/app/util/Formatters.kt",
    '''package com.dioonplus.app.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val arabicLocale = Locale("ar", "JO")
const val STORAGE_FRACTION_DIGITS = 3

fun formatMoney(cents: Long, includeSign: Boolean = false): String {
    val currency = CurrencySettings.current
    val value = BigDecimal.valueOf(cents, STORAGE_FRACTION_DIGITS).abs()
        .setScale(currency.fractionDigits, RoundingMode.HALF_UP)
    val formatter = NumberFormat.getNumberInstance(arabicLocale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = currency.fractionDigits
        roundingMode = RoundingMode.HALF_UP
    }
    val sign = when {
        !includeSign -> ""
        cents > 0 -> "+"
        cents < 0 -> "−"
        else -> ""
    }
    return "$sign${formatter.format(value)} ${currency.symbol}"
}

fun parseMoneyToCents(value: String): Long? {
    val normalized = value
        .trim()
        .replace('٫', '.')
        .replace(',', '.')
        .replace(" ", "")
    if (normalized.isBlank()) return null
    return runCatching {
        normalized.toBigDecimal()
            .setScale(CurrencySettings.current.fractionDigits, RoundingMode.HALF_UP)
            .movePointRight(STORAGE_FRACTION_DIGITS)
            .longValueExact()
            .takeIf { it > 0 }
    }.getOrNull()
}

fun formatDateTime(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy، h:mm a", arabicLocale).format(Date(timestamp))

fun formatDay(timestamp: Long): String =
    SimpleDateFormat("EEE", arabicLocale).format(Date(timestamp))

fun fileSafeDate(timestamp: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(timestamp))
''',
)

# Rebuild the upgraded ledger table so old installations get the same foreign-key protection as fresh installs.
path = "app/src/main/java/com/dioonplus/app/data/LedgerDatabase.kt"
text = read(path)
text = replace_once(
    text,
    '''    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE ledger_entries ADD COLUMN due_at INTEGER")
            db.execSQL("ALTER TABLE ledger_entries ADD COLUMN parent_entry_id INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_due ON ledger_entries(due_at)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_parent ON ledger_entries(parent_entry_id)")
        }
    }
''',
    '''    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
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
''',
    "database migration",
)
text = replace_once(
    text,
    '''        require(!existing.isPayment) { "لا يمكن تعديل دفعة السداد بهذه الطريقة" }
        require(amountCents >= existing.paidCents) { "المبلغ الجديد أقل من الدفعات المسجلة" }
''',
    '''        require(!existing.isPayment) { "لا يمكن تعديل دفعة السداد بهذه الطريقة" }
        require(existing.paidCents == 0L || type == existing.type) {
            "لا يمكن تغيير نوع الدين بعد تسجيل دفعة"
        }
        require(amountCents >= existing.paidCents) { "المبلغ الجديد أقل من الدفعات المسجلة" }
''',
    "lock debt type after payment",
)
text = replace_once(
    text,
    '''        private const val DATABASE_VERSION = 2''',
    '''        private const val DATABASE_VERSION = 3''',
    "database version",
)
write(path, text)

# Backup v3 uses three internal decimal places and validates all relationships before replacing current data.
write(
    "app/src/main/java/com/dioonplus/app/data/BackupService.kt",
    '''package com.dioonplus.app.data

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
''',
)

# Reminder scheduling stays invisible to the user but avoids repeated overdue alerts.
write(
    "app/src/main/java/com/dioonplus/app/reminder/DueReminderScheduler.kt",
    '''package com.dioonplus.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dioonplus.app.data.DueItem
import java.util.Calendar

class DueReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun reschedule(items: List<DueItem>, force: Boolean = false) {
        items.forEach { schedule(it, force) }
    }

    fun schedule(item: DueItem, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val dueAtNine = Calendar.getInstance().apply {
            timeInMillis = item.dueAt
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val target = when {
            dueAtNine > now -> dueAtNine
            DueReminderReceiver.wasNotifiedToday(context, item.entryId) -> nextNineAm(now)
            else -> now + 60_000L
        }
        val key = scheduledKey(item.entryId)
        val storedTarget = preferences.getLong(key, 0L)
        val pendingSoon = dueAtNine <= now && storedTarget in (now + 1L)..(now + 5 * 60_000L)
        if (!force && (storedTarget == target || pendingSoon)) return

        val intent = Intent(context, DueReminderReceiver::class.java)
            .putExtra(EXTRA_ENTRY_ID, item.entryId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            item.entryId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pendingIntent)
        preferences.edit().putLong(key, target).apply()
    }

    private fun nextNineAm(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
        internal const val PREFS_NAME = "due_reminder_state"
        internal fun scheduledKey(entryId: Long) = "scheduled_$entryId"
    }
}
''',
)

write(
    "app/src/main/java/com/dioonplus/app/reminder/DueReminderReceiver.kt",
    '''package com.dioonplus.app.reminder

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
import java.util.Calendar

class DueReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getLongExtra(DueReminderScheduler.EXTRA_ENTRY_ID, -1L)
        if (entryId <= 0 || wasNotifiedToday(context, entryId)) return
        val item = LedgerDatabase(context).getDueItem(entryId) ?: return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "تذكيرات الديون", NotificationManager.IMPORTANCE_DEFAULT),
        )
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val openIntent = PendingIntent.getActivity(
            context,
            entryId.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            entryId.hashCode(),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("استحقاق دين: ${item.partyName}")
                .setContentText("المتبقي ${formatMoney(item.remainingCents)} مستحق الآن")
                .setAutoCancel(true)
                .setContentIntent(openIntent)
                .build(),
        )
        context.getSharedPreferences(DueReminderScheduler.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(notifiedKey(entryId), todayKey())
            .remove(DueReminderScheduler.scheduledKey(entryId))
            .apply()
    }

    companion object {
        private const val CHANNEL_ID = "due_debt_reminders"
        private fun notifiedKey(entryId: Long) = "notified_$entryId"

        fun wasNotifiedToday(context: Context, entryId: Long): Boolean =
            context.getSharedPreferences(DueReminderScheduler.PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(notifiedKey(entryId), -1) == todayKey()

        private fun todayKey(): Int = Calendar.getInstance().let { calendar ->
            calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
        }
    }
}
''',
)

write(
    "app/src/main/java/com/dioonplus/app/reminder/ReminderRescheduleReceiver.kt",
    '''package com.dioonplus.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dioonplus.app.data.LedgerDatabase

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()
        Thread {
            try {
                val items = LedgerDatabase(context.applicationContext).listDueItems()
                DueReminderScheduler(context.applicationContext).reschedule(items, force = true)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
''',
)

# Pass the existing preferences to feedback call sites; no new user-facing controls are added.
path = "app/src/main/java/com/dioonplus/app/DioonPlusApp.kt"
text = read(path)
text = replace_once(
    text,
    '''                    appState = appState,
                    party = selectedParty,
                    onBack = appState::closeParty,
''',
    '''                    appState = appState,
                    party = selectedParty,
                    preferences = preferences,
                    onBack = appState::closeParty,
''',
    "party screen preferences",
)
text = replace_once(
    text,
    '''                        0 -> HomeScreen(contentPadding = innerPadding, appState = appState)''',
    '''                        0 -> HomeScreen(contentPadding = innerPadding, appState = appState, preferences = preferences)''',
    "home screen preferences",
)
write(path, text)

path = "app/src/main/java/com/dioonplus/app/ui/screens/HomeScreen.kt"
text = read(path)
text = replace_once(
    text,
    '''import com.dioonplus.app.DioonAppState
import com.dioonplus.app.data.DueItem
''',
    '''import com.dioonplus.app.DioonAppState
import com.dioonplus.app.data.DueItem
import com.dioonplus.app.security.AppPreferences
''',
    "home preferences import",
)
text = replace_once(
    text,
    '''fun HomeScreen(contentPadding: PaddingValues, appState: DioonAppState) {''',
    '''fun HomeScreen(contentPadding: PaddingValues, appState: DioonAppState, preferences: AppPreferences) {''',
    "home signature",
)
text = replace_once(
    text,
    '''                    tone.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                    showAddDialog = false''',
    '''                    if (preferences.soundEnabled) tone.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                    showAddDialog = false''',
    "home sound setting",
)
write(path, text)

path = "app/src/main/java/com/dioonplus/app/ui/screens/PartyDetailsScreen.kt"
text = read(path)
text = replace_once(
    text,
    '''import com.dioonplus.app.DioonAppState
import com.dioonplus.app.data.*
''',
    '''import com.dioonplus.app.DioonAppState
import com.dioonplus.app.data.*
import com.dioonplus.app.security.AppPreferences
''',
    "party preferences import",
)
text = replace_once(
    text,
    '''fun PartyDetailsScreen(appState: DioonAppState, party: Party, onBack: () -> Unit) {''',
    '''fun PartyDetailsScreen(appState: DioonAppState, party: Party, preferences: AppPreferences, onBack: () -> Unit) {''',
    "party signature",
)
text = replace_once(
    text,
    '''        appState.addEntry(selectedType, cents, note, createdAt, dueAt).also { if (it) { tone.startTone(ToneGenerator.TONE_PROP_ACK, 120); haptic.performHapticFeedback(HapticFeedbackType.LongPress); addEntryType = null } }''',
    '''        appState.addEntry(selectedType, cents, note, createdAt, dueAt).also { if (it) { if (preferences.soundEnabled) tone.startTone(ToneGenerator.TONE_PROP_ACK, 120); if (preferences.vibrationEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress); addEntryType = null } }''',
    "party feedback settings",
)
text = replace_once(
    text,
    '''            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { EntryTypeChoice(Modifier.weight(1f), type == EntryType.GAVE, "أعطيت", SuccessGreen) { type = EntryType.GAVE }; EntryTypeChoice(Modifier.weight(1f), type == EntryType.TOOK, "أخذت", DebtRed) { type = EntryType.TOOK } }
''',
    '''            if (existingEntry?.paidCents?.let { it > 0L } == true) {
                Text(if (type == EntryType.GAVE) "نوع الحركة: أعطيت" else "نوع الحركة: أخذت", color = TextSecondary)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { EntryTypeChoice(Modifier.weight(1f), type == EntryType.GAVE, "أعطيت", SuccessGreen) { type = EntryType.GAVE }; EntryTypeChoice(Modifier.weight(1f), type == EntryType.TOOK, "أخذت", DebtRed) { type = EntryType.TOOK } }
            }
''',
    "hide type switch after payment",
)
text = replace_once(
    text,
    '''private fun centsToInput(cents: Long) = BigDecimal.valueOf(cents,2).stripTrailingZeros().toPlainString()''',
    '''private fun centsToInput(cents: Long) = BigDecimal.valueOf(cents,3).stripTrailingZeros().toPlainString()''',
    "money input scale",
)
write(path, text)

# Receive system restart/update events so reminders survive without any new screen.
path = "app/src/main/AndroidManifest.xml"
text = read(path)
text = replace_once(
    text,
    '''    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />''',
    '''    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />''',
    "boot permission",
)
text = replace_once(
    text,
    '''        <receiver
            android:name=".reminder.DueReminderReceiver"
            android:exported="false" />
''',
    '''        <receiver
            android:name=".reminder.DueReminderReceiver"
            android:exported="false" />

        <receiver
            android:name=".reminder.ReminderRescheduleReceiver"
            android:enabled="true"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
''',
    "restart receiver",
)
write(path, text)

# Patch release version only; the visible application layout remains unchanged.
path = "app/build.gradle.kts"
text = read(path)
text = replace_once(
    text,
    '''        versionCode = 9
        versionName = "0.5.0"''',
    '''        versionCode = 10
        versionName = "0.5.1"''',
    "version bump",
)
write(path, text)

path = ".github/workflows/publish-release.yml"
text = read(path)
text = replace_once(
    text,
    '''          notes="نسخة ديون بلس ${VERSION}: إصلاح فتح لوحة المفاتيح تلقائياً، وحماية الأزرار السفلية من التداخل مع أزرار الهاتف، مع توقيع اختبار ثابت للتحديثات القادمة."''',
    '''          notes="نسخة ديون بلس ${VERSION}: إصدار إصلاحي يحسن دقة الأرصدة والعملات، سلامة النسخ الاحتياطي، التذكيرات، واحترام إعدادات الصوت والاهتزاز دون تعقيد الواجهة."''',
    "release notes",
)
write(path, text)

# Remove the temporary self-applying patch after the real source changes are committed.
for relative in [
    "tools/apply_core_stability_051.py",
    ".github/workflows/apply-core-stability-051.yml",
]:
    target = ROOT / relative
    if target.exists():
        target.unlink()
