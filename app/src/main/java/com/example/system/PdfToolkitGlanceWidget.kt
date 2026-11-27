package com.example.system

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
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
import com.example.R
import com.example.core.database.dao.LibraryDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetLibraryEntryPoint {
    fun libraryDao(): LibraryDao
}

private data class WidgetSnapshot(
    val unlockedDocCount: Int,
    val latestDocId: String?,
    val latestDocTitle: String?
)

class PdfToolkitGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(COMPACT_SIZE, MEDIUM_SIZE, EXPANDED_SIZE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadSnapshot(context)
        provideContent {
            GlanceTheme {
                WidgetContent(context = context, snapshot = snapshot)
            }
        }
    }

    private suspend fun loadSnapshot(context: Context): WidgetSnapshot = withContext(Dispatchers.IO) {
        runCatching {
            val ep = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetLibraryEntryPoint::class.java
            )
            // Only query unlocked documents so private vault filenames never appear on the home screen
            val docs = ep.libraryDao().observeDocuments(includeLocked = false).first()
            val latest = docs.firstOrNull()
            WidgetSnapshot(
                unlockedDocCount = docs.size,
                latestDocId = latest?.id,
                latestDocTitle = latest?.title
            )
        }.getOrDefault(WidgetSnapshot(0, null, null))
    }

    @Composable
    private fun WidgetContent(context: Context, snapshot: WidgetSnapshot) {
        val size = LocalSize.current
        val isWide = size.width >= MEDIUM_SIZE.width

        fun shortcutIntent(toolKey: String): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = PdfToolkitQuickSettingsTileService.ACTION_SHORTCUT
                putExtra(PdfToolkitQuickSettingsTileService.EXTRA_SHORTCUT_TOOL, toolKey)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

        fun openDocIntent(docId: String): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = PdfToolkitQuickSettingsTileService.ACTION_SHORTCUT
                putExtra(PdfToolkitQuickSettingsTileService.EXTRA_DOCUMENT_ID, docId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(22.dp)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(R.string.widget_title),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.height(2.dp))
            val statusText = if (snapshot.unlockedDocCount > 0) {
                context.getString(R.string.widget_docs_count, snapshot.unlockedDocCount)
            } else {
                context.getString(R.string.widget_empty_hint)
            }
            Text(
                text = statusText,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )

            if (isWide && snapshot.latestDocId != null && !snapshot.latestDocTitle.isNullOrBlank()) {
                Spacer(GlanceModifier.height(6.dp))
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(GlanceTheme.colors.secondaryContainer)
                        .cornerRadius(12.dp)
                        .clickable(actionStartActivity(openDocIntent(snapshot.latestDocId)))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(
                            R.string.widget_resume_prefix,
                            snapshot.latestDocTitle.take(28)
                        ),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSecondaryContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(GlanceModifier.height(10.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Button(
                    text = context.getString(R.string.shortcut_scan_short),
                    onClick = actionStartActivity(shortcutIntent("doc_scanner")),
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(GlanceModifier.width(8.dp))
                Button(
                    text = context.getString(R.string.shortcut_sign_short),
                    onClick = actionStartActivity(shortcutIntent("sign")),
                    modifier = GlanceModifier.defaultWeight()
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Button(
                    text = context.getString(R.string.shortcut_notes_short),
                    onClick = actionStartActivity(shortcutIntent("notes")),
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(GlanceModifier.width(8.dp))
                Button(
                    text = context.getString(R.string.widget_library_short),
                    onClick = actionStartActivity(shortcutIntent("library")),
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        }
    }

    companion object {
        private val COMPACT_SIZE = DpSize(180.dp, 110.dp)
        private val MEDIUM_SIZE = DpSize(250.dp, 130.dp)
        private val EXPANDED_SIZE = DpSize(320.dp, 180.dp)

        suspend fun requestUpdate(context: Context) {
            runCatching { PdfToolkitGlanceWidget().updateAll(context.applicationContext) }
        }
    }
}

class PdfToolkitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PdfToolkitGlanceWidget()
}
