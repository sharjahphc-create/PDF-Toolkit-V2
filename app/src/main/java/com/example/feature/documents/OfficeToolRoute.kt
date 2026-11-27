package com.example.feature.documents

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ui.components.EmptyStateView
import com.example.core.ui.components.MoveToCategoryBottomSheet
import com.example.core.ui.components.ProcessingProgressView
import com.example.core.ui.components.ToolDoneCard
import com.example.core.ui.components.ToolStep
import com.example.core.ui.components.ToolWorkflowScaffold
import com.example.feature.tools.ToolCatalog

@Composable
fun OfficeToolRoute(
    toolId: String,
    onExit: () -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: OfficeToolsViewModel = hiltViewModel()
) {
    LaunchedEffect(toolId) {
        viewModel.initTool(toolId)
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showCategorySheet by remember { mutableStateOf(false) }

    val toolDef = remember(toolId) { ToolCatalog.findById(toolId) }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.openDocumentUri(it) }
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(state.outputMimeType)
    ) { uri ->
        uri?.let { viewModel.exportOutputToSaf(it) }
    }

    BackHandler(enabled = state.step != ToolStep.PICK) {
        viewModel.stepBackOrExit(onExit)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is OfficeToolEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is OfficeToolEvent.ShareFile -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        putExtra(Intent.EXTRA_SUBJECT, event.title)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, event.title))
                }
            }
        }
    }

    val supportedMimes = when (toolId) {
        "word" -> arrayOf(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/octet-stream"
        )
        "excel" -> arrayOf(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/octet-stream"
        )
        else -> arrayOf(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/octet-stream"
        )
    }

    val primaryText = when (state.step) {
        ToolStep.PICK -> when (toolId) {
            "word" -> "Open .docx File"
            "excel" -> "Open .xlsx File"
            else -> "Open .pptx File"
        }
        ToolStep.CONFIGURE -> when {
            toolId == "powerpoint" || state.exportFormat == OfficeExportFormat.PDF -> "Export as PDF"
            toolId == "word" -> "Save .docx"
            else -> "Save .xlsx"
        }
        ToolStep.PROCESS -> null
        ToolStep.DONE -> "Done"
    }

    ToolWorkflowScaffold(
        title = toolDef?.title ?: "Document",
        subtitle = state.displayName.ifBlank { toolDef?.subtitle },
        currentStep = state.step,
        onBack = { viewModel.stepBackOrExit(onExit) },
        primaryButtonText = primaryText,
        primaryButtonEnabled = true,
        onPrimaryClick = {
            when (state.step) {
                ToolStep.PICK -> openDocLauncher.launch(supportedMimes)
                ToolStep.CONFIGURE -> viewModel.processAndExport()
                ToolStep.PROCESS -> Unit
                ToolStep.DONE -> onExit()
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) {
        when (state.step) {
            ToolStep.PICK -> {
                val icon = when (toolId) {
                    "word" -> Icons.AutoMirrored.Filled.LibraryBooks
                    "excel" -> Icons.Default.GridOn
                    else -> Icons.Default.Slideshow
                }
                val title = when (toolId) {
                    "word" -> "Open or create a Word document"
                    "excel" -> "Open or create an Excel workbook"
                    else -> "Open PowerPoint and export PDF"
                }
                val subtitle = when (toolId) {
                    "word" -> "Edit .docx paragraphs with headings & styles, then save .docx or export PDF."
                    "excel" -> "Inspect sheets, edit cells or formulas, and save .xlsx or export PDF."
                    else -> "Preview slides & speaker notes offline via Apache POI and convert cleanly to PDF."
                }
                val secondaryLabel = when (toolId) {
                    "word" -> "New Word Doc"
                    "excel" -> "New Spreadsheet"
                    else -> "Load Sample Deck"
                }
                EmptyStateView(
                    icon = icon,
                    title = title,
                    subtitle = subtitle,
                    primaryActionLabel = "Browse Device",
                    onPrimaryAction = { openDocLauncher.launch(supportedMimes) },
                    secondaryActionLabel = secondaryLabel,
                    onSecondaryAction = viewModel::createNewDocument
                )
            }

            ToolStep.CONFIGURE -> {
                when (toolId) {
                    "word" -> WordEditorPanel(
                        state = state,
                        onExportFormatChange = viewModel::setExportFormat,
                        onParagraphTextChange = viewModel::updateParagraphText,
                        onToggleHeading = viewModel::toggleParagraphHeading,
                        onToggleBold = viewModel::toggleParagraphBold,
                        onToggleItalic = viewModel::toggleParagraphItalic,
                        onAddParagraph = viewModel::addDocxParagraph,
                        onRemoveParagraph = viewModel::removeDocxParagraph
                    )
                    "excel" -> ExcelEditorPanel(
                        state = state,
                        onExportFormatChange = viewModel::setExportFormat,
                        onSelectSheet = viewModel::selectSheet,
                        onSelectCell = viewModel::selectCell,
                        onUpdateCellValue = viewModel::updateSelectedCellValue,
                        onAddRow = viewModel::addXlsxRow,
                        onAddColumn = viewModel::addXlsxColumn
                    )
                    "powerpoint" -> PowerPointConvertPanel(
                        state = state,
                        onSelectSlide = viewModel::selectSlide,
                        onToggleNotes = viewModel::setIncludeSpeakerNotes
                    )
                }
            }

            ToolStep.PROCESS -> {
                ProcessingProgressView(
                    progress = state.progress,
                    onCancel = viewModel::cancelProcess
                )
            }

            ToolStep.DONE -> {
                val outName = state.outputFile?.name ?: "Document"
                ToolDoneCard(
                    title = "File ready",
                    summaryText = "$outName generated offline on your device.",
                    metrics = state.summaryMetrics,
                    onOpen = onOpenLibrary,
                    onShare = viewModel::shareOutput,
                    onSaveAs = { saveAsLauncher.launch(outName) },
                    onSaveToCategory = { showCategorySheet = true }
                )
            }
        }
    }

    if (showCategorySheet) {
        MoveToCategoryBottomSheet(
            categories = categories,
            currentCategoryId = null,
            onSelectCategory = { catId -> viewModel.assignOutputToCategory(catId) },
            onDismiss = { showCategorySheet = false }
        )
    }
}
