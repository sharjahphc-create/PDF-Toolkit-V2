package com.example.feature.pdftools.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.ui.components.MetricPill
import com.example.core.ui.components.RunsOnDeviceBadge
import com.example.feature.pdftools.CorePdfToolUiState
import com.example.feature.pdftools.PasswordToolMode
import com.example.feature.pdftools.engine.CompressionPreset
import kotlin.math.roundToInt

@Composable
fun MergeConfigurePanel(
    state: CorePdfToolUiState,
    onAddMorePdfs: () -> Unit,
    onMoveSource: (Int, Int) -> Unit,
    onRemoveSource: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Order PDFs to combine", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${state.mergeSources.size} files • ${state.mergeSources.sumOf { it.pageCount }} pages total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onAddMorePdfs,
                modifier = Modifier.heightIn(min = 48.dp).testTag("merge_add_more_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add PDF")
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            itemsIndexed(state.mergeSources, key = { _, item -> item.id }) { idx, item ->
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().animateItem()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${idx + 1}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.pageCount} pages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onMoveSource(idx, -1) },
                            enabled = idx > 0
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                        }
                        IconButton(
                            onClick = { onMoveSource(idx, 1) },
                            enabled = idx < state.mergeSources.lastIndex
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                        }
                        IconButton(onClick = { onRemoveSource(item.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove file")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SplitConfigurePanel(
    state: CorePdfToolUiState,
    onRangeChange: (String) -> Unit,
    onTogglePage: (Int) -> Unit,
    onRequestThumbnail: (Int) -> Unit
) {
    val pageCount = state.sourcePdf?.pageCount ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.splitRangeInput,
            onValueChange = onRangeChange,
            label = { Text("Page ranges (e.g. 1-3, 5)") },
            supportingText = {
                Text("${state.selectedSplitPages.size} of $pageCount pages selected")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("split_range_input")
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            items((0 until pageCount).toList(), key = { it }) { pageIdx ->
                LaunchedEffect(pageIdx) { onRequestThumbnail(pageIdx) }
                val isSelected = state.selectedSplitPages.contains(pageIdx)
                val bmp = state.thumbnails[pageIdx]
                Surface(
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = if (isSelected) 4.dp else 1.dp,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.large)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = MaterialTheme.shapes.large
                        )
                        .clickable { onTogglePage(pageIdx) }
                        .testTag("split_page_${pageIdx + 1}")
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (bmp != null && !bmp.isRecycled) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Page ${pageIdx + 1}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Page ${pageIdx + 1}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun CompressConfigurePanel(
    state: CorePdfToolUiState,
    onSelectPreset: (CompressionPreset) -> Unit
) {
    val beforeKb = (state.beforeSizeBytes / 1024L).coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            MetricPill("Original size", "$beforeKb KB", modifier = Modifier.weight(1f))
            MetricPill(
                "Pages",
                "${state.sourcePdf?.pageCount ?: 1}",
                modifier = Modifier.weight(1f)
            )
        }
        Text("Choose compression level", style = MaterialTheme.typography.titleMedium)

        CompressionPreset.entries.forEach { preset ->
            val selected = state.compressionPreset == preset
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = MaterialTheme.shapes.large
                    )
                    .clickable { onSelectPreset(preset) }
                    .testTag("compress_preset_${preset.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(preset.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            preset.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OrganizePagesConfigurePanel(
    state: CorePdfToolUiState,
    onRotate: (Int) -> Unit,
    onToggleDelete: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onCropChange: (Int, Int) -> Unit,
    onRequestThumbnail: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Rotate, delete, reorder, or crop margins per page",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            itemsIndexed(state.organizeSpecs, key = { _, spec -> spec.originalPageIndex }) { idx, spec ->
                LaunchedEffect(spec.originalPageIndex) { onRequestThumbnail(spec.originalPageIndex) }
                val bmp = state.thumbnails[spec.originalPageIndex]
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (spec.isDeleted) 0.48f else 1f)
                        .animateItem()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(width = 62.dp, height = 82.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (bmp != null && !bmp.isRecycled) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Original page ${spec.originalPageIndex + 1}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { rotationZ = spec.rotationDelta.toFloat() }
                                    )
                                } else {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Page ${spec.originalPageIndex + 1}" +
                                        if (spec.isDeleted) " (Deleted)" else "",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Rotate ${spec.rotationDelta}° • Crop ${spec.cropInsetPercent}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row {
                                    IconButton(onClick = { onRotate(idx) }) {
                                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate 90 degrees")
                                    }
                                    IconButton(onClick = { onMove(idx, -1) }, enabled = idx > 0) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move page up")
                                    }
                                    IconButton(
                                        onClick = { onMove(idx, 1) },
                                        enabled = idx < state.organizeSpecs.lastIndex
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move page down")
                                    }
                                    IconButton(onClick = { onToggleDelete(idx) }) {
                                        Icon(
                                            imageVector = if (spec.isDeleted) Icons.Default.Restore else Icons.Default.Delete,
                                            contentDescription = if (spec.isDeleted) "Restore page" else "Delete page"
                                        )
                                    }
                                }
                            }
                        }
                        if (!spec.isDeleted) {
                            Slider(
                                value = spec.cropInsetPercent.toFloat(),
                                onValueChange = { onCropChange(idx, it.roundToInt()) },
                                valueRange = 0f..30f
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PasswordConfigurePanel(
    state: CorePdfToolUiState,
    onModeChange: (PasswordToolMode) -> Unit,
    onFieldsChange: (String?, String?, Boolean?, Boolean?, String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.passwordMode == PasswordToolMode.LOCK_AES256,
                onClick = { onModeChange(PasswordToolMode.LOCK_AES256) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                modifier = Modifier.testTag("password_mode_lock")
            ) { Text("Lock (AES-256)") }

            SegmentedButton(
                selected = state.passwordMode == PasswordToolMode.UNLOCK,
                onClick = { onModeChange(PasswordToolMode.UNLOCK) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(Icons.Default.LockOpen, contentDescription = null) },
                modifier = Modifier.testTag("password_mode_unlock")
            ) { Text("Unlock PDF") }
        }

        RunsOnDeviceBadge()

        if (state.passwordMode == PasswordToolMode.LOCK_AES256) {
            OutlinedTextField(
                value = state.lockUserPassword,
                onValueChange = { onFieldsChange(it, null, null, null, null) },
                label = { Text("Open password (required to view PDF)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("lock_user_password_input")
            )
            OutlinedTextField(
                value = state.lockOwnerPassword,
                onValueChange = { onFieldsChange(null, it, null, null, null) },
                label = { Text("Owner permissions password (optional)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("lock_owner_password_input")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Allow printing", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.allowPrinting,
                    onCheckedChange = { onFieldsChange(null, null, it, null, null) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Allow copying text & images", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.allowCopy,
                    onCheckedChange = { onFieldsChange(null, null, null, it, null) }
                )
            }
        } else {
            OutlinedTextField(
                value = state.unlockPasswordInput,
                onValueChange = { onFieldsChange(null, null, null, null, it) },
                label = { Text("Current PDF password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("unlock_password_input")
            )
            Text(
                "Removes encryption into a new unlocked copy while leaving the original locked file untouched.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
