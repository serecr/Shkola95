package com.devin.schoolbell95.notif

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.devin.schoolbell95.data.AppDatabase
import com.devin.schoolbell95.data.DayKind
import com.devin.schoolbell95.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

object BellScheduler {

    const val CHANNEL_ID = "bells"
    private const val ACTION = "com.devin.schoolbell95.BELL"
    const val EXTRA_TITLE = "title"
    const val EXTRA_BODY = "body"
    private const val REQ_BASE = 10000

    private val UFA: TimeZone = TimeZone.getTimeZone("Asia/Yekaterinburg")

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Школьные звонки",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = "Уведомления о звонках и переменах" }
                nm.createNotificationChannel(ch)
            }
        }
    }

    /**
     * Reschedule alarms for today's remaining bells plus tomorrow's full set.
     * Cheap to call from app startup and after schedule edits.
     */
    fun rescheduleAll(context: Context) {
        ensureChannel(context)
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val prefs = Prefs(context)
            val db = AppDatabase.get(context)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            cancelAll(context, am)

            val now = Calendar.getInstance(UFA)
            var reqId = REQ_BASE
            for (dayOffset in 0..1) {
                val day = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
                val dow = day.get(Calendar.DAY_OF_WEEK)
                if (dow == Calendar.SUNDAY) continue
                val kind = DayKind.fromCalendarDow(dow)
                val bells = db.bellDao().list(prefs.shift, kind)
                if (bells.isEmpty()) continue
                bells.forEach { b ->
                    if (prefs.notify5Min) {
                        val t = atMinute(day, b.startMin - 5)
                        if (t > now.timeInMillis) {
                            schedule(
                                context, am, reqId++, t,
                                "Через 5 минут — ${b.lessonNumber}-й урок",
                                "Звонок в ${fmt(b.startMin)}",
                            )
                        }
                    }
                    if (prefs.notifyOnBell) {
                        val tStart = atMinute(day, b.startMin)
                        if (tStart > now.timeInMillis) {
                            schedule(
                                context, am, reqId++, tStart,
                                "Прозвенел звонок",
                                "Начался ${b.lessonNumber}-й урок · до ${fmt(b.endMin)}",
                            )
                        }
                        val tEnd = atMinute(day, b.endMin)
                        if (tEnd > now.timeInMillis) {
                            schedule(
                                context, am, reqId++, tEnd,
                                "Прозвенел звонок",
                                "Перемена после ${b.lessonNumber}-го урока",
                            )
                        }
                    }
                }
            }
        }
    }

    private fun cancelAll(context: Context, am: AlarmManager) {
        for (i in REQ_BASE until REQ_BASE + 1000) {
            val pi = pi(context, i, null, null, allowNoCreate = true) ?: continue
            am.cancel(pi)
            pi.cancel()
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun schedule(
        context: Context,
        am: AlarmManager,
        reqId: Int,
        triggerAt: Long,
        title: String,
        body: String,
    ) {
        val pi = pi(context, reqId, title, body) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun pi(
        context: Context,
        reqId: Int,
        title: String?,
        body: String?,
        allowNoCreate: Boolean = false,
    ): PendingIntent? {
        val intent = Intent(context, BellReceiver::class.java).apply {
            action = ACTION
            if (title != null) putExtra(EXTRA_TITLE, title)
            if (body != null) putExtra(EXTRA_BODY, body)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        if (allowNoCreate) flags = flags or PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, reqId, intent, flags)
    }

    private fun atMinute(day: Calendar, minOfDay: Int): Long {
        val c = day.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, (minOfDay / 60).coerceIn(0, 23))
        c.set(Calendar.MINUTE, minOfDay % 60)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun fmt(min: Int): String {
        val h = (min / 60) % 24
        val m = min % 60
        return "%02d:%02d".format(h, m)
    }
}
