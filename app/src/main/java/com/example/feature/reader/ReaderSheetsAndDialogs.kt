package com.example.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.tts.TtsPlaybackState
import com.example.core.tts.TtsStatus
import com.example.domain.model.Bookmark
import com.example.domain.model.Highlight
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderContentsAndAnnotationsSheet(
    tocEntries: List<ReaderTocEntry>,
    bookmarks: List<Bookmark>,
    highlights: List<Highlight>,
    currentPageIndex: Int,
    onJumpToPage: (Int) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onExportMarkdown: () -> Unit,
    onExportPdf: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 3),
                    modifier = Modifier.testTag("reader_sheet_tab_toc")
                ) { Text("Contents") }
                SegmentedButton(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 3),
                    modifier = Modifier.testTag("reader_sheet_tab_bookmarks")
                ) { Text("Marks (${bookmarks.size})") }
                SegmentedButton(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    shape = SegmentedButtonDefaults.itemShape(2, 3),
                    modifier = Modifier.testTag("reader_sheet_tab_highlights")
                ) { Text("Notes (${highlights.size})") }
            }

            if (tab == 2 && highlights.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportMarkdown,
                        modifier = Modifier.weight(1f).testTag("export_highlights_md_button")
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export .md")
                    }
                    OutlinedButton(
                        onClick = onExportPdf,
                        modifier = Modifier.weight(1f).testTag("export_highlights_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export PDF")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.height(330.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (tab) {
                    0 -> {
                        if (tocEntries.isEmpty()) {
                            item {
                                Text(
                                    "No Table of Contents entries found.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(tocEntries) { entry ->
                                val isCurrent = entry.pageIndex == currentPageIndex
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCurrent) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = (entry.level * 14).dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable {
                                            onJumpToPage(entry.pageIndex)
                                            onDismiss()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.List,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = entry.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "p.${entry.pageIndex + 1}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        if (bookmarks.isEmpty()) {
                            item {
                                Text(
                                    "No bookmarks yet. Tap the bookmark icon on any page.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(bookmarks, key = { it.id }) { bm ->
                                BookmarkRowItem(
                                    bookmark = bm,
                                    onClick = {
                                        onJumpToPage(bm.pageNumber - 1)
                                        onDismiss()
                                    },
                                    onDelete = { onDeleteBookmark(bm.id) }
                                )
                            }
                        }
                    }

                    else -> {
                        if (highlights.isEmpty()) {
                            item {
                                Text(
                                    "No highlights yet. Tap the highlight pen icon to save quotes or margin notes.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(highlights, key = { it.id }) { hl ->
                                HighlightBannerItem(
                                    highlight = hl,
                                    onClick = {
                                        onJumpToPage(hl.pageNumber - 1)
                                        onDismiss()
                                    },
                                    onDelete = { onDeleteHighlight(hl.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSearchSheet(
    query: String,
    hits: List<ReaderSearchHit>,
    totalMatches: Int,
    onQueryChange: (String) -> Unit,
    onJumpToPage: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Search Inside Document", style = MaterialTheme.typography.titleLarge)
                if (query.isNotBlank()) {
                    Text(
                        text = "$totalMatches match(es)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().testTag("reader_search_input"),
                placeholder = { Text("Search keywords…") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true
            )
            LazyColumn(
                modifier = Modifier.height(280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (query.isNotBlank() && hits.isEmpty()) {
                    item {
                        Text(
                            "No matches found for \"$query\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(hits) { hit ->
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    onJumpToPage(hit.pageIndex)
                                    onDismiss()
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        hit.pageLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "${hit.matchCountOnPage} hit(s)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    hit.snippet,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderTtsControlsSheet(
    ttsState: TtsPlaybackState,
    onTogglePlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onRateChange: (Float) -> Unit,
    onSleepTimerSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Read Aloud (On-Device TTS)", style = MaterialTheme.typography.titleLarge)

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = ttsState.currentSentence?.text ?: "Tap Play to begin reading from this page.",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (ttsState.sentences.isNotEmpty()) {
                        LinearProgressIndicator(
                            progress = { ttsState.progressFraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Sentence ${ttsState.currentSentenceIndex + 1} of ${ttsState.sentences.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrev) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.tts_previous_sentence))
                }
                Button(onClick = onTogglePlayPause, modifier = Modifier.testTag("tts_sheet_play_button")) {
                    Icon(
                        if (ttsState.status == TtsStatus.SPEAKING) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (ttsState.status == TtsStatus.SPEAKING) "Pause" else "Play")
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.tts_next_sentence))
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.tts_stop))
                }
            }

            Column {
                Text(
                    "Speed: ${String.format(Locale.US, "%.2fx", ttsState.speechRate)}",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = ttsState.speechRate,
                    onValueChange = onRateChange,
                    valueRange = 0.5f..2.25f,
                    modifier = Modifier.testTag("tts_speed_slider")
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    val sleepLabel = if (ttsState.sleepTimer.isActive) {
                        val m = ttsState.sleepTimer.remainingSeconds / 60
                        val s = ttsState.sleepTimer.remainingSeconds % 60
                        "Sleep Timer (${String.format(Locale.US, "%d:%02d", m, s)} left)"
                    } else {
                        "Sleep Timer (Off)"
                    }
                    Text(sleepLabel, style = MaterialTheme.typography.labelLarge)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Off", 5 to "5m", 15 to "15m", 30 to "30m", 45 to "45m", 60 to "60m").forEach { (mins, label) ->
                        val active = if (mins == 0) !ttsState.sleepTimer.isActive
                        else ttsState.sleepTimer.isActive && ttsState.sleepTimer.totalSeconds == mins * 60
                        FilterChip(
                            selected = active,
                            onClick = { onSleepTimerSelect(mins) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordLookupBottomSheet(
    state: WordLookupState,
    onQueryChange: (String) -> Unit,
    onSelectLanguage: (ReaderTranslateLanguage) -> Unit,
    onDownloadPack: () -> Unit,
    onTranslate: () -> Unit,
    onImportDictionaryPack: () -> Unit,
    onHighlightSnippet: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Dictionary & Offline Translate", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = state.queryText,
                onValueChange = onQueryChange,
                label = { Text("Word or passage") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("lookup_query_input")
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp)) }
                ) { Text("Dictionary") }
                SegmentedButton(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp)) }
                ) { Text("Translate") }
            }

            if (tab == 0) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = state.dictionaryTerm.ifBlank { "Enter a word" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = state.dictionaryDefinition
                                ?: "Not found in the built-in offline glossary. Import an optional offline dictionary pack (.txt or .json) below.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${state.dictionarySourceLabel} • Custom pack: ${state.customPackWordCount} words",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onImportDictionaryPack,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Load Offline Pack")
                    }
                    Button(
                        onClick = { onHighlightSnippet(state.queryText) },
                        enabled = state.queryText.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Highlight")
                    }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderTranslateLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = state.targetLanguage == lang,
                            onClick = { onSelectLanguage(lang) },
                            label = { Text(lang.displayName) }
                        )
                    }
                }

                when {
                    state.isCheckingPack || state.isDownloadingPack || state.isTranslating -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                when {
                                    state.isDownloadingPack -> "Downloading ${state.targetLanguage.displayName} pack via Play Services…"
                                    state.isTranslating -> "Translating on device…"
                                    else -> "Checking local language pack…"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    !state.isLanguagePackDownloaded -> {
                        OutlinedButton(
                            onClick = onDownloadPack,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Download ${state.targetLanguage.displayName} Offline Pack")
                        }
                    }
                }

                Button(
                    onClick = onTranslate,
                    enabled = state.queryText.isNotBlank() && !state.isTranslating && !state.isDownloadingPack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Translate to ${state.targetLanguage.displayName}")
                }

                state.translatedText?.let { translated ->
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Translated (${state.targetLanguage.displayName}) • Runs on device",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(translated, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                state.translationError?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingStatsBottomSheet(
    title: String,
    currentPageNumber: Int,
    totalPages: Int,
    stats: ReaderStatsSummary,
    bookmarksCount: Int,
    highlightsCount: Int,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("On-Device Reading Stats", style = MaterialTheme.typography.titleLarge)
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { (stats.percentComplete / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile("Progress", "${stats.percentComplete}%", "Page $currentPageNumber/$totalPages", Modifier.weight(1f))
                StatTile("Time Left", "~${stats.estimatedMinutesLeft} min", "${stats.totalWordCount} words", Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile("Total Read", "${stats.totalMinutesRead} min", "Session: ${stats.sessionMinutes}m", Modifier.weight(1f))
                StatTile("TTS & Notes", "${stats.totalTtsMinutes} min", "$bookmarksCount marks • $highlightsCount notes", Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHighlightDialog(
    pageNumber: Int,
    initialQuote: String,
    onConfirm: (colorKey: String, quote: String, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var quoteInput by remember(initialQuote) { mutableStateOf(initialQuote) }
    var noteInput by remember { mutableStateOf("") }
    var colorKey by remember { mutableStateOf("yellow") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Save Highlight (Page $pageNumber)",
                style = MaterialTheme.typography.headlineSmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    "yellow" to Color(0xFFFDE047),
                    "teal" to Color(0xFF5EEAD4),
                    "rose" to Color(0xFFFDA4AF),
                    "violet" to Color(0xFFC4B5FD)
                ).forEach { (key, col) ->
                    Surface(
                        shape = CircleShape,
                        color = col,
                        border = if (colorKey == key) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { colorKey = key }
                    ) {}
                }
            }
            OutlinedTextField(
                value = quoteInput,
                onValueChange = { quoteInput = it },
                label = { Text("Quoted text") },
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                label = { Text("Optional margin note") },
                maxLines = 2,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        onConfirm(colorKey, quoteInput, noteInput)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderPasswordDialog(
    title: String,
    error: String?,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var passInput by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Encrypted PDF",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Enter password to read $title",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = passInput,
                onValueChange = { passInput = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = { onSubmit(passInput) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Open")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun BookmarkRowItem(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(bookmark.label, style = MaterialTheme.typography.titleSmall)
                if (bookmark.excerpt.isNotBlank()) {
                    Text(
                        bookmark.excerpt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove bookmark")
            }
        }
    }
}

@Composable
fun HighlightBannerItem(
    highlight: Highlight,
    onClick: () -> Unit = {},
    onDelete: () -> Unit
) {
    val swatch = when (highlight.colorKey) {
        "teal" -> Color(0xFF14B8A6)
        "rose" -> Color(0xFFF43F5E)
        "violet" -> Color(0xFF8B5CF6)
        else -> Color(0xFFEAB308)
    }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = swatch.copy(alpha = 0.14f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Page ${highlight.pageNumber} • ${highlight.colorKey.replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = swatch
                )
                Text("\"${highlight.quotedText}\"", style = MaterialTheme.typography.bodyMedium)
                if (highlight.noteText.isNotBlank()) {
                    Text(
                        highlight.noteText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete highlight")
            }
        }
    }
}
