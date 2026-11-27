package com.example.system

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
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
import com.example.MainActivity

class PdfToolkitGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent(context)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        fun shortcutIntent(toolKey: String): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = PdfToolkitQuickSettingsTileService.ACTION_SHORTCUT
                putExtra(PdfToolkitQuickSettingsTileService.EXTRA_SHORTCUT_TOOL, toolKey)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(20.dp)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PDF Toolkit • Offline",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.height(10.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Button(
                    text = "Scan",
                    onClick = actionStartActivity(shortcutIntent("doc_scanner")),
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(GlanceModifier.width(8.dp))
                Button(
                    text = "Sign",
                    onClick = actionStartActivity(shortcutIntent("sign")),
                    modifier = GlanceModifier.defaultWeight()
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Button(
                    text = "Notes",
                    onClick = actionStartActivity(shortcutIntent("notes")),
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(GlanceModifier.width(8.dp))
                Button(
                    text = "Library",
                    onClick = actionStartActivity(shortcutIntent("library")),
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        }
    }
}

class PdfToolkitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PdfToolkitGlanceWidget()
}
