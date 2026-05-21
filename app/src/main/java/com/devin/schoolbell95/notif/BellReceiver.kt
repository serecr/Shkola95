package com.devin.schoolbell95.notif

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.devin.schoolbell95.MainActivity
import com.devin.schoolbell95.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BellReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(BellScheduler.EXTRA_TITLE) ?: "Звонок"
        val body = intent.getStringExtra(BellScheduler.EXTRA_BODY) ?: ""

        BellScheduler.ensureChannel(context)
        if (canPostNotifications(context)) {
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
        }

        finishAfterReschedule(context)
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun BroadcastReceiver.finishAfterReschedule(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                BellScheduler.rescheduleAllNow(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
