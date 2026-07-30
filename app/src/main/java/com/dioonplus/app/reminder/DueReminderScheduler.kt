package com.dioonplus.app.reminder

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
