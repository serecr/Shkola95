package com.devin.schoolbell95.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devin.schoolbell95.AppViewModel
import com.devin.schoolbell95.BellLogic
import com.devin.schoolbell95.BellState
import com.devin.schoolbell95.data.Bell
import com.devin.schoolbell95.data.Subject
import com.devin.schoolbell95.ui.dayOfWeekName
import com.devin.schoolbell95.ui.fmtCountdown
import com.devin.schoolbell95.ui.fmtTime
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.TimeZone

private val UFA: TimeZone = TimeZone.getTimeZone("Asia/Yekaterinburg")

@Composable
fun HomeScreen(vm: AppViewModel) {
    val allBells by vm.todayBells.collectAsState()
    val subjects by vm.todaySubjects.collectAsState()
    val shift by vm.shift.collectAsState()

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(500)
        }
    }

    val cal = remember(nowMillis) {
        Calendar.getInstance(UFA).apply { timeInMillis = nowMillis }
    }
    val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val nowSec = cal.get(Calendar.SECOND)
    val isSunday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
    val dowIso = (((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1)

    // When we have a subjects list, hide bells past the last lesson with a subject
    // (e.g. 6А has only 6 lessons on Tue-Fri even though the school rings 7-8 bells).
    val maxLessonWithSubject = subjects.maxOfOrNull { it.lessonNumber }
    val bells = if (maxLessonWithSubject != null) {
        allBells.filter { it.lessonNumber <= maxLessonWithSubject }
    } else allBells

    val state = BellLogic.computeState(bells, nowMin, isSunday)
    val secondsLeft = BellLogic.secondsUntilNextEvent(state, nowMin, nowSec)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(horizontal = 20.dp, vertical = 16.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Школа №95 · Уфа",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${dayOfWeekName(dowIso)} · ${nowTimeString(cal)} · смена $shift",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        AnimatedTimerCard(state, secondsLeft, nowMin, nowSec, subjects)

        Spacer(Modifier.height(20.dp))

        Text(
            "Расписание звонков:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start),
        )
        Spacer(Modifier.height(8.dp))

        if (bells.isEmpty()) {
            Text(
                "Нет звонков. Открой «Звонки», чтобы добавить.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(bells, key = { it.id }) { bell ->
                    val isCurrent = state is BellState.InLesson && state.bell.id == bell.id
                    val subjectName = subjects.firstOrNull { it.lessonNumber == bell.lessonNumber }?.name
                    BellRowCompact(bell, isCurrent, subjectName)
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AnimatedTimerCard(
    state: BellState,
    secondsLeft: Int?,
    nowMin: Int,
    nowSec: Int,
    subjects: List<Subject>,
) {
    val stateKey = when (state) {
        is BellState.InLesson -> "l${state.bell.id}"
        is BellState.InBreak -> "b${state.prevBell.id}"
        BellState.BeforeFirst -> "before"
        BellState.AfterLast -> "after"
        BellState.Weekend -> "weekend"
    }
    AnimatedContent(
        targetState = stateKey,
        transitionSpec = {
            (fadeIn(tween(400)) togetherWith fadeOut(tween(200)))
        },
        label = "timerCard",
    ) { targetState ->
        key(targetState) {
            BigTimerCard(state, secondsLeft, nowMin, nowSec, subjects)
        }
    }
}

@Composable
private fun BigTimerCard(
    state: BellState,
    secondsLeft: Int?,
    nowMin: Int,
    nowSec: Int,
    subjects: List<Subject>,
) {
    fun subjectName(lessonNumber: Int): String? =
        subjects.firstOrNull { it.lessonNumber == lessonNumber }?.name

    val (title, time, subtitle, accent) = when (state) {
        is BellState.InLesson -> {
            val name = subjectName(state.bell.lessonNumber)
            val titleStr = if (name != null) "${state.bell.lessonNumber}-й урок · $name" else "Идёт ${state.bell.lessonNumber}-й урок"
            Quad(
                titleStr,
                fmtCountdown(secondsLeft ?: 0),
                "До звонка · конец в ${fmtTime(state.bell.endMin)}",
                MaterialTheme.colorScheme.primaryContainer,
            )
        }
        is BellState.InBreak -> {
            val nextName = subjectName(state.nextBell.lessonNumber)
            val sub = if (nextName != null)
                "Дальше ${state.nextBell.lessonNumber}-й · $nextName в ${fmtTime(state.nextBell.startMin)}"
            else
                "До ${state.nextBell.lessonNumber}-го урока в ${fmtTime(state.nextBell.startMin)}"
            Quad(
                "Перемена",
                fmtCountdown(secondsLeft ?: 0),
                sub,
                MaterialTheme.colorScheme.tertiaryContainer,
            )
        }
        BellState.BeforeFirst -> Quad(
            "До начала занятий",
            "—",
            "Уроки сегодня ещё не начались",
            MaterialTheme.colorScheme.secondaryContainer,
        )
        BellState.AfterLast -> Quad(
            "Учебный день закончен",
            "🎉",
            "До завтра!",
            MaterialTheme.colorScheme.secondaryContainer,
        )
        BellState.Weekend -> Quad(
            "Воскресенье",
            "Выходной",
            "Сегодня звонков нет",
            MaterialTheme.colorScheme.secondaryContainer,
        )
    }

    val progress = when (state) {
        is BellState.InLesson -> {
            val total = (state.bell.endMin - state.bell.startMin) * 60
            val elapsed = (nowMin - state.bell.startMin) * 60 + nowSec
            if (total > 0) (elapsed.toFloat() / total).coerceIn(0f, 1f) else 0f
        }
        is BellState.InBreak -> {
            val total = (state.nextBell.startMin - state.prevBell.endMin) * 60
            val elapsed = (nowMin - state.prevBell.endMin) * 60 + nowSec
            if (total > 0) (elapsed.toFloat() / total).coerceIn(0f, 1f) else 0f
        }
        else -> 0f
    }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (state is BellState.InLesson || state is BellState.InBreak) 1.04f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accent),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                time,
                fontSize = (64 * pulse).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            if (state is BellState.InLesson || state is BellState.InBreak) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )
            }
        }
    }
}

@Composable
private fun BellRowCompact(bell: Bell, highlight: Boolean, subjectName: String?) {
    val bg = if (highlight) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${bell.lessonNumber}",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    subjectName ?: "${bell.lessonNumber}-й урок",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (subjectName != null) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (subjectName != null) {
                    Text(
                        "${bell.lessonNumber}-й урок",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "${fmtTime(bell.startMin)} — ${fmtTime(bell.endMin)}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private fun nowTimeString(cal: Calendar): String {
    return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
