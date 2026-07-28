package com.dioonplus.app.reminder

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
