package com.devin.schoolbell95.ui.screens

import android.app.TimePickerDialog
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.ExperimentalMaterial3Api
import com.devin.schoolbell95.AppViewModel
import com.devin.schoolbell95.data.Bell
import com.devin.schoolbell95.data.DayKind
import com.devin.schoolbell95.ui.fmtTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BellsScreen(vm: AppViewModel) {
    val bells by vm.bells.collectAsState()
    val shift by vm.shift.collectAsState()
    val selectedDayKind by vm.selectedDayKind.collectAsState()

    var showReset by remember { mutableStateOf(false) }
    var showShortened by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Bell?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val nextNumber = (bells.maxOfOrNull { it.lessonNumber } ?: 0) + 1
                    val last = bells.maxByOrNull { it.endMin }
                    val start = (last?.endMin ?: (8 * 60)) + 10
                    editing = Bell(
                        shift = shift,
                        dayKind = selectedDayKind,
                        lessonNumber = nextNumber,
                        startMin = start,
                        endMin = start + 40,
                    )
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Добавить") },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Смена $shift", style = MaterialTheme.typography.titleMedium)
            }
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                val options = listOf(DayKind.MONDAY, DayKind.TUE_SAT)
                options.forEachIndexed { index, kind ->
                    SegmentedButton(
                        selected = selectedDayKind == kind,
                        onClick = { vm.setSelectedDayKind(kind) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    ) {
                        Text(DayKind.label(kind))
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { showShortened = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Сокращён.")
                }
                OutlinedButton(
                    onClick = { showReset = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Обычный")
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(bells, key = { it.id }) { bell ->
                    BellRow(
                        bell = bell,
                        onClick = { editing = bell },
                        onDelete = { vm.deleteBell(bell) },
                    )
                }
            }
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Обычный день?") },
            text = {
                Text(
                    "Вернуть стандартное расписание МАОУ ЦО №95 для «${DayKind.label(selectedDayKind)}» (смена $shift).",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetBellsToDefault()
                    showReset = false
                }) { Text("Применить") }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) { Text("Отмена") }
            },
        )
    }

    if (showShortened) {
        AlertDialog(
            onDismissRequest = { showShortened = false },
            title = { Text("Сокращённый день?") },
            text = {
                Text(
                    "Переключить на 6 уроков по 30 минут (08:30–11:55) для «${DayKind.label(selectedDayKind)}» (смена $shift). Используется в предпраздничные дни.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.applyShortenedSchedule()
                    showShortened = false
                }) { Text("Применить") }
            },
            dismissButton = {
                TextButton(onClick = { showShortened = false }) { Text("Отмена") }
            },
        )
    }

    editing?.let { bell ->
        EditBellDialog(
            bell = bell,
            onDismiss = { editing = null },
            onSave = {
                vm.upsertBell(it)
                editing = null
            },
        )
    }
}

@Composable
private fun BellRow(bell: Bell, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${bell.lessonNumber}",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.size(36.dp).padding(top = 2.dp),
            )
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${fmtTime(bell.startMin)} — ${fmtTime(bell.endMin)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "${bell.endMin - bell.startMin} мин",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null)
            }
        }
    }
}

@Composable
private fun EditBellDialog(
    bell: Bell,
    onDismiss: () -> Unit,
    onSave: (Bell) -> Unit,
) {
    var start by remember { mutableStateOf(bell.startMin) }
    var end by remember { mutableStateOf(bell.endMin) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Урок ${bell.lessonNumber}") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Начало:", modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m -> start = h * 60 + m },
                            (start / 60) % 24, start % 60, true,
                        ).show()
                    }) { Text(fmtTime(start), fontFamily = FontFamily.Monospace) }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Конец:", modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m -> end = h * 60 + m },
                            (end / 60) % 24, end % 60, true,
                        ).show()
                    }) { Text(fmtTime(end), fontFamily = FontFamily.Monospace) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(bell.copy(startMin = start, endMin = end))
            }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
