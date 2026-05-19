package com.devin.schoolbell95.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.devin.schoolbell95.AppViewModel
import com.devin.schoolbell95.ui.theme.AccentPresets

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val shift by vm.shift.collectAsState()
    val className by vm.className.collectAsState()
    val dark by vm.darkMode.collectAsState()
    val accent by vm.accentColor.collectAsState()
    val dynamic by vm.dynamicColor.collectAsState()
    val aiEnabled by vm.aiEnabled.collectAsState()
    val hfToken by vm.hfToken.collectAsState()
    val modelUrl by vm.modelUrl.collectAsState()
    val aiSystemPrompt by vm.aiSystemPrompt.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.titleLarge)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Смена", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = shift == 1,
                        onClick = { vm.setShift(1) },
                        label = { Text("1 смена") },
                    )
                    FilterChip(
                        selected = shift == 2,
                        onClick = { vm.setShift(2) },
                        label = { Text("2 смена") },
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Класс", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = className,
                    onValueChange = { vm.setClassName(it) },
                    label = { Text("Например: 7Б") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Режим темы", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная")
                        .forEach { (key, label) ->
                            FilterChip(
                                selected = dark == key,
                                onClick = { vm.setDarkMode(key) },
                                label = { Text(label) },
                            )
                        }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Цвет оформления", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Material You", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Цвета из обоев телефона",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = dynamic,
                            onCheckedChange = { vm.setDynamicColor(it) },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Text(
                    "Палитра",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (dynamic) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))

                val rows = AccentPresets.chunked(4)
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { preset ->
                            val selected = accent == preset.key && !dynamic
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = !dynamic) { vm.setAccentColor(preset.key) }
                                    .padding(vertical = 4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(preset.swatch)
                                        .border(
                                            width = if (selected) 3.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selected) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    preset.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (dynamic) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        repeat(4 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Локальная AI", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Qwen 2.5 1.5B (Alibaba), запуск на телефоне, без интернета после скачивания.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = aiEnabled,
                        onCheckedChange = { vm.setAiEnabled(it) },
                    )
                }
                Spacer(Modifier.height(12.dp))

                Text(
                    "Hugging Face токен (необязательно)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Qwen 2.5 скачивается без токена. Токен нужен только если в «URL модели» " +
                        "вставлена гейтнутая ссылка (напр. Gemma).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = hfToken,
                    onValueChange = { vm.setHfToken(it) },
                    placeholder = { Text("hf_••••••••") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "URL модели (необязательно)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Оставь пустым, чтобы использовать стандартный Qwen 2.5 1.5B. " +
                        "Можно указать ссылку на свой .task файл.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = modelUrl,
                    onValueChange = { vm.setModelUrl(it) },
                    placeholder = { Text("https://…/model.task") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "Системный промпт",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Поведение AI. Пусто = стандартный «школьный помощник без отказов».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = aiSystemPrompt,
                    onValueChange = { vm.setAiSystemPrompt(it) },
                    placeholder = { Text("Например: «Отвечай как друг, без воды»") },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("О приложении", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text("МАОУ «Школа №95» г. Уфа", style = MaterialTheme.typography.bodyMedium)
                Text("3.5 Alpha by Радмир", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}
