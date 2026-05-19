package com.devin.schoolbell95.notif

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.devin.schoolbell95.MainActivity
import com.devin.schoolbell95.R

class BellReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == "android.intent.action.TIME_SET" ||
            intent.action == "android.intent.action.TIMEZONE_CHANGED"
        ) {
            BellScheduler.rescheduleAll(context)
            return
        }

        val title = intent.getStringExtra(BellScheduler.EXTRA_TITLE) ?: "Звонок"
        val body = intent.getStringExtra(BellScheduler.EXTRA_BODY) ?: ""

        BellScheduler.ensureChannel(context)
        val tap = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, BellScheduler.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(title.hashCode(), n)

        // Reschedule alarms once a day, after the last bell, so future days get covered.
        BellScheduler.rescheduleAll(context)
    }
}
