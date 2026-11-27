package com.example.feature.reader

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.datastore.PdfColorFilterMode
import com.example.core.datastore.PdfFitMode
import com.example.core.datastore.ReaderBookPreset
import com.example.core.datastore.ReaderFontChoice
import com.example.core.datastore.ReaderOrientationLock
import com.example.core.datastore.ReaderPageTurnMode
import com.example.core.datastore.ReaderTextAlignment
import com.example.core.datastore.ReaderThemeMode
import com.example.core.datastore.UserPreferences
import com.example.core.tts.TtsPlaybackState
import com.example.core.tts.TtsStatus
import com.example.ui.theme.AtkinsonHyperlegibleFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiterataFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import java.time.LocalTime
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
    val context = LocalContext.current

    val dictPackPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importOfflineDictionaryPack(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { ev ->
            when (ev) {
                is ReaderEvent.ShowMessage -> snackbarHostState.showSnackbar(ev.message)
                is ReaderEvent.ShareExportedFile -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = ev.mimeType
                        putExtra(Intent.EXTRA_STREAM, ev.uri)
                        putExtra(Intent.EXTRA_SUBJECT, ev.title)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, ev.title))
                }
            }
        }
    }

    val isLockedDocument = state.document?.isLocked == true
    DisposableEffect(isLockedDocument) {
        val window = (context as? Activity)?.window
        if (isLockedDocument) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (isLockedDocument) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    val effectivePreset = prefs.effectiveReaderPreset(state.document?.id)
    val isPerBookPreset = prefs.hasPerBookPreset(state.document?.id)

    ReaderScreen(
        state = state,
        preset = effectivePreset,
        isPerBookPreset = isPerBookPreset,
        ttsState = ttsState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onPageChange = viewModel::goToPage,
        onSaveScrollFraction = viewModel::saveScrollFraction,
        onEnsurePageRendered = viewModel::ensurePageRendered,
        onToggleControls = viewModel::toggleControlsVisibility,
        onToggleReflow = viewModel::toggleReflowTextMode,
        onDismissLayoutWarning = viewModel::dismissLayoutWarning,
        onToggleBookmark = viewModel::toggleCurrentPageBookmark,
        onDeleteBookmark = viewModel::deleteBookmark,
        onAddHighlight = viewModel::addHighlight,
        onDeleteHighlight = viewModel::deleteHighlight,
        onExportHighlightsMarkdown = viewModel::exportHighlightsAsMarkdown,
        onExportHighlightsPdf = viewModel::exportHighlightsAsPdf,
        onSearchQuery = viewModel::searchInDocument,
        onToggleTts = viewModel::startOrToggleTtsFromCurrentPage,
        onPrevTts = viewModel::prevTtsSentence,
        onNextTts = viewModel::nextTtsSentence,
        onStopTts = viewModel::stopTts,
        onTtsRateChange = viewModel::setTtsRate,
        onTtsSleepTimer = viewModel::setTtsSleepTimer,
        onUpdatePreset = { transform -> viewModel.updateReaderPreset(isPerBookPreset, transform) },
        onTogglePerBookPreset = viewModel::togglePerBookPresetMode,
        onSubmitPassword = viewModel::submitPdfPassword,
        onOpenWordLookup = viewModel::openWordLookup,
        onUpdateLookupQuery = viewModel::updateLookupQuery,
        onSelectTranslateLanguage = viewModel::selectTranslateLanguage,
        onDownloadTranslatePack = viewModel::downloadTranslatePack,
        onRunOfflineTranslate = viewModel::runOfflineTranslation,
        onImportDictionaryPack = { dictPackPicker.launch(arrayOf("text/plain", "application/json", "*/*")) },
        onDismissWordLookup = viewModel::dismissWordLookup
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    preset: ReaderBookPreset,
    isPerBookPreset: Boolean,
    ttsState: TtsPlaybackState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onPageChange: (Int) -> Unit,
    onSaveScrollFraction: (Float) -> Unit,
    onEnsurePageRendered: (Int) -> Unit,
    onToggleControls: () -> Unit,
    onToggleReflow: () -> Unit,
    onDismissLayoutWarning: () -> Unit,
    onToggleBookmark: () -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onAddHighlight: (String, String, String) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onExportHighlightsMarkdown: () -> Unit,
    onExportHighlightsPdf: () -> Unit,
    onSearchQuery: (String) -> Unit,
    onToggleTts: () -> Unit,
    onPrevTts: () -> Unit,
    onNextTts: () -> Unit,
    onStopTts: () -> Unit,
    onTtsRateChange: (Float) -> Unit,
    onTtsSleepTimer: (Int) -> Unit,
    onUpdatePreset: ((ReaderBookPreset) -> ReaderBookPreset) -> Unit,
    onTogglePerBookPreset: (Boolean) -> Unit,
    onSubmitPassword: (String) -> Unit,
    onOpenWordLookup: (String) -> Unit,
    onUpdateLookupQuery: (String) -> Unit,
    onSelectTranslateLanguage: (ReaderTranslateLanguage) -> Unit,
    onDownloadTranslatePack: () -> Unit,
    onRunOfflineTranslate: () -> Unit,
    onImportDictionaryPack: () -> Unit,
    onDismissWordLookup: () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val focusRequester = remember { FocusRequester() }

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showAnnotationsSheet by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showTtsSheet by remember { mutableStateOf(false) }
    var showStatsSheet by remember { mutableStateOf(false) }
    var showAddHighlightDialog by remember { mutableStateOf(false) }
    var prefilledHighlightQuote by remember { mutableStateOf<String?>(null) }

    // Request focus for volume-key page turning
    LaunchedEffect(preset.volumeKeysTurnPage) {
        if (preset.volumeKeysTurnPage) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    // Window brightness, Keep Screen On, Orientation Lock, and Immersive Mode (zero permissions needed)
    DisposableEffect(
        preset.brightness,
        preset.keepScreenOn,
        preset.orientationLock,
        preset.immersiveMode,
        state.isControlsVisible
    ) {
        val activity = context as? Activity
        val window = activity?.window
        if (window != null) {
            val lp = window.attributes
            lp.screenBrightness = if (preset.brightness < 0f) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                preset.brightness.coerceIn(0.05f, 1.0f)
            }
            window.attributes = lp

            if (preset.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (preset.immersiveMode && !state.isControlsVisible) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        if (activity != null) {
            activity.requestedOrientation = when (preset.orientationLock) {
                ReaderOrientationLock.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                ReaderOrientationLock.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                ReaderOrientationLock.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
        onDispose {
            val act = context as? Activity
            val w = act?.window
            if (w != null) {
                val resetLp = w.attributes
                resetLp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = resetLp
                w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(w, w.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val (surfaceBg, textInk) = resolveReaderColors(preset.theme, systemDark)
    val readerFontFamily = when (preset.fontChoice) {
        ReaderFontChoice.SANS -> PlusJakartaSansFontFamily
        ReaderFontChoice.SERIF -> LiterataFontFamily
        ReaderFontChoice.DYSLEXIC -> AtkinsonHyperlegibleFontFamily
        ReaderFontChoice.MONO -> JetBrainsMonoFontFamily
    }
    val textAlign = when (preset.textAlignment) {
        ReaderTextAlignment.START -> TextAlign.Start
        ReaderTextAlignment.JUSTIFY -> TextAlign.Justify
        ReaderTextAlignment.CENTER -> TextAlign.Center
    }
    val effectiveWarmAlpha = remember(
        preset.warmLight,
        preset.warmLightScheduleEnabled,
        preset.warmLightStartHour,
        preset.warmLightEndHour
    ) {
        val scheduledActive = preset.warmLightScheduleEnabled &&
            ReaderPdfAndExportHelper.isWarmLightScheduledAtHour(
                currentHour24 = LocalTime.now().hour,
                startHour = preset.warmLightStartHour,
                endHour = preset.warmLightEndHour
            )
        val base = preset.warmLight.coerceIn(0f, 0.45f)
        if (scheduledActive) maxOf(base, 0.18f) else base
    }

    val currentPageNumber = state.currentPage + 1
    val isCurrentBookmarked = state.bookmarks.any { it.pageNumber == currentPageNumber }
    val isPdfFixedMode = state.contentKind == ReaderContentKind.PDF && !state.reflowTextMode
    val isTtsActiveForDoc = ttsState.documentId == state.document?.id &&
        (ttsState.status == TtsStatus.SPEAKING || ttsState.status == TtsStatus.PAUSED)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceBg)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (!preset.volumeKeysTurnPage || keyEvent.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (keyEvent.key) {
                        Key.VolumeUp -> {
                            onPageChange(state.currentPage - 1)
                            true
                        }
                        Key.VolumeDown -> {
                            onPageChange(state.currentPage + 1)
                            true
                        }
                        else -> false
                    }
                }
            }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = surfaceBg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding()) },
            topBar = {
                AnimatedVisibility(visible = state.isControlsVisible) {
                    TopAppBar(
                        modifier = Modifier.statusBarsPadding(),
                        navigationIcon = {
                            IconButton(onClick = onBack, modifier = Modifier.testTag("reader_back_button")) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.navigate_back),
                                    tint = textInk
                                )
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
                                    text = "Page $currentPageNumber/${state.totalPages} • ${state.stats.percentComplete}% • ~${state.stats.estimatedMinutesLeft}m left",
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
                                        contentDescription = "Switch PDF / Reading view",
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
                                Icon(Icons.Default.Bookmarks, contentDescription = "Contents, bookmarks & highlights", tint = textInk)
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
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isTtsActiveForDoc) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showTtsSheet = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = ttsState.currentSentence?.text ?: "Read Aloud active",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                                    onClick = {
                                        val sampleWord = state.textSections.getOrNull(state.currentPage)?.body
                                            ?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(120).orEmpty()
                                        onOpenWordLookup(sampleWord)
                                    },
                                    modifier = Modifier.testTag("reader_lookup_button")
                                ) {
                                    Icon(Icons.Default.MenuBook, contentDescription = "Dictionary & Translate", tint = textInk)
                                }

                                IconButton(
                                    onClick = {
                                        prefilledHighlightQuote = null
                                        showAddHighlightDialog = true
                                    },
                                    modifier = Modifier.testTag("reader_highlight_button")
                                ) {
                                    Icon(Icons.Default.BorderColor, contentDescription = "Add highlight", tint = textInk)
                                }

                                IconButton(
                                    onClick = { showStatsSheet = true },
                                    modifier = Modifier.testTag("reader_stats_button")
                                ) {
                                    Icon(Icons.Default.BarChart, contentDescription = "Reading stats", tint = textInk)
                                }

                                IconButton(
                                    onClick = {
                                        if (isTtsActiveForDoc) showTtsSheet = true else {
                                            onToggleTts()
                                            showTtsSheet = true
                                        }
                                    },
                                    modifier = Modifier.testTag("reader_tts_button")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Read aloud controls",
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
                    state.isLoading -> CircularProgressIndicator()

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

                    isPdfFixedMode -> {
                        PdfFixedReaderSurface(
                            state = state,
                            preset = preset,
                            onPageChange = onPageChange,
                            onEnsurePageRendered = onEnsurePageRendered,
                            onToggleControls = onToggleControls
                        )
                    }

                    else -> {
                        ReflowReadingSurface(
                            state = state,
                            preset = preset,
                            ttsState = ttsState,
                            isTtsActiveForDoc = isTtsActiveForDoc,
                            readerFontFamily = readerFontFamily,
                            textAlign = textAlign,
                            textInk = textInk,
                            onPageChange = onPageChange,
                            onSaveScrollFraction = onSaveScrollFraction,
                            onToggleControls = onToggleControls,
                            onDismissLayoutWarning = onDismissLayoutWarning,
                            onDeleteHighlight = onDeleteHighlight,
                            onLongPressText = onOpenWordLookup
                        )
                    }
                }

                // Subtle reading progress bar along the very bottom when controls are hidden
                if (!state.isControlsVisible && !state.isLoading && state.errorMessage == null) {
                    LinearProgressIndicator(
                        progress = { (state.stats.percentComplete / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                        trackColor = Color.Transparent
                    )
                }
            }
        }

        // Warm light amber reading overlay (manual or scheduled night mode, non-intercepting)
        if (effectiveWarmAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFA726).copy(alpha = effectiveWarmAlpha))
            )
        }
    }

    if (showSettingsSheet) {
        ReaderControlsBottomSheet(
            preset = preset,
            isPdfFixedMode = isPdfFixedMode,
            isPerBookPresetActive = isPerBookPreset,
            onTogglePerBookPreset = onTogglePerBookPreset,
            onUpdatePreset = onUpdatePreset,
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showAnnotationsSheet) {
        ReaderContentsAndAnnotationsSheet(
            tocEntries = state.tocEntries,
            bookmarks = state.bookmarks,
            highlights = state.highlights,
            currentPageIndex = state.currentPage,
            onJumpToPage = onPageChange,
            onDeleteBookmark = onDeleteBookmark,
            onDeleteHighlight = onDeleteHighlight,
            onExportMarkdown = onExportHighlightsMarkdown,
            onExportPdf = onExportHighlightsPdf,
            onDismiss = { showAnnotationsSheet = false }
        )
    }

    if (showSearchSheet) {
        ReaderSearchSheet(
            query = state.searchQuery,
            hits = state.searchHits,
            totalMatches = state.totalSearchMatches,
            onQueryChange = onSearchQuery,
            onJumpToPage = onPageChange,
            onDismiss = { showSearchSheet = false }
        )
    }

    if (showTtsSheet) {
        ReaderTtsControlsSheet(
            ttsState = ttsState,
            onTogglePlayPause = onToggleTts,
            onPrev = onPrevTts,
            onNext = onNextTts,
            onStop = onStopTts,
            onRateChange = onTtsRateChange,
            onSleepTimerSelect = onTtsSleepTimer,
            onDismiss = { showTtsSheet = false }
        )
    }

    if (showStatsSheet) {
        ReadingStatsBottomSheet(
            title = state.document?.title ?: "Document",
            currentPageNumber = currentPageNumber,
            totalPages = state.totalPages,
            stats = state.stats,
            bookmarksCount = state.bookmarks.size,
            highlightsCount = state.highlights.size,
            onDismiss = { showStatsSheet = false }
        )
    }

    state.wordLookup?.let { lookup ->
        WordLookupBottomSheet(
            state = lookup,
            onQueryChange = onUpdateLookupQuery,
            onSelectLanguage = onSelectTranslateLanguage,
            onDownloadPack = onDownloadTranslatePack,
            onTranslate = onRunOfflineTranslate,
            onImportDictionaryPack = onImportDictionaryPack,
            onHighlightSnippet = { snippet ->
                onDismissWordLookup()
                prefilledHighlightQuote = snippet
                showAddHighlightDialog = true
            },
            onDismiss = onDismissWordLookup
        )
    }

    if (showAddHighlightDialog) {
        val defaultQuote = prefilledHighlightQuote ?: state.textSections
            .getOrNull(state.currentPage)?.body?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }?.take(120).orEmpty()
        AddHighlightDialog(
            pageNumber = currentPageNumber,
            initialQuote = defaultQuote,
            onConfirm = onAddHighlight,
            onDismiss = { showAddHighlightDialog = false }
        )
    }

    if (state.passwordRequiredFile != null) {
        ReaderPasswordDialog(
            title = state.document?.title ?: "PDF",
            error = state.passwordError,
            onSubmit = onSubmitPassword,
            onCancel = onBack
        )
    }
}

@Composable
private fun PdfFixedReaderSurface(
    state: ReaderUiState,
    preset: ReaderBookPreset,
    onPageChange: (Int) -> Unit,
    onEnsurePageRendered: (Int) -> Unit,
    onToggleControls: () -> Unit
) {
    val colorFilter = remember(preset.pdfColorFilter) { buildPdfColorFilter(preset.pdfColorFilter) }
    val wantsAutoCrop = preset.autoCropMargins || preset.pdfFitMode == PdfFitMode.AUTO_CROP
    val contentScale = if (preset.pdfFitMode == PdfFitMode.FIT_WIDTH) ContentScale.FillWidth else ContentScale.Fit

    if (preset.pageTurnMode == ReaderPageTurnMode.SCROLL) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentPage)
        val visiblePage by remember { derivedStateOf { listState.firstVisibleItemIndex } }
        LaunchedEffect(visiblePage) {
            if (visiblePage != state.currentPage && visiblePage in 0 until state.totalPages) {
                onPageChange(visiblePage)
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onToggleControls() })
                }
        ) {
            items(state.totalPages) { pageIdx ->
                LaunchedEffect(pageIdx) { onEnsurePageRendered(pageIdx) }
                val bmp = if (wantsAutoCrop) {
                    state.croppedPageBitmaps[pageIdx] ?: state.pageBitmaps[pageIdx]
                } else {
                    state.pageBitmaps[pageIdx]
                }
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (bmp != null && !bmp.isRecycled) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "PDF page ${pageIdx + 1}",
                            contentScale = contentScale,
                            colorFilter = colorFilter,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 860.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
        return
    }

    var zoom by remember(state.currentPage) { mutableFloatStateOf(1f) }
    var panX by remember(state.currentPage) { mutableFloatStateOf(0f) }
    var panY by remember(state.currentPage) { mutableFloatStateOf(0f) }
    var accumulatedDrag by remember(state.currentPage) { mutableFloatStateOf(0f) }

    AnimatedContent(
        targetState = state.currentPage,
        transitionSpec = {
            when (preset.pageTurnMode) {
                ReaderPageTurnMode.FADE -> fadeIn() togetherWith fadeOut()
                ReaderPageTurnMode.TAP_ONLY -> fadeIn() togetherWith fadeOut()
                else -> {
                    if (targetState > initialState) {
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                    }
                }
            }
        },
        label = "pdf_page_turn"
    ) { pageIdx ->
        val pageBmp = if (wantsAutoCrop) {
            state.croppedPageBitmaps[pageIdx] ?: state.pageBitmaps[pageIdx]
        } else {
            state.pageBitmaps[pageIdx]
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pageIdx, preset.tapZonesEnabled) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (preset.tapZonesEnabled && zoom <= 1.05f) {
                                val fracX = offset.x / size.width.coerceAtLeast(1)
                                when {
                                    fracX < 0.28f -> onPageChange(pageIdx - 1)
                                    fracX > 0.72f -> onPageChange(pageIdx + 1)
                                    else -> onToggleControls()
                                }
                            } else {
                                onToggleControls()
                            }
                        },
                        onDoubleTap = {
                            if (zoom > 1.2f) {
                                zoom = 1f; panX = 0f; panY = 0f
                            } else {
                                zoom = 2.0f
                            }
                        }
                    )
                }
                .pointerInput(pageIdx, preset.pageTurnMode) {
                    if (preset.pageTurnMode != ReaderPageTurnMode.TAP_ONLY) {
                        detectHorizontalDragGestures(
                            onDragStart = { accumulatedDrag = 0f },
                            onDragEnd = {
                                if (zoom <= 1.05f) {
                                    if (accumulatedDrag < -110f) onPageChange(pageIdx + 1)
                                    else if (accumulatedDrag > 110f) onPageChange(pageIdx - 1)
                                }
                                accumulatedDrag = 0f
                            }
                        ) { _, dragAmount ->
                            if (zoom <= 1.05f) accumulatedDrag += dragAmount
                        }
                    }
                }
                .pointerInput(pageIdx) {
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
                    contentDescription = "PDF page ${pageIdx + 1}",
                    contentScale = contentScale,
                    colorFilter = colorFilter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 860.dp)
                        .padding(if (preset.pdfFitMode == PdfFitMode.FIT_WIDTH) 0.dp else 10.dp)
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
}

@Composable
private fun ReflowReadingSurface(
    state: ReaderUiState,
    preset: ReaderBookPreset,
    ttsState: TtsPlaybackState,
    isTtsActiveForDoc: Boolean,
    readerFontFamily: androidx.compose.ui.text.font.FontFamily,
    textAlign: TextAlign,
    textInk: Color,
    onPageChange: (Int) -> Unit,
    onSaveScrollFraction: (Float) -> Unit,
    onToggleControls: () -> Unit,
    onDismissLayoutWarning: () -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onLongPressText: (String) -> Unit
) {
    val currentPageNumber = state.currentPage + 1
    val section = state.textSections.getOrNull(state.currentPage)
    val pageHighlights = state.highlights.filter { it.pageNumber == currentPageNumber }
    val scrollState = rememberScrollState()
    var accumulatedDrag by remember(state.currentPage) { mutableFloatStateOf(0f) }

    LaunchedEffect(state.currentPage, state.initialScrollFraction) {
        if (state.initialScrollFraction > 0f && scrollState.maxValue > 0) {
            scrollState.scrollTo((state.initialScrollFraction * scrollState.maxValue).roundToInt())
        } else {
            scrollState.scrollTo(0)
        }
    }

    LaunchedEffect(scrollState.value, scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            onSaveScrollFraction(scrollState.value.toFloat() / scrollState.maxValue.toFloat())
        }
    }

    val activeTtsText = if (isTtsActiveForDoc) ttsState.currentSentence?.text else null
    val annotatedBody = remember(section?.body, activeTtsText, state.searchQuery) {
        buildHighlightedReadingText(
            rawBody = section?.body.orEmpty().ifBlank {
                "No extractable text on this page. Switch back to PDF view for scanned pages."
            },
            ttsSentence = activeTtsText,
            searchQuery = state.searchQuery
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 780.dp)
            .verticalScroll(scrollState)
            .pointerInput(state.currentPage, preset.tapZonesEnabled) {
                detectTapGestures(
                    onTap = { offset ->
                        if (preset.tapZonesEnabled) {
                            val fracX = offset.x / size.width.coerceAtLeast(1)
                            when {
                                fracX < 0.24f -> onPageChange(state.currentPage - 1)
                                fracX > 0.76f -> onPageChange(state.currentPage + 1)
                                else -> onToggleControls()
                            }
                        } else {
                            onToggleControls()
                        }
                    },
                    onLongPress = {
                        val excerpt = section?.body?.lineSequence()
                            ?.firstOrNull { it.isNotBlank() }?.take(180).orEmpty()
                        onLongPressText(excerpt)
                    }
                )
            }
            .pointerInput(state.currentPage, preset.pageTurnMode) {
                if (preset.pageTurnMode != ReaderPageTurnMode.TAP_ONLY && preset.pageTurnMode != ReaderPageTurnMode.SCROLL) {
                    detectHorizontalDragGestures(
                        onDragStart = { accumulatedDrag = 0f },
                        onDragEnd = {
                            if (accumulatedDrag < -110f) onPageChange(state.currentPage + 1)
                            else if (accumulatedDrag > 110f) onPageChange(state.currentPage - 1)
                            accumulatedDrag = 0f
                        }
                    ) { _, dragAmount ->
                        accumulatedDrag += dragAmount
                    }
                }
            }
            .padding(horizontal = preset.horizontalMarginDp.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (state.contentKind == ReaderContentKind.PDF && !state.layoutWarningDismissed) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth().testTag("reader_pdf_reflow_warning")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Reading view reflows extracted PDF text — columns, tables, or scanned images may differ from the original layout.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismissLayoutWarning) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss warning",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        Text(
            text = section?.title ?: "Page $currentPageNumber",
            fontFamily = readerFontFamily,
            fontSize = (21f * preset.fontScale).sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = textAlign,
            color = textInk,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = annotatedBody,
            fontFamily = readerFontFamily,
            fontSize = (16f * preset.fontScale).sp,
            lineHeight = (16f * preset.fontScale * preset.lineSpacingMultiplier).sp,
            fontWeight = if (preset.boldText) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = textAlign,
            color = textInk,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Tip: Long-press text to open Offline Dictionary & Translate.",
            style = MaterialTheme.typography.labelSmall,
            color = textInk.copy(alpha = 0.52f)
        )

        if (pageHighlights.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Highlights on this page",
                style = MaterialTheme.typography.labelLarge,
                color = textInk.copy(alpha = 0.78f)
            )
            pageHighlights.forEach { hl ->
                HighlightBannerItem(highlight = hl, onDelete = { onDeleteHighlight(hl.id) })
            }
        }
    }
}

private fun resolveReaderColors(theme: ReaderThemeMode, systemDark: Boolean): Pair<Color, Color> =
    when (theme) {
        ReaderThemeMode.PAPER -> Color(0xFFFDFCF9) to Color(0xFF1F2937)
        ReaderThemeMode.SEPIA -> Color(0xFFF5ECD8) to Color(0xFF3E2E1E)
        ReaderThemeMode.GRAY -> Color(0xFFE5E7EB) to Color(0xFF1F2937)
        ReaderThemeMode.SLATE_DARK -> Color(0xFF161B22) to Color(0xFFE6EDF3)
        ReaderThemeMode.OLED_BLACK -> Color(0xFF000000) to Color(0xFFE5E7EB)
        ReaderThemeMode.FOLLOW_SYSTEM -> if (systemDark) {
            Color(0xFF161B22) to Color(0xFFE6EDF3)
        } else {
            Color(0xFFFDFCF9) to Color(0xFF1F2937)
        }
    }

private fun buildPdfColorFilter(mode: PdfColorFilterMode): ColorFilter? = when (mode) {
    PdfColorFilterMode.LIGHT -> null
    PdfColorFilterMode.SEPIA -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.96f, 0f, 0f, 0f, 6f,
                0f, 0.91f, 0f, 0f, 2f,
                0f, 0f, 0.78f, 0f, -4f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    PdfColorFilterMode.DARK -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -0.86f, 0f, 0f, 0f, 232f,
                0f, -0.86f, 0f, 0f, 234f,
                0f, 0f, -0.86f, 0f, 238f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    PdfColorFilterMode.INVERT -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}

internal fun buildHighlightedReadingText(
    rawBody: String,
    ttsSentence: String?,
    searchQuery: String
): AnnotatedString = buildAnnotatedString {
    append(rawBody)

    val cleanQuery = searchQuery.trim()
    if (cleanQuery.length >= 2) {
        var idx = rawBody.indexOf(cleanQuery, ignoreCase = true)
        while (idx >= 0) {
            addStyle(
                style = SpanStyle(
                    background = Color(0xFFFDE047).copy(alpha = 0.55f),
                    color = Color(0xFF111827),
                    fontWeight = FontWeight.SemiBold
                ),
                start = idx,
                end = (idx + cleanQuery.length).coerceAtMost(rawBody.length)
            )
            idx = rawBody.indexOf(cleanQuery, startIndex = idx + cleanQuery.length, ignoreCase = true)
        }
    }

    val cleanTts = ttsSentence?.trim().orEmpty()
    if (cleanTts.length >= 4) {
        val ttsIdx = rawBody.indexOf(cleanTts, ignoreCase = true)
        if (ttsIdx >= 0) {
            addStyle(
                style = SpanStyle(
                    background = Color(0xFF38BDF8).copy(alpha = 0.32f),
                    fontWeight = FontWeight.Medium
                ),
                start = ttsIdx,
                end = (ttsIdx + cleanTts.length).coerceAtMost(rawBody.length)
            )
        }
    }
}
