package com.devin.schoolbell95.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
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
import androidx.glance.action.actionStartActivity
import com.devin.schoolbell95.MainActivity

class AssistantShortcutWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                Content()
            }
        }
    }

    @Composable
    private fun Content() {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.tertiaryContainer)
                .cornerRadius(24.dp)
                .clickable(actionStartActivity<MainActivity>())
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = GlanceModifier.fillMaxWidth(),
            ) {
                Text(
                    "✨",
                    style = TextStyle(fontSize = 28.sp),
                )
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    "Помощник",
                    style = TextStyle(
                        color = GlanceTheme.colors.onTertiaryContainer,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    ),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    "Школа №95",
                    style = TextStyle(
                        color = GlanceTheme.colors.onTertiaryContainer,
                        fontSize = 12.sp,
                    ),
                )
            }
        }
    }
}

class AssistantShortcutWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AssistantShortcutWidget()
}
