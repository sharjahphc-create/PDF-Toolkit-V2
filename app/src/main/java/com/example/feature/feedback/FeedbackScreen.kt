package com.example.feature.feedback

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.R
import com.example.core.database.dao.FeedbackDraftDao
import com.example.core.database.entity.FeedbackDraftEntity
import com.example.core.datastore.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject

data class FeedbackUiState(
    val feedbackType: String = "Bug",
    val title: String = "",
    val body: String = "",
    val includeDiagnostics: Boolean = false,
    val draftSavedAt: Long? = null
)

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val feedbackDraftDao: FeedbackDraftDao,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val existing = feedbackDraftDao.observeDrafts().first().firstOrNull()
            if (existing != null) {
                _uiState.value = FeedbackUiState(
                    feedbackType = existing.feedbackType,
                    title = existing.title,
                    body = existing.body,
                    includeDiagnostics = existing.includeDiagnostics,
                    draftSavedAt = existing.updatedAt
                )
            }
        }
    }

    fun setType(type: String) = _uiState.update { it.copy(feedbackType = type) }
    fun setTitle(title: String) = _uiState.update { it.copy(title = title) }
    fun setBody(body: String) = _uiState.update { it.copy(body = body) }
    fun setIncludeDiagnostics(include: Boolean) = _uiState.update { it.copy(includeDiagnostics = include) }

    fun saveDraft() {
        val state = _uiState.value
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val deviceId = preferencesRepository.getOrCreateDeviceId()
            feedbackDraftDao.upsertDraft(
                FeedbackDraftEntity(
                    id = "active_feedback_draft",
                    feedbackType = state.feedbackType,
                    title = state.title,
                    body = state.body,
                    includeDiagnostics = state.includeDiagnostics,
                    updatedAt = now,
                    deviceId = deviceId,
                    isDeleted = false
                )
            )
            _uiState.update { it.copy(draftSavedAt = now) }
            _events.emit("Feedback draft saved offline")
        }
    }

    fun buildSanitizedDiagnostics(): String {
        return "App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
            "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "Locale: ${Locale.getDefault().toLanguageTag()}\n" +
            "Network Permission: None (100% Offline)"
    }

    fun buildIssueUrl(): String {
        val state = _uiState.value
        val fullBody = buildString {
            append("Type: ${state.feedbackType}\n\n")
            append(state.body.trim())
            if (state.includeDiagnostics) {
                append("\n\n---\nSanitized Diagnostics:\n")
                append(buildSanitizedDiagnostics())
            }
        }
        val encTitle = URLEncoder.encode("[${state.feedbackType}] ${state.title.trim()}", "UTF-8")
        val encBody = URLEncoder.encode(fullBody, "UTF-8")
        val baseUrl = "https://github.com/offline-pdf-toolkit/app/issues/new?title=$encTitle&body=$encBody"
        return baseUrl.take(5900)
    }

    fun notifyMessage(msg: String) {
        viewModelScope.launch { _events.emit(msg) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    viewModel: FeedbackViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var ratingStars by remember { mutableIntStateOf(5) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("feedback_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                title = { Text("Feedback & Support", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Compose Offline Draft", style = MaterialTheme.typography.titleMedium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Bug", "Feature", "Suggestion").forEach { kind ->
                                    FilterChip(
                                        selected = state.feedbackType == kind,
                                        onClick = { viewModel.setType(kind) },
                                        label = { Text(kind) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = state.title,
                                onValueChange = viewModel::setTitle,
                                label = { Text("Summary") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("feedback_title_input")
                            )
                            OutlinedTextField(
                                value = state.body,
                                onValueChange = viewModel::setBody,
                                label = { Text("Details") },
                                minLines = 4,
                                modifier = Modifier.fillMaxWidth().testTag("feedback_body_input")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Attach sanitized device info (no files/PII)", style = MaterialTheme.typography.bodySmall)
                                Switch(
                                    checked = state.includeDiagnostics,
                                    onCheckedChange = viewModel::setIncludeDiagnostics
                                )
                            }
                            if (state.includeDiagnostics) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        viewModel.buildSanitizedDiagnostics(),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = viewModel::saveDraft,
                                    modifier = Modifier.weight(1f).testTag("feedback_save_draft_button")
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save Draft")
                                }
                                Button(
                                    onClick = {
                                        viewModel.saveDraft()
                                        runCatching {
                                            val url = viewModel.buildIssueUrl()
                                            CustomTabsIntent.Builder().build()
                                                .launchUrl(context, Uri.parse(url))
                                        }.onFailure {
                                            viewModel.notifyMessage("Saved offline draft. No browser found to open issue tracker.")
                                        }
                                    },
                                    enabled = state.title.isNotBlank() || state.body.isNotBlank(),
                                    modifier = Modifier.weight(1f).testTag("feedback_open_browser_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open Tracker")
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Rate PDF Toolkit", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Enjoying fast, 100% offline PDF tools without trackers? Leave feedback anytime.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                (1..5).forEach { star ->
                                    IconButton(onClick = {
                                        ratingStars = star
                                        viewModel.notifyMessage("Thank you for rating $star/5!")
                                    }) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = "Rate $star stars",
                                            tint = if (star <= ratingStars) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant
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
