package com.devin.schoolbell95.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.devin.schoolbell95.BellLogic
import com.devin.schoolbell95.BellState
import com.devin.schoolbell95.data.AppDatabase
import com.devin.schoolbell95.data.Bell
import com.devin.schoolbell95.data.DayKind
import com.devin.schoolbell95.data.Prefs
import com.devin.schoolbell95.data.Subject
import java.util.Calendar
import java.util.TimeZone

class TodayScheduleWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = Prefs(context)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Yekaterinburg"))
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val kind = DayKind.fromCalendarDow(dow)
        val db = AppDatabase.get(context)
        val allBells = db.bellDao().list(prefs.shift, kind).sortedBy { it.startMin }
        val subjects = db.subjectDao().list(dow)
        val maxLesson = subjects.maxOfOrNull { it.lessonNumber }
        val bells = if (maxLesson != null) allBells.filter { it.lessonNumber <= maxLesson } else allBells
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val state = BellLogic.computeState(bells, nowMin, dow == Calendar.SUNDAY)
        val currentId = (state as? BellState.InLesson)?.bell?.id

        val header = when (dow) {
            Calendar.SUNDAY -> "Воскресенье"
            Calendar.MONDAY -> "Понедельник"
            Calendar.TUESDAY -> "Вторник"
            Calendar.WEDNESDAY -> "Среда"
            Calendar.THURSDAY -> "Четверг"
            Calendar.FRIDAY -> "Пятница"
            else -> "Суббота"
        }

        provideContent {
            GlanceTheme {
                Content(header, bells, subjects, currentId)
            }
        }
    }

    @Composable
    private fun Content(header: String, bells: List<Bell>, subjects: List<Subject>, currentId: Long?) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.primaryContainer)
                .cornerRadius(20.dp)
                .padding(12.dp),
        ) {
            Text(
                "Школа №95 · $header",
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimaryContainer,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(GlanceModifier.height(6.dp))
            if (bells.isEmpty()) {
                Text(
                    "Звонков нет",
                    style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer),
                )
            } else {
                bells.forEach { b ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val highlight = b.id == currentId
                        val name = subjects.firstOrNull { it.lessonNumber == b.lessonNumber }?.name
                        Text(
                            "${b.lessonNumber}",
                            style = TextStyle(
                                color = GlanceTheme.colors.onPrimaryContainer,
                                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                            ),
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        Text(
                            "${fmt(b.startMin)}",
                            style = TextStyle(
                                color = GlanceTheme.colors.onPrimaryContainer,
                                fontSize = 12.sp,
                                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        Text(
                            name ?: "урок",
                            style = TextStyle(
                                color = GlanceTheme.colors.onPrimaryContainer,
                                fontSize = 13.sp,
                                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun fmt(min: Int): String {
        val h = (min / 60) % 24
        val m = min % 60
        return "%02d:%02d".format(h, m)
    }
}

class TodayScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayScheduleWidget()
}
