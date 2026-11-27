package com.example.feature.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.tts.TtsPlaybackState
import com.example.core.tts.TtsStatus
import com.example.domain.model.DocumentCategory
import com.example.domain.model.LibraryDocument
import com.example.domain.model.LibrarySortOrder
import com.example.ui.theme.LiterataFontFamily
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTtsRoute(
    onOpenReader: (String) -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importDocument(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.ShowMessage -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = if (event.undoDocId != null) context.getString(R.string.undo) else null,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed && event.undoDocId != null) {
                        viewModel.undoDelete(event.undoDocId)
                    }
                }
                is LibraryEvent.LaunchShareIntent -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        putExtra(Intent.EXTRA_SUBJECT, event.title)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, event.title))
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.library_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = stringResource(R.string.library_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    openDocLauncher.launch(
                        arrayOf(
                            "application/pdf",
                            "application/epub+zip",
                            "text/plain",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        )
                    )
                },
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("import_document_fab"),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.import_document)) },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
            ) {
                AnimatedVisibility(
                    visible = ttsState.sentences.isNotEmpty() || ttsState.status == TtsStatus.SPEAKING,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    TtsEngineDeck(
                        ttsState = ttsState,
                        onPlayPause = viewModel::togglePlayPause,
                        onStop = viewModel::stopTts,
                        onPrevious = viewModel::previousSentence,
                        onNext = viewModel::nextSentence,
                        onRateChange = viewModel::setSpeechRate,
                        onSelectVoice = viewModel::selectVoice,
                        onSleepTimerSelected = viewModel::setSleepTimer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "header_banner") {
                        OfflineBadgeCard(
                            totalDocs = uiState.documents.size,
                            totalTtsMinutes = uiState.totalTtsMinutes
                        )
                    }

                    item(key = "search_bar") {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("library_search_input"),
                            placeholder = { Text(stringResource(R.string.search_library_hint)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.large
                        )
                    }

                    item(key = "category_chips") {
                        CategoryFilterRow(
                            categories = uiState.categories,
                            selectedCategoryId = uiState.selectedCategoryId,
                            sortOrder = uiState.sortOrder,
                            onSelectCategory = viewModel::onCategorySelected,
                            onSelectSort = viewModel::onSortOrderSelected
                        )
                    }

                    if (uiState.isImporting) {
                        item(key = "importing_indicator") {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }

                    if (uiState.documents.isEmpty()) {
                        item(key = "empty_state") {
                            EmptyLibraryCard(
                                onLoadSample = viewModel::loadSamplePdf,
                                onImportSaf = {
                                    openDocLauncher.launch(arrayOf("application/pdf", "text/plain"))
                                }
                            )
                        }
                    } else {
                        items(
                            items = uiState.documents,
                            key = { it.id }
                        ) { doc ->
                            val category = uiState.categories.firstOrNull { it.id == doc.categoryId }
                            val isActiveTts = ttsState.documentId == doc.id &&
                                (ttsState.status == TtsStatus.SPEAKING || ttsState.status == TtsStatus.PAUSED)
                            LibraryDocumentCard(
                                document = doc,
                                category = category,
                                isActiveTts = isActiveTts,
                                onOpenClick = { onOpenReader(doc.id) },
                                onListenClick = { viewModel.listenToDocument(doc) },
                                onLockVaultClick = { viewModel.moveToVault(doc) },
                                onShareClick = { viewModel.shareDocument(doc) },
                                onDeleteClick = { viewModel.deleteDocument(doc) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineBadgeCard(
    totalDocs: Int,
    totalTtsMinutes: Int
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.section_one_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.runs_on_device),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "$totalDocs indexed files • $totalTtsMinutes min listened",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<DocumentCategory>,
    selectedCategoryId: String?,
    sortOrder: LibrarySortOrder,
    onSelectCategory: (String?) -> Unit,
    onSelectSort: (LibrarySortOrder) -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategoryId == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text(stringResource(R.string.category_all)) },
                    modifier = Modifier.testTag("category_chip_all")
                )
            }
            items(categories, key = { it.id }) { category ->
                FilterChip(
                    selected = selectedCategoryId == category.id,
                    onClick = {
                        onSelectCategory(if (selectedCategoryId == category.id) null else category.id)
                    },
                    label = { Text(category.name) },
                    modifier = Modifier.testTag("category_chip_${category.id}")
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box {
            AssistChip(
                onClick = { sortMenuExpanded = true },
                label = {
                    Text(
                        when (sortOrder) {
                            LibrarySortOrder.RECENT -> "Recent"
                            LibrarySortOrder.TITLE_AZ -> "A–Z"
                            LibrarySortOrder.SIZE_DESC -> "Size"
                        }
                    )
                }
            )
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Recent") },
                    onClick = {
                        onSelectSort(LibrarySortOrder.RECENT)
                        sortMenuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("A to Z") },
                    onClick = {
                        onSelectSort(LibrarySortOrder.TITLE_AZ)
                        sortMenuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Largest first") },
                    onClick = {
                        onSelectSort(LibrarySortOrder.SIZE_DESC)
                        sortMenuExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(
    onLoadSample: () -> Unit,
    onImportSaf: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.empty_library_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.empty_library_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onLoadSample,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("load_sample_pdf_button")
                ) {
                    Text(stringResource(R.string.import_sample))
                }
                OutlinedButton(
                    onClick = onImportSaf,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("import_saf_empty_button")
                ) {
                    Text("Pick File")
                }
            }
        }
    }
}

@Composable
private fun LibraryDocumentCard(
    document: LibraryDocument,
    category: DocumentCategory?,
    isActiveTts: Boolean,
    onOpenClick: () -> Unit,
    onListenClick: () -> Unit,
    onLockVaultClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isActiveTts) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_doc_card_${document.id}")
            .clickable(onClick = onOpenClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 74.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val thumbFile = document.thumbnailPath?.let { File(it) }
                if (thumbFile != null && thumbFile.exists()) {
                    AsyncImage(
                        model = thumbFile,
                        contentDescription = document.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (document.mimeType == "application/pdf") {
                            Icons.Default.PictureAsPdf
                        } else {
                            Icons.Default.Description
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (category != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(category.colorHex).copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(category.colorHex),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${stringResource(R.string.pages_format, document.pageCount)} • ${formatFileSize(document.fileSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onListenClick,
                modifier = Modifier.testTag("listen_doc_button_${document.id}")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.open_tts_listen),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onLockVaultClick,
                modifier = Modifier.testTag("vault_lock_doc_button_${document.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock in Vault"
                )
            }
            IconButton(
                onClick = onShareClick,
                modifier = Modifier.testTag("share_doc_button_${document.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.share_document)
                )
            }
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.testTag("delete_doc_button_${document.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.delete_document)
                )
            }
        }
    }
}

@Composable
private fun TtsEngineDeck(
    ttsState: TtsPlaybackState,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRateChange: (Float) -> Unit,
    onSelectVoice: (String) -> Unit,
    onSleepTimerSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    var sleepMenuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("tts_engine_deck")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ttsState.documentTitle.ifBlank { stringResource(R.string.tts_player_title) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val statusLabel = when (ttsState.status) {
                        TtsStatus.SPEAKING -> stringResource(R.string.tts_speaking)
                        TtsStatus.PAUSED -> stringResource(R.string.tts_paused)
                        TtsStatus.INITIALIZING -> stringResource(R.string.tts_initializing)
                        TtsStatus.UNAVAILABLE -> stringResource(R.string.tts_unavailable)
                        TtsStatus.ERROR -> ttsState.errorMessage ?: "TTS Error"
                        else -> stringResource(R.string.tts_ready)
                    }
                    Text(
                        text = "$statusLabel • ${
                            if (ttsState.sentences.isNotEmpty()) {
                                stringResource(
                                    R.string.sentences_progress_format,
                                    ttsState.currentSentenceIndex + 1,
                                    ttsState.sentences.size
                                )
                            } else {
                                "0/0"
                            }
                        }",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    AssistChip(
                        onClick = { sleepMenuExpanded = true },
                        leadingIcon = { Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = {
                            if (ttsState.sleepTimer.isActive) {
                                val min = ttsState.sleepTimer.remainingSeconds / 60
                                val sec = ttsState.sleepTimer.remainingSeconds % 60
                                Text(stringResource(R.string.tts_sleep_timer_active, min, sec))
                            } else {
                                Text("Sleep")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = sleepMenuExpanded,
                        onDismissRequest = { sleepMenuExpanded = false }
                    ) {
                        listOf(0 to "Off", 5 to "5 min", 15 to "15 min", 30 to "30 min", 60 to "60 min").forEach { (mins, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onSleepTimerSelected(mins)
                                    sleepMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Highlighted Active Sentence Preview
            val currentSentence = ttsState.currentSentence
            if (currentSentence != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val annotated = buildAnnotatedString {
                        val range = ttsState.wordRangeInSentence
                        val raw = currentSentence.text
                        if (range != null && range.first in raw.indices && range.last < raw.length) {
                            append(raw.substring(0, range.first))
                            withStyle(
                                SpanStyle(
                                    background = MaterialTheme.colorScheme.secondaryContainer,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            ) {
                                append(raw.substring(range.first, range.last + 1))
                            }
                            append(raw.substring(range.last + 1))
                        } else {
                            append(raw)
                        }
                    }
                    Text(
                        text = annotated,
                        fontFamily = LiterataFontFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { ttsState.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
            )

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier.testTag("tts_prev_button")
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.tts_previous_sentence))
                    }
                    FilledIconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("tts_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (ttsState.status == TtsStatus.SPEAKING) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = if (ttsState.status == TtsStatus.SPEAKING) {
                                stringResource(R.string.tts_pause)
                            } else {
                                stringResource(R.string.tts_play)
                            }
                        )
                    }
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.testTag("tts_next_button")
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.tts_next_sentence))
                    }
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.testTag("tts_stop_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.tts_stop))
                    }
                }

                Box {
                    AssistChip(
                        onClick = { voiceMenuExpanded = true },
                        leadingIcon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = {
                            Text(
                                text = ttsState.selectedVoice?.localeTag ?: "Voice",
                                maxLines = 1
                            )
                        }
                    )
                    DropdownMenu(
                        expanded = voiceMenuExpanded,
                        onDismissRequest = { voiceMenuExpanded = false }
                    ) {
                        if (ttsState.availableVoices.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tts_voice_default)) },
                                onClick = { voiceMenuExpanded = false }
                            )
                        } else {
                            ttsState.availableVoices.take(12).forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text(voice.displayName) },
                                    onClick = {
                                        onSelectVoice(voice.name)
                                        voiceMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = String.format(Locale.US, "%.2fx", ttsState.speechRate),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(48.dp)
                )
                Slider(
                    value = ttsState.speechRate,
                    onValueChange = onRateChange,
                    valueRange = 0.5f..2.5f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tts_speed_slider")
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.toInt().coerceAtLeast(1)} KB"
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}
