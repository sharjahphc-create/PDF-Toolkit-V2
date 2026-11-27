package com.example.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.common.OperationProgress

enum class ToolStep(val index: Int, val labelRes: Int) {
    PICK(0, R.string.step_pick),
    CONFIGURE(1, R.string.step_configure),
    PROCESS(2, R.string.step_process),
    DONE(3, R.string.step_done)
}

@Composable
fun ToolStepper(
    currentStep: ToolStep,
    modifier: Modifier = Modifier,
    steps: List<ToolStep> = ToolStep.entries
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        steps.forEachIndexed { idx, step ->
            val isCompleted = step.index < currentStep.index
            val isActive = step == currentStep
            val label = stringResource(step.labelRes)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Step ${idx + 1} of ${steps.size}: $label" +
                        when {
                            isCompleted -> ", completed"
                            isActive -> ", current"
                            else -> ""
                        }
                }
            ) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        isActive -> MaterialTheme.colorScheme.primary
                        isCompleted -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(15.dp)
                            )
                        } else {
                            Text(
                                text = "${idx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = if (isActive) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1
                )
            }
            if (idx < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                        .height(2.dp)
                        .background(
                            if (isCompleted) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolWorkflowScaffold(
    title: String,
    subtitle: String? = null,
    currentStep: ToolStep,
    onBack: () -> Unit,
    primaryButtonText: String?,
    primaryButtonEnabled: Boolean = true,
    onPrimaryClick: (() -> Unit)? = null,
    secondaryActionText: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("tool_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (primaryButtonText != null && onPrimaryClick != null) {
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (secondaryActionText != null && onSecondaryClick != null) {
                                TextButton(
                                    onClick = onSecondaryClick,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("tool_secondary_action")
                                ) {
                                    Text(secondaryActionText)
                                }
                            }
                            Button(
                                onClick = onPrimaryClick,
                                enabled = primaryButtonEnabled,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                                    .testTag("tool_primary_button")
                            ) {
                                Text(
                                    text = primaryButtonText,
                                    style = MaterialTheme.typography.labelLarge
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
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
            ) {
                ToolStepper(currentStep = currentStep)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "toolStepContent",
                    modifier = Modifier.weight(1f)
                ) { _ ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        content = content
                    )
                }
            }
        }
    }
}

@Composable
fun ProcessingProgressView(
    progress: OperationProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .testTag("processing_progress_card")
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RunsOnDeviceBadge()
            Spacer(Modifier.height(16.dp))
            Text(
                text = progress.statusText.ifBlank { stringResource(R.string.processing_on_device) },
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(12.dp))
            if (progress.isIndeterminate) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${(progress.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (progress.isCancellable) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("cancel_processing_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
fun ToolDoneCard(
    title: String,
    summaryText: String,
    metrics: List<Pair<String, String>> = emptyList(),
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSaveAs: () -> Unit,
    onSaveToCategory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .testTag("tool_done_card")
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (metrics.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    metrics.forEach { (label, value) ->
                        MetricPill(
                            label = label,
                            value = value,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("done_open_button")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_open))
                }
                FilledTonalButton(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("done_share_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.share_document))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onSaveAs,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("done_save_as_button")
                ) {
                    Icon(Icons.Default.SaveAs, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_save_as))
                }
                OutlinedButton(
                    onClick = onSaveToCategory,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("done_save_category_button")
                ) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_save_to_category))
                }
            }
        }
    }
}
