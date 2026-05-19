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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.devin.schoolbell95.BellLogic
import com.devin.schoolbell95.BellState
import com.devin.schoolbell95.data.AppDatabase
import com.devin.schoolbell95.data.DayKind
import com.devin.schoolbell95.data.Prefs
import java.util.Calendar
import java.util.TimeZone

class NextBellWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = Prefs(context)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Yekaterinburg"))
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val dayKind = DayKind.fromCalendarDow(dow)
        val bells = AppDatabase.get(context).bellDao().list(prefs.shift, dayKind)
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val isSunday = dow == Calendar.SUNDAY
        val state = BellLogic.computeState(bells, nowMin, isSunday)
        val seconds = BellLogic.secondsUntilNextEvent(state, nowMin, cal.get(Calendar.SECOND)) ?: 0

        val title = when (state) {
            is BellState.InLesson -> "Урок ${state.bell.lessonNumber}"
            is BellState.InBreak -> "Перемена"
            BellState.BeforeFirst -> "До уроков"
            BellState.AfterLast -> "Уроки окончены"
            BellState.Weekend -> "Воскресенье"
        }
        val counter = when (state) {
            is BellState.InLesson -> formatTime(seconds)
            is BellState.InBreak -> formatTime(seconds)
            BellState.BeforeFirst -> bells.firstOrNull()?.let { "в ${fmt(it.startMin)}" } ?: "—"
            BellState.AfterLast -> "🎉"
            BellState.Weekend -> "—"
        }
        val sub = when (state) {
            is BellState.InLesson -> "до звонка"
            is BellState.InBreak -> "до ${state.nextBell.lessonNumber}-го урока"
            else -> "Школа №95"
        }

        provideContent {
            GlanceTheme {
                Widget(title, counter, sub)
            }
        }
    }

    @Composable
    private fun Widget(title: String, counter: String, sub: String) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.primaryContainer)
                .cornerRadius(20.dp)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = GlanceModifier.fillMaxWidth(),
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimaryContainer,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = counter,
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimaryContainer,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = sub,
                    style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer),
                )
            }
        }
    }

    private fun formatTime(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec)
        else "%02d:%02d".format(m, sec)
    }

    private fun fmt(min: Int): String {
        val h = (min / 60) % 24
        val m = min % 60
        return "%02d:%02d".format(h, m)
    }
}

class NextBellWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextBellWidget()
}
