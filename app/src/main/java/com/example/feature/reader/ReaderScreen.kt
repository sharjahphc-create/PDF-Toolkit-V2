package com.example.feature.reader

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.datastore.ReaderFontChoice
import com.example.core.datastore.ReaderThemeMode
import com.example.core.datastore.UserPreferences
import com.example.core.tts.TtsPlaybackState
import com.example.core.tts.TtsStatus
import com.example.domain.model.Bookmark
import com.example.domain.model.Highlight
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiterataFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import kotlin.math.roundToInt

@Composable
fun ReaderRoute(
    documentId: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    LaunchedEffect(documentId) {
        viewModel.openDocument(documentId)
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { ev ->
            when (ev) {
                is ReaderEvent.ShowMessage -> snackbarHostState.showSnackbar(ev.message)
            }
        }
    }

    ReaderScreen(
        state = state,
        prefs = prefs,
        ttsState = ttsState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onPageChange = viewModel::goToPage,
        onToggleControls = viewModel::toggleControlsVisibility,
        onToggleReflow = viewModel::toggleReflowTextMode,
        onToggleBookmark = viewModel::toggleCurrentPageBookmark,
        onDeleteBookmark = viewModel::deleteBookmark,
        onAddHighlight = viewModel::addHighlight,
        onDeleteHighlight = viewModel::deleteHighlight,
        onSearchQuery = viewModel::searchInDocument,
        onToggleTts = viewModel::startOrToggleTtsFromCurrentPage,
        onPrevTts = viewModel::prevTtsSentence,
        onNextTts = viewModel::nextTtsSentence,
        onStopTts = viewModel::stopTts,
        onReaderTheme = viewModel::setReaderTheme,
        onReaderFont = viewModel::setReaderFont,
        onReaderFontScale = viewModel::setReaderFontScale,
        onReaderBrightness = viewModel::setReaderBrightness,
        onReaderWarmLight = viewModel::setReaderWarmLight,
        onSubmitPassword = viewModel::submitPdfPassword
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    prefs: UserPreferences,
    ttsState: TtsPlaybackState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onPageChange: (Int) -> Unit,
    onToggleControls: () -> Unit,
    onToggleReflow: () -> Unit,
    onToggleBookmark: () -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onAddHighlight: (String, String, String) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onSearchQuery: (String) -> Unit,
    onToggleTts: () -> Unit,
    onPrevTts: () -> Unit,
    onNextTts: () -> Unit,
    onStopTts: () -> Unit,
    onReaderTheme: (ReaderThemeMode) -> Unit,
    onReaderFont: (ReaderFontChoice) -> Unit,
    onReaderFontScale: (Float) -> Unit,
    onReaderBrightness: (Float) -> Unit,
    onReaderWarmLight: (Float) -> Unit,
    onSubmitPassword: (String) -> Unit
) {
    val context = LocalContext.current
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showAnnotationsSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showAddHighlightDialog by remember { mutableStateOf(false) }

    // Apply per-reader brightness override cleanly and restore on leave
    DisposableEffect(prefs.readerBrightness) {
        val window = (context as? Activity)?.window
        val lp = window?.attributes
        if (window != null && lp != null) {
            lp.screenBrightness = if (prefs.readerBrightness < 0f) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                prefs.readerBrightness.coerceIn(0.05f, 1.0f)
            }
            window.attributes = lp
        }
        onDispose {
            val resetWindow = (context as? Activity)?.window
            val resetLp = resetWindow?.attributes
            if (resetWindow != null && resetLp != null) {
                resetLp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                resetWindow.attributes = resetLp
            }
        }
    }

    val (surfaceBg, textInk) = when (prefs.readerTheme) {
        ReaderThemeMode.PAPER -> Color(0xFFFDFCF9) to Color(0xFF1F2937)
        ReaderThemeMode.SEPIA -> Color(0xFFF5ECD8) to Color(0xFF3E2E1E)
        ReaderThemeMode.SLATE_DARK -> Color(0xFF161B22) to Color(0xFFE6EDF3)
        ReaderThemeMode.OLED_BLACK -> Color(0xFF000000) to Color(0xFFE5E7EB)
    }

    val readerFontFamily = when (prefs.readerFontChoice) {
        ReaderFontChoice.SANS -> PlusJakartaSansFontFamily
        ReaderFontChoice.SERIF -> LiterataFontFamily
        ReaderFontChoice.MONO -> JetBrainsMonoFontFamily
    }

    val currentPageNumber = state.currentPage + 1
    val isCurrentBookmarked = state.bookmarks.any { it.pageNumber == currentPageNumber }

    Box(modifier = Modifier.fillMaxSize().background(surfaceBg)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = surfaceBg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding()) },
            topBar = {
                AnimatedVisibility(visible = state.isControlsVisible) {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = onBack, modifier = Modifier.testTag("reader_back_button")) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back), tint = textInk)
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = state.document?.title ?: "Reader",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = textInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Page $currentPageNumber of ${state.totalPages}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textInk.copy(alpha = 0.72f)
                                )
                            }
                        },
                        actions = {
                            if (state.contentKind == ReaderContentKind.PDF) {
                                IconButton(
                                    onClick = onToggleReflow,
                                    modifier = Modifier.testTag("reader_toggle_reflow_button")
                                ) {
                                    Icon(
                                        imageVector = if (state.reflowTextMode) Icons.Default.PictureAsPdf else Icons.AutoMirrored.Filled.Article,
                                        contentDescription = "Switch PDF / Text view",
                                        tint = textInk
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showSearchSheet = true },
                                modifier = Modifier.testTag("reader_search_button")
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Search inside document", tint = textInk)
                            }
                            IconButton(
                                onClick = onToggleBookmark,
                                modifier = Modifier.testTag("reader_bookmark_button")
                            ) {
                                Icon(
                                    imageVector = if (isCurrentBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Toggle bookmark",
                                    tint = if (isCurrentBookmarked) MaterialTheme.colorScheme.primary else textInk
                                )
                            }
                            IconButton(
                                onClick = { showAnnotationsSheet = true },
                                modifier = Modifier.testTag("reader_annotations_button")
                            ) {
                                Icon(Icons.Default.Bookmarks, contentDescription = "Bookmarks & highlights", tint = textInk)
                            }
                            IconButton(
                                onClick = { showSettingsSheet = true },
                                modifier = Modifier.testTag("reader_settings_button")
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = "Reader display controls", tint = textInk)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = surfaceBg.copy(alpha = 0.94f)
                        )
                    )
                }
            },
            bottomBar = {
                AnimatedVisibility(visible = state.isControlsVisible) {
                    Surface(
                        color = surfaceBg.copy(alpha = 0.96f),
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (ttsState.documentId == state.document?.id &&
                                (ttsState.status == TtsStatus.SPEAKING || ttsState.status == TtsStatus.PAUSED)
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = ttsState.currentSentence?.text ?: "Read Aloud active",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row {
                                            IconButton(onClick = onPrevTts) {
                                                Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.tts_previous_sentence))
                                            }
                                            IconButton(onClick = onToggleTts) {
                                                Icon(
                                                    if (ttsState.status == TtsStatus.SPEAKING) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "Play or pause read aloud"
                                                )
                                            }
                                            IconButton(onClick = onNextTts) {
                                                Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.tts_next_sentence))
                                            }
                                            IconButton(onClick = onStopTts) {
                                                Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.tts_stop))
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { onPageChange(state.currentPage - 1) },
                                    enabled = state.currentPage > 0,
                                    modifier = Modifier.testTag("reader_prev_page_button")
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous page", tint = textInk)
                                }

                                if (state.totalPages > 1) {
                                    Slider(
                                        value = state.currentPage.toFloat(),
                                        onValueChange = { onPageChange(it.roundToInt()) },
                                        valueRange = 0f..(state.totalPages - 1).toFloat(),
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }

                                IconButton(
                                    onClick = { onPageChange(state.currentPage + 1) },
                                    enabled = state.currentPage < state.totalPages - 1,
                                    modifier = Modifier.testTag("reader_next_page_button")
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Next page", tint = textInk)
                                }

                                IconButton(
                                    onClick = { showAddHighlightDialog = true },
                                    modifier = Modifier.testTag("reader_highlight_button")
                                ) {
                                    Icon(Icons.Default.BorderColor, contentDescription = "Add highlight", tint = textInk)
                                }

                                IconButton(
                                    onClick = onToggleTts,
                                    modifier = Modifier.testTag("reader_tts_button")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Read aloud",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator()
                    }
                    state.errorMessage != null -> {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(state.errorMessage, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.navigate_back)) }
                        }
                    }
                    state.contentKind == ReaderContentKind.PDF && !state.reflowTextMode -> {
                        val pageBmp = state.pageBitmaps[state.currentPage]
                        var zoom by remember(state.currentPage) { mutableFloatStateOf(1f) }
                        var panX by remember(state.currentPage) { mutableFloatStateOf(0f) }
                        var panY by remember(state.currentPage) { mutableFloatStateOf(0f) }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(state.currentPage) {
                                    detectTapGestures(
                                        onTap = { onToggleControls() },
                                        onDoubleTap = {
                                            if (zoom > 1.2f) {
                                                zoom = 1f; panX = 0f; panY = 0f
                                            } else {
                                                zoom = 2.0f
                                            }
                                        }
                                    )
                                }
                                .pointerInput(state.currentPage) {
                                    detectTransformGestures { _, pan, gestureZoom, _ ->
                                        val nextZoom = (zoom * gestureZoom).coerceIn(1f, 3.5f)
                                        zoom = nextZoom
                                        if (nextZoom > 1f) {
                                            panX += pan.x
                                            panY += pan.y
                                        } else {
                                            panX = 0f; panY = 0f
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (pageBmp != null && !pageBmp.isRecycled) {
                                Image(
                                    bitmap = pageBmp.asImageBitmap(),
                                    contentDescription = "PDF page $currentPageNumber",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 820.dp)
                                        .padding(12.dp)
                                        .graphicsLayer {
                                            scaleX = zoom
                                            scaleY = zoom
                                            translationX = panX
                                            translationY = panY
                                        }
                                )
                            } else {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    else -> {
                        val section = state.textSections.getOrNull(state.currentPage)
                        val pageHighlights = state.highlights.filter { it.pageNumber == currentPageNumber }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 760.dp)
                                .verticalScroll(rememberScrollState())
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { onToggleControls() })
                                }
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = section?.title ?: "Page $currentPageNumber",
                                fontFamily = readerFontFamily,
                                fontSize = (20f * prefs.readerFontScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textInk
                            )
                            Text(
                                text = section?.body.orEmpty().ifBlank { "No extractable text on this page. Toggle Page View for scanned PDFs." },
                                fontFamily = readerFontFamily,
                                fontSize = (16f * prefs.readerFontScale).sp,
                                lineHeight = (25f * prefs.readerFontScale).sp,
                                color = textInk
                            )
                            if (pageHighlights.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Highlights on this page",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = textInk.copy(alpha = 0.75f)
                                )
                                pageHighlights.forEach { hl ->
                                    HighlightBannerItem(highlight = hl, onDelete = { onDeleteHighlight(hl.id) })
                                }
                            }
                        }
                    }
                }
            }
        }

        // Warm light amber reading overlay (non-intercepting)
        if (prefs.readerWarmLight > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFA726).copy(alpha = prefs.readerWarmLight.coerceIn(0f, 0.45f)))
            )
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Reader Appearance", style = MaterialTheme.typography.titleLarge)

                Text("Theme", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ReaderThemeMode.PAPER to "Paper",
                        ReaderThemeMode.SEPIA to "Sepia",
                        ReaderThemeMode.SLATE_DARK to "Dark",
                        ReaderThemeMode.OLED_BLACK to "OLED"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = prefs.readerTheme == mode,
                            onClick = { onReaderTheme(mode) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("reader_theme_${mode.name}")
                        )
                    }
                }

                Text("Typography", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ReaderFontChoice.entries.forEachIndexed { idx, font ->
                        SegmentedButton(
                            selected = prefs.readerFontChoice == font,
                            onClick = { onReaderFont(font) },
                            shape = SegmentedButtonDefaults.itemShape(idx, ReaderFontChoice.entries.size)
                        ) {
                            Text(
                                when (font) {
                                    ReaderFontChoice.SANS -> "Sans"
                                    ReaderFontChoice.SERIF -> "Serif"
                                    ReaderFontChoice.MONO -> "Mono"
                                }
                            )
                        }
                    }
                }

                Column {
                    Text(
                        "Font Size (${(prefs.readerFontScale * 100).roundToInt()}%)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = prefs.readerFontScale,
                        onValueChange = onReaderFontScale,
                        valueRange = 0.85f..1.6f
                    )
                }

                Column {
                    val brightLabel = if (prefs.readerBrightness < 0f) "System" else "${(prefs.readerBrightness * 100).roundToInt()}%"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Brightness ($brightLabel)", style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { onReaderBrightness(-1f) }) { Text("Use System") }
                    }
                    Slider(
                        value = if (prefs.readerBrightness < 0f) 0.55f else prefs.readerBrightness,
                        onValueChange = onReaderBrightness,
                        valueRange = 0.08f..1.0f
                    )
                }

                Column {
                    Text(
                        "Warm Light Filter (${(prefs.readerWarmLight * 100).roundToInt()}%)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = prefs.readerWarmLight,
                        onValueChange = onReaderWarmLight,
                        valueRange = 0f..0.42f
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showAnnotationsSheet) {
        var tab by remember { mutableIntStateOf(0) }
        ModalBottomSheet(onDismissRequest = { showAnnotationsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("Bookmarks (${state.bookmarks.size})") }
                    SegmentedButton(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("Highlights (${state.highlights.size})") }
                }

                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (tab == 0) {
                        if (state.bookmarks.isEmpty()) {
                            item {
                                Text(
                                    "No bookmarks yet. Tap the bookmark icon on any page.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(state.bookmarks, key = { it.id }) { bm ->
                                BookmarkRowItem(
                                    bookmark = bm,
                                    onClick = {
                                        onPageChange(bm.pageNumber - 1)
                                        showAnnotationsSheet = false
                                    },
                                    onDelete = { onDeleteBookmark(bm.id) }
                                )
                            }
                        }
                    } else {
                        if (state.highlights.isEmpty()) {
                            item {
                                Text(
                                    "No highlights yet. Tap the highlight pen icon to save quotes or notes.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(state.highlights, key = { it.id }) { hl ->
                                HighlightBannerItem(
                                    highlight = hl,
                                    onClick = {
                                        onPageChange(hl.pageNumber - 1)
                                        showAnnotationsSheet = false
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

    if (showSearchSheet) {
        ModalBottomSheet(onDismissRequest = { showSearchSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Search Inside Document", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQuery,
                    modifier = Modifier.fillMaxWidth().testTag("reader_search_input"),
                    placeholder = { Text("Search keywords…") },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true
                )
                LazyColumn(
                    modifier = Modifier.height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.searchHits) { hit ->
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    onPageChange(hit.pageIndex)
                                    showSearchSheet = false
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(hit.pageLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(hit.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddHighlightDialog) {
        var quoteInput by remember {
            mutableStateOf(
                state.textSections.getOrNull(state.currentPage)?.body?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(120).orEmpty()
            )
        }
        var noteInput by remember { mutableStateOf("") }
        var colorKey by remember { mutableStateOf("yellow") }

        AlertDialog(
            onDismissRequest = { showAddHighlightDialog = false },
            title = { Text("Save Highlight (Page $currentPageNumber)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(
                            "yellow" to Color(0xFFFDE047),
                            "teal" to Color(0xFF5EEAD4),
                            "rose" to Color(0xFFFDA4AF),
                            "violet" to Color(0xFFC4B5FD)
                        ).forEach { (key, col) ->
                            Surface(
                                shape = CircleShape,
                                color = col,
                                border = if (colorKey == key) androidx.compose.foundation.BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable { colorKey = key }
                            ) {}
                        }
                    }
                    OutlinedTextField(
                        value = quoteInput,
                        onValueChange = { quoteInput = it },
                        label = { Text("Quoted text") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("Optional margin note") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAddHighlight(colorKey, quoteInput, noteInput)
                    showAddHighlightDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddHighlightDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (state.passwordRequiredFile != null) {
        var passInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Encrypted PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter password to read ${state.document?.title ?: "PDF"}")
                    OutlinedTextField(
                        value = passInput,
                        onValueChange = { passInput = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.passwordError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onSubmitPassword(passInput) }) { Text("Open") }
            },
            dismissButton = {
                TextButton(onClick = onBack) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun BookmarkRowItem(
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
private fun HighlightBannerItem(
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(swatch)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Page ${highlight.pageNumber}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(highlight.quotedText, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (highlight.noteText.isNotBlank()) {
                    Text(highlight.noteText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete highlight")
            }
        }
    }
}
