package com.devin.schoolbell95.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.devin.schoolbell95.AppViewModel
import com.devin.schoolbell95.notif.BellScheduler

@Composable
fun NotificationsScreen(vm: AppViewModel) {
    val notifyBell by vm.notifyBell.collectAsState()
    val notify5 by vm.notify5.collectAsState()
    val context = LocalContext.current

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result not needed; just request */ }

    Column(
        Modifier
            .fillMaxSize()
            .padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Уведомления", style = MaterialTheme.typography.titleLarge)
        Text(
            "Приложение пришлёт уведомление в момент звонка и за 5 минут до начала урока. Работает офлайн.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SwitchRow(
            title = "Звонок на/с урока",
            subtitle = "Уведомление точно в момент звонка",
            checked = notifyBell,
            onChange = { vm.setNotifyBell(it) },
        )
        SwitchRow(
            title = "За 5 минут до урока",
            subtitle = "Подсказка, что скоро прозвенит",
            checked = notify5,
            onChange = { vm.setNotify5(it) },
        )

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                if (Build.VERSION.SDK_INT >= 33) {
                    permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                BellScheduler.rescheduleAll(context)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Запросить разрешение и обновить") }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                OutlinedButton(
                    onClick = {
                        val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(i)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Разрешить точные будильники") }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
