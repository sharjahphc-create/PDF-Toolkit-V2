package com.example.feature.designsystem

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.common.OperationProgress
import com.example.core.datastore.PreferencesRepository
import com.example.core.datastore.ThemeMode
import com.example.core.datastore.ToolsViewMode
import com.example.core.datastore.UserPreferences
import com.example.core.ui.components.ConfirmActionBottomSheet
import com.example.core.ui.components.EmptyStateView
import com.example.core.ui.components.MoveToCategoryBottomSheet
import com.example.core.ui.components.ProcessingProgressView
import com.example.core.ui.components.RunsOnDeviceBadge
import com.example.core.ui.components.SkeletonDocumentRow
import com.example.core.ui.components.ToolCard
import com.example.core.ui.components.ToolDoneCard
import com.example.core.ui.components.ToolStep
import com.example.core.ui.components.ToolStepper
import com.example.domain.model.DocumentCategory
import com.example.ui.theme.ToolAccentCategory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignSystemGalleryScreen(
    prefs: UserPreferences,
    preferencesRepository: PreferencesRepository
) {
    val scope = rememberCoroutineScope()
    var previewViewMode by remember { mutableStateOf(ToolsViewMode.GRID_2) }
    var activeStep by remember { mutableStateOf(ToolStep.CONFIGURE) }
    var showConfirmSheet by remember { mutableStateOf(false) }
    var showMoveCategorySheet by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf<String?>("cat_work") }
    var showSkeletons by remember { mutableStateOf(false) }

    val sampleCategories = remember {
        listOf(
            DocumentCategory("cat_work", "Work", "work", 0xFF2563EB, false, true, 0),
            DocumentCategory("cat_personal", "Personal", "person", 0xFF0D9488, false, true, 1),
            DocumentCategory("cat_bills", "Bills", "receipt", 0xFFD97706, false, true, 2),
            DocumentCategory("cat_ids", "IDs", "badge", 0xFF7C3AED, false, true, 3)
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.design_system_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = stringResource(R.string.design_system_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Theme & Accessibility", style = MaterialTheme.typography.titleMedium)
                            RunsOnDeviceBadge()
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = prefs.themeMode == mode,
                                    onClick = { scope.launch { preferencesRepository.setThemeMode(mode) } },
                                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }
                        ToggleRow("Dynamic Color (Android 12+)", prefs.dynamicColor) {
                            scope.launch { preferencesRepository.setDynamicColor(it) }
                        }
                        ToggleRow("High Contrast", prefs.highContrast) {
                            scope.launch { preferencesRepository.setHighContrast(it) }
                        }
                        ToggleRow("Reduce Motion", prefs.reduceMotion) {
                            scope.launch { preferencesRepository.setReduceMotion(it) }
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    Text("Tool Card View Modes", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = previewViewMode == ToolsViewMode.GRID_2,
                            onClick = { previewViewMode = ToolsViewMode.GRID_2 },
                            label = { Text("2-Col Grid") }
                        )
                        FilterChip(
                            selected = previewViewMode == ToolsViewMode.LIST,
                            onClick = { previewViewMode = ToolsViewMode.LIST },
                            label = { Text("List") }
                        )
                        FilterChip(
                            selected = previewViewMode == ToolsViewMode.COMPACT_3,
                            onClick = { previewViewMode = ToolsViewMode.COMPACT_3 },
                            label = { Text("3-Col Compact") }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val sampleTools = listOf(
                        Triple("sign", "Sign" to "Draw your signature, place it", Icons.Default.Draw to ToolAccentCategory.SIGN_SECURITY),
                        Triple("compress", "Compress" to "Small enough to upload", Icons.Default.Compress to ToolAccentCategory.PDF_CORE),
                        Triple("scan", "Smart Scan" to "Auto-crop & enhance papers", Icons.Default.DocumentScanner to ToolAccentCategory.SCAN_OCR),
                        Triple("translate", "Translate" to "Offline language packs", Icons.Default.Translate to ToolAccentCategory.AI_DEVICE),
                        Triple("merge", "Merge" to "Many PDFs into one", Icons.Default.MergeType to ToolAccentCategory.PDF_CORE),
                        Triple("lock", "Password" to "AES-256 lock & unlock", Icons.Default.Lock to ToolAccentCategory.SIGN_SECURITY)
                    )
                    when (previewViewMode) {
                        ToolsViewMode.LIST -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                sampleTools.take(3).forEachIndexed { idx, (id, labels, iconCat) ->
                                    ToolCard(
                                        toolId = id,
                                        title = labels.first,
                                        subtitle = labels.second,
                                        icon = iconCat.first,
                                        category = iconCat.second,
                                        viewMode = ToolsViewMode.LIST,
                                        isFavorite = idx == 0,
                                        onClick = {}
                                    )
                                }
                            }
                        }
                        ToolsViewMode.GRID_2 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                sampleTools.take(4).chunked(2).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        row.forEach { (id, labels, iconCat) ->
                                            ToolCard(
                                                toolId = id,
                                                title = labels.first,
                                                subtitle = labels.second,
                                                icon = iconCat.first,
                                                category = iconCat.second,
                                                viewMode = ToolsViewMode.GRID_2,
                                                isFavorite = id == "sign",
                                                onClick = {},
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        ToolsViewMode.COMPACT_3 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                sampleTools.chunked(3).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { (id, labels, iconCat) ->
                                            ToolCard(
                                                toolId = id,
                                                title = labels.first,
                                                subtitle = labels.second,
                                                icon = iconCat.first,
                                                category = iconCat.second,
                                                viewMode = ToolsViewMode.COMPACT_3,
                                                isFavorite = id == "sign",
                                                onClick = {},
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    Text("Canonical 4-Step Tool Flow", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolStep.entries.forEach { step ->
                            FilterChip(
                                selected = activeStep == step,
                                onClick = { activeStep = step },
                                label = { Text(stringResource(step.labelRes)) }
                            )
                        }
                    }
                    ToolStepper(currentStep = activeStep)
                    Spacer(Modifier.height(8.dp))
                    when (activeStep) {
                        ToolStep.PROCESS -> ProcessingProgressView(
                            progress = OperationProgress(0.68f, "Optimizing PDF streams on device…"),
                            onCancel = { activeStep = ToolStep.CONFIGURE }
                        )
                        ToolStep.DONE -> ToolDoneCard(
                            title = "Contract_compressed.pdf ready",
                            summaryText = "Saved offline without touching the original file.",
                            metrics = listOf("Before" to "4.8 MB", "After" to "1.1 MB"),
                            onOpen = {},
                            onShare = {},
                            onSaveAs = {},
                            onSaveToCategory = { showMoveCategorySheet = true }
                        )
                        else -> EmptyStateView(
                            icon = Icons.Default.FolderOff,
                            title = "Reusable Empty & Skeleton State",
                            subtitle = "Toggle skeleton loaders or open shared bottom sheets below.",
                            primaryActionLabel = "Confirm Sheet",
                            onPrimaryAction = { showConfirmSheet = true },
                            secondaryActionLabel = "Move Category",
                            onSecondaryAction = { showMoveCategorySheet = true }
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Skeleton Loader", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { showSkeletons = !showSkeletons }) {
                            Text(if (showSkeletons) "Hide" else "Show")
                        }
                    }
                    if (showSkeletons) {
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SkeletonDocumentRow()
                            SkeletonDocumentRow()
                        }
                    }
                }
            }
        }
    }

    if (showConfirmSheet) {
        ConfirmActionBottomSheet(
            title = "Replace original file?",
            message = "Originals are never overwritten without your explicit confirmation. Save a new copy instead or confirm replacement.",
            confirmLabel = "Replace Original",
            isDestructive = true,
            onConfirm = {},
            onDismiss = { showConfirmSheet = false }
        )
    }

    if (showMoveCategorySheet) {
        MoveToCategoryBottomSheet(
            categories = sampleCategories,
            currentCategoryId = selectedCategoryId,
            onSelectCategory = { selectedCategoryId = it },
            onDismiss = { showMoveCategorySheet = false }
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
