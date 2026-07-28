package com.dioonplus.app.reminder

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
