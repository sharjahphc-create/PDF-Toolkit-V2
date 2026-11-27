package com.example.feature.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.common.OperationProgress
import com.example.core.ui.components.EmptyStateView
import com.example.core.ui.components.ProcessingProgressView
import com.example.core.ui.components.RunsOnDeviceBadge
import com.example.core.ui.components.ToolDoneCard
import com.example.core.ui.components.ToolStep
import com.example.core.ui.components.ToolWorkflowScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ToolWorkspaceScreen(
    toolId: String,
    onBack: () -> Unit,
    onOpenLibrary: () -> Unit
) {
    val tool = remember(toolId) { ToolCatalog.findById(toolId) }
    var currentStep by remember { mutableStateOf(ToolStep.PICK) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pickedUri = uri
            currentStep = ToolStep.CONFIGURE
        }
    }

    val primaryButtonText = when (currentStep) {
        ToolStep.PICK -> "Select File via SAF"
        ToolStep.CONFIGURE -> "Run ${tool?.title ?: "Tool"}"
        ToolStep.PROCESS -> null
        ToolStep.DONE -> "Done"
    }

    ToolWorkflowScaffold(
        title = tool?.title ?: "Tool",
        subtitle = tool?.subtitle,
        currentStep = currentStep,
        onBack = onBack,
        primaryButtonText = primaryButtonText,
        onPrimaryClick = {
            when (currentStep) {
                ToolStep.PICK -> picker.launch(arrayOf("application/pdf", "image/*", "*/*"))
                ToolStep.CONFIGURE -> {
                    currentStep = ToolStep.PROCESS
                    scope.launch {
                        delay(700L)
                        currentStep = ToolStep.DONE
                    }
                }
                ToolStep.PROCESS -> Unit
                ToolStep.DONE -> onBack()
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) {
        when (currentStep) {
            ToolStep.PICK -> {
                EmptyStateView(
                    icon = tool?.icon ?: Icons.Default.UploadFile,
                    title = "Choose a document",
                    subtitle = "Uses Android Storage Access Framework—no broad storage permissions required.",
                    primaryActionLabel = "Browse Files",
                    onPrimaryAction = { picker.launch(arrayOf("application/pdf", "image/*")) },
                    secondaryActionLabel = "Open Library",
                    onSecondaryAction = onOpenLibrary
                )
            }
            ToolStep.CONFIGURE -> {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RunsOnDeviceBadge()
                        Text(
                            text = "Selected source",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = pickedUri?.lastPathSegment ?: "Document ready",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(onClick = { currentStep = ToolStep.PICK }) {
                            Text("Choose another file")
                        }
                    }
                }
            }
            ToolStep.PROCESS -> {
                ProcessingProgressView(
                    progress = OperationProgress(
                        progress = 0.65f,
                        statusText = "Running ${tool?.title ?: "task"} on your device…",
                        isIndeterminate = true
                    ),
                    onCancel = { currentStep = ToolStep.CONFIGURE }
                )
            }
            ToolStep.DONE -> {
                ToolDoneCard(
                    title = "${tool?.title ?: "Task"} complete",
                    summaryText = "Original document preserved untouched.",
                    onOpen = onOpenLibrary,
                    onShare = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Prepared output for FileProvider sharing")
                        }
                    },
                    onSaveAs = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Ready to export via Storage Access Framework")
                        }
                    },
                    onSaveToCategory = onOpenLibrary
                )
            }
        }
    }
}
