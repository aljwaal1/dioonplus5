package com.dioonplus.app.reminder

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
