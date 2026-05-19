package com.devin.schoolbell95.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devin.schoolbell95.AppViewModel
import com.devin.schoolbell95.data.Subject
import java.util.Calendar

private val DOWS = listOf(
    Calendar.MONDAY to "Пн",
    Calendar.TUESDAY to "Вт",
    Calendar.WEDNESDAY to "Ср",
    Calendar.THURSDAY to "Чт",
    Calendar.FRIDAY to "Пт",
    Calendar.SATURDAY to "Сб",
)

@Composable
fun SubjectsScreen(vm: AppViewModel) {
    val selected by vm.selectedSubjectsDow.collectAsState()
    val subjects by vm.subjectsForSelectedDay.collectAsState()

    var editing by remember { mutableStateOf<Subject?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp)),
    ) {
        Text(
            "Уроки 6А",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DOWS.forEach { (dow, label) ->
                FilterChip(
                    selected = selected == dow,
                    onClick = { vm.setSelectedSubjectsDow(dow) },
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.height(4.dp))
                Text("Добавить")
            }
            OutlinedButton(onClick = { confirmReset = true }) {
                Icon(Icons.Filled.Restore, null)
                Spacer(Modifier.height(4.dp))
                Text("Сброс")
            }
        }
        Spacer(Modifier.height(10.dp))

        if (subjects.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "В этот день уроков нет",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(subjects, key = { it.id }) { s ->
                    SubjectRow(
                        subject = s,
                        onEdit = { editing = s },
                        onDelete = { vm.deleteSubject(s) },
                    )
                }
            }
        }
    }

    if (creating) {
        val nextLessonNumber = (subjects.maxOfOrNull { it.lessonNumber } ?: 0) + 1
        SubjectEditDialog(
            initial = Subject(dow = selected, lessonNumber = nextLessonNumber, name = ""),
            title = "Новый урок",
            onDismiss = { creating = false },
            onSave = {
                vm.upsertSubject(it)
                creating = false
            },
        )
    }
    editing?.let { s ->
        SubjectEditDialog(
            initial = s,
            title = "${s.lessonNumber}-й урок",
            onDismiss = { editing = null },
            onSave = {
                vm.upsertSubject(it)
                editing = null
            },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Сбросить расписание уроков?") },
            text = { Text("Восстановит стандартное расписание 6А (по фото).") },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetSubjectsToDefault()
                    confirmReset = false
                }) { Text("Сбросить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun SubjectRow(subject: Subject, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${subject.lessonNumber}.",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                subject.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, null) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, null) }
        }
    }
}

@Composable
private fun SubjectEditDialog(
    initial: Subject,
    title: String,
    onDismiss: () -> Unit,
    onSave: (Subject) -> Unit,
) {
    var lessonText by remember { mutableStateOf(initial.lessonNumber.toString()) }
    var name by remember { mutableStateOf(initial.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = lessonText,
                    onValueChange = { lessonText = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("№ урока") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Предмет") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = lessonText.toIntOrNull() ?: initial.lessonNumber
                onSave(initial.copy(lessonNumber = n, name = name.trim()))
            }, enabled = name.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
