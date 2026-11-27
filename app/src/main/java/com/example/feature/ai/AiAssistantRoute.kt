package com.example.feature.ai

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.common.OperationProgress
import com.example.core.ui.components.EmptyStateView
import com.example.core.ui.components.ProcessingProgressView
import com.example.core.ui.components.RunsOnDeviceBadge
import com.example.core.ui.components.ToolStep
import com.example.core.ui.components.ToolWorkflowScaffold
import com.example.data.repository.LibraryRepository
import com.example.domain.model.LibraryDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiAssistantUiState(
    val step: ToolStep = ToolStep.PICK,
    val selectedDoc: LibraryDocument? = null,
    val rawText: String = "",
    val insights: DocumentAiInsights? = null,
    val questionInput: String = "",
    val qaHistory: List<DocumentQaAnswer> = emptyList(),
    val progress: OperationProgress = OperationProgress()
)

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val aiEngine: OnDeviceAiEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    val libraryDocuments: StateFlow<List<LibraryDocument>> = libraryRepository
        .observeDocuments(includeLocked = false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var activeJob: Job? = null

    fun importAndAnalyzeUri(uri: android.net.Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    step = ToolStep.PROCESS,
                    progress = OperationProgress(0.2f, "Importing & extracting text offline…", isIndeterminate = true)
                )
            }
            libraryRepository.importFromUri(uri)
                .onSuccess { doc -> analyzeDocument(doc) }
                .onFailure { err ->
                    _uiState.update { it.copy(step = ToolStep.PICK) }
                    _events.emit(err.localizedMessage ?: "Failed to import file")
                }
        }
    }

    fun analyzeSamplePdf() {
        viewModelScope.launch {
            libraryRepository.importBundledSamplePdf()
                .onSuccess { doc -> analyzeDocument(doc) }
                .onFailure { err -> _events.emit(err.localizedMessage ?: "Failed to load sample") }
        }
    }

    fun analyzeDocument(doc: LibraryDocument) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    step = ToolStep.PROCESS,
                    selectedDoc = doc,
                    progress = OperationProgress(0.45f, "Running on-device AI analysis…", isIndeterminate = true)
                )
            }
            val text = libraryRepository.extractSpeakableText(doc.id).ifBlank { doc.ocrText }
            val insights = aiEngine.analyzeDocument(text, doc.title)
            _uiState.update {
                it.copy(
                    step = ToolStep.CONFIGURE,
                    selectedDoc = doc,
                    rawText = text,
                    insights = insights,
                    qaHistory = emptyList()
                )
            }
        }
    }

    fun updateQuestionInput(q: String) {
        _uiState.update { it.copy(questionInput = q) }
    }

    fun askQuestion() {
        val state = _uiState.value
        val q = state.questionInput.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            val answer = aiEngine.answerQuestion(state.rawText, q)
            _uiState.update {
                it.copy(
                    questionInput = "",
                    qaHistory = listOf(answer) + it.qaHistory
                )
            }
        }
    }

    fun applySmartFilename(suggestedTitle: String) {
        val doc = _uiState.value.selectedDoc ?: return
        viewModelScope.launch {
            libraryRepository.updateDocumentTitle(doc.id, suggestedTitle)
            _uiState.update {
                it.copy(selectedDoc = doc.copy(title = suggestedTitle))
            }
            _events.emit("Renamed in Library to \"$suggestedTitle\"")
        }
    }

    fun cancelProcess() {
        activeJob?.cancel()
        _uiState.update { it.copy(step = ToolStep.PICK) }
    }

    fun stepBackOrExit(onExit: () -> Unit) {
        if (_uiState.value.step != ToolStep.PICK) {
            _uiState.update { AiAssistantUiState() }
        } else {
            onExit()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantRoute(
    onExit: () -> Unit,
    onOpenReader: (String) -> Unit,
    viewModel: AiAssistantViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val libraryDocs by viewModel.libraryDocuments.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importAndAnalyzeUri(it) }
    }

    BackHandler(enabled = state.step != ToolStep.PICK) {
        viewModel.stepBackOrExit(onExit)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    ToolWorkflowScaffold(
        title = "Smart AI Reader",
        subtitle = state.selectedDoc?.title ?: "Summarize, extract entities & Q&A offline",
        currentStep = state.step,
        onBack = { viewModel.stepBackOrExit(onExit) },
        primaryButtonText = when (state.step) {
            ToolStep.PICK -> "Browse Document"
            ToolStep.CONFIGURE -> if (state.selectedDoc != null) "Open in Reader" else null
            ToolStep.PROCESS -> null
            ToolStep.DONE -> "Done"
        },
        onPrimaryClick = {
            when (state.step) {
                ToolStep.PICK -> openDocLauncher.launch(arrayOf("application/pdf", "text/plain", "application/epub+zip"))
                ToolStep.CONFIGURE -> state.selectedDoc?.let { onOpenReader(it.id) }
                else -> onExit()
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) {
        when (state.step) {
            ToolStep.PICK -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        EmptyStateView(
                            icon = Icons.Default.AutoAwesome,
                            title = "On-Device AI Assistant",
                            subtitle = "Generate summaries, action items, entity extraction, grounded page-cited Q&A, and smart filenames 100% locally.",
                            primaryActionLabel = "Browse Document",
                            onPrimaryAction = {
                                openDocLauncher.launch(arrayOf("application/pdf", "text/plain", "application/epub+zip"))
                            },
                            secondaryActionLabel = stringResource(R.string.import_sample),
                            onSecondaryAction = viewModel::analyzeSamplePdf
                        )
                    }
                    if (libraryDocs.isNotEmpty()) {
                        item {
                            Text("Analyze from your Library", style = MaterialTheme.typography.titleSmall)
                        }
                        items(libraryDocs, key = { it.id }) { doc ->
                            Card(
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable { viewModel.analyzeDocument(doc) }
                                    .testTag("ai_pick_doc_${doc.id}")
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(doc.title, style = MaterialTheme.typography.titleSmall)
                                        Text("${doc.pageCount} pages", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ToolStep.PROCESS -> {
                ProcessingProgressView(
                    progress = state.progress,
                    onCancel = viewModel::cancelProcess
                )
            }

            ToolStep.CONFIGURE, ToolStep.DONE -> {
                val insights = state.insights
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { RunsOnDeviceBadge() }
                    if (insights != null) {
                        item {
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Executive Summary", style = MaterialTheme.typography.titleMedium)
                                    Text(insights.summary, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        if (insights.keyPoints.isNotEmpty()) {
                            item {
                                Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Key Points", style = MaterialTheme.typography.titleMedium)
                                        insights.keyPoints.forEach { pt ->
                                            Text("• $pt", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }

                        if (insights.actionItems.isNotEmpty()) {
                            item {
                                Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Action Items", style = MaterialTheme.typography.titleMedium)
                                        insights.actionItems.forEach { act ->
                                            Row(verticalAlignment = Alignment.Top) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(act, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (insights.entities.isNotEmpty()) {
                            item {
                                Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Extracted Dates, Contacts & Amounts", style = MaterialTheme.typography.titleMedium)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            insights.entities.forEach { badge ->
                                                Surface(
                                                    shape = MaterialTheme.shapes.small,
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        "${badge.typeLabel}: ${badge.value}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (insights.suggestedFilenames.isNotEmpty()) {
                            item {
                                Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Smart Filename Suggestions (Tap to Rename)", style = MaterialTheme.typography.titleMedium)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            insights.suggestedFilenames.forEach { name ->
                                                AssistChip(
                                                    onClick = { viewModel.applySmartFilename(name) },
                                                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                                                    label = { Text(name) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Ask This Document (${insights.chunkCount} offline chunks)", style = MaterialTheme.typography.titleMedium)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = state.questionInput,
                                            onValueChange = viewModel::updateQuestionInput,
                                            placeholder = { Text("Ask a question…") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).testTag("ai_qa_input")
                                        )
                                        IconButton(
                                            onClick = viewModel::askQuestion,
                                            modifier = Modifier.testTag("ai_qa_send_button")
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask question")
                                        }
                                    }
                                    state.qaHistory.forEach { qa ->
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text("Q: ${qa.question}", style = MaterialTheme.typography.labelLarge)
                                                Text(qa.answer, style = MaterialTheme.typography.bodySmall)
                                                if (qa.citedPages.isNotEmpty()) {
                                                    Text(
                                                        "Sources: Page ${qa.citedPages.joinToString(", ")}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
