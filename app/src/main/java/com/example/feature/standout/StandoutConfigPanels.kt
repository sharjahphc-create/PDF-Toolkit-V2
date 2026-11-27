package com.example.feature.standout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.LibraryDocument
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RedactConfigPanel(
    state: StandoutUiState,
    viewModel: StandoutToolsViewModel
) {
    val pageBoxes = state.redactionBoxes.filter { it.pageIndex == state.currentPage }
    Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Page ${state.currentPage + 1} of ${state.primaryPageCount} • ${state.redactionBoxes.size} box(es)",
                    style = MaterialTheme.typography.titleSmall
                )
                Row {
                    IconButton(
                        onClick = { viewModel.changePreviewPage(state.currentPage - 1) },
                        enabled = state.currentPage > 0
                    ) { Icon(Icons.Default.ChevronLeft, contentDescription = "Previous page") }
                    IconButton(
                        onClick = { viewModel.changePreviewPage(state.currentPage + 1) },
                        enabled = state.currentPage < state.primaryPageCount - 1
                    ) { Icon(Icons.Default.ChevronRight, contentDescription = "Next page") }
                }
            }

            state.previewBitmap?.let { bmp ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(bmp.width.toFloat() / bmp.height.coerceAtLeast(1))
                        .pointerInput(state.currentPage, state.draftRedactW, state.draftRedactH) {
                            detectTapGestures { offset ->
                                val nx = ((offset.x / size.width) - state.draftRedactW / 2f).coerceIn(0f, 0.92f)
                                val ny = ((offset.y / size.height) - state.draftRedactH / 2f).coerceIn(0f, 0.92f)
                                viewModel.updateDraftRedactBox(nx, ny, state.draftRedactW, state.draftRedactH)
                            }
                        }
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Redaction preview — tap to position box",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Existing committed redaction boxes on this page
                        pageBoxes.forEach { box ->
                            drawRect(
                                color = Color.Black.copy(alpha = 0.92f),
                                topLeft = Offset(box.x * size.width, box.y * size.height),
                                size = Size(box.w * size.width, box.h * size.height)
                            )
                        }
                        // Active draft box outline
                        drawRect(
                            color = Color.Black.copy(alpha = 0.65f),
                            topLeft = Offset(state.draftRedactX * size.width, state.draftRedactY * size.height),
                            size = Size(state.draftRedactW * size.width, state.draftRedactH * size.height)
                        )
                        drawRect(
                            color = Color(0xFFEF4444),
                            topLeft = Offset(state.draftRedactX * size.width, state.draftRedactY * size.height),
                            size = Size(state.draftRedactW * size.width, state.draftRedactH * size.height),
                            style = Stroke(width = 3f)
                        )
                    }
                }
            }

            Text(
                "Tap preview to position the redaction bar, adjust size, then tap Add Box to stack multiple redactions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = viewModel::addCurrentRedactBox,
                    modifier = Modifier.weight(1f).testTag("redact_add_box_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Box")
                }
                OutlinedButton(
                    onClick = viewModel::undoLastRedactBox,
                    enabled = state.redactionBoxes.isNotEmpty()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Undo")
                }
                IconButton(
                    onClick = viewModel::clearPageRedactBoxes,
                    enabled = pageBoxes.isNotEmpty()
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear page boxes")
                }
            }

            Text("Box Width (${(state.draftRedactW * 100).roundToInt()}%)", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = state.draftRedactW,
                onValueChange = { viewModel.updateDraftRedactBox(state.draftRedactX, state.draftRedactY, it, state.draftRedactH) },
                valueRange = 0.10f..0.92f
            )
            Text("Box Height (${(state.draftRedactH * 100).roundToInt()}%)", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = state.draftRedactH,
                onValueChange = { viewModel.updateDraftRedactBox(state.draftRedactX, state.draftRedactY, state.draftRedactW, it) },
                valueRange = 0.03f..0.30f
            )

            if (state.piiMatches.isNotEmpty()) {
                Text("Detected PII (tap to jump to page)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.piiMatches.take(8).forEach { match ->
                        AssistChip(
                            onClick = { match.pageNumbers.firstOrNull()?.let { viewModel.changePreviewPage(it - 1) } },
                            label = { Text("${match.category}: ${match.sample} (p.${match.pageNumbers.firstOrNull() ?: 1})") }
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-scrub detected PII (${state.piiMatches.size})")
                Switch(
                    checked = state.scrubDetectedPii,
                    onCheckedChange = { viewModel.updateRedactToggles(state.redactAllPages, it, state.stripMetadata) }
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Apply boxes to all pages")
                Switch(
                    checked = state.redactAllPages,
                    onCheckedChange = { viewModel.updateRedactToggles(it, state.scrubDetectedPii, state.stripMetadata) }
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Strip PDF metadata (Author, Title)")
                Switch(
                    checked = state.stripMetadata,
                    onCheckedChange = { viewModel.updateRedactToggles(state.redactAllPages, state.scrubDetectedPii, it) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatermarkConfigPanel(
    state: StandoutUiState,
    viewModel: StandoutToolsViewModel
) {
    Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = state.watermarkText,
                onValueChange = { viewModel.updateWatermarkFields(it, state.headerText, state.footerText, state.includePageNumbers) },
                label = { Text("Watermark Text") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("Watermark Style", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WatermarkStyle.entries.forEach { style ->
                    FilterChip(
                        selected = state.watermarkStyle == style,
                        onClick = {
                            viewModel.updateWatermarkFields(
                                state.watermarkText, state.headerText, state.footerText,
                                state.includePageNumbers, state.watermarkOpacity, style, state.pageNumberFormat
                            )
                        },
                        label = { Text(style.label) }
                    )
                }
            }
            Text("Opacity (${(state.watermarkOpacity * 100).roundToInt()}%)", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = state.watermarkOpacity,
                onValueChange = {
                    viewModel.updateWatermarkFields(
                        state.watermarkText, state.headerText, state.footerText,
                        state.includePageNumbers, it, state.watermarkStyle, state.pageNumberFormat
                    )
                },
                valueRange = 0.08f..0.60f
            )
            OutlinedTextField(
                value = state.headerText,
                onValueChange = { viewModel.updateWatermarkFields(state.watermarkText, it, state.footerText, state.includePageNumbers) },
                label = { Text("Header Text") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.footerText,
                onValueChange = { viewModel.updateWatermarkFields(state.watermarkText, state.headerText, it, state.includePageNumbers) },
                label = { Text("Footer Text") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Add Page Numbers")
                Switch(
                    checked = state.includePageNumbers,
                    onCheckedChange = { viewModel.updateWatermarkFields(state.watermarkText, state.headerText, state.footerText, it) }
                )
            }
            if (state.includePageNumbers) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageNumberFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = state.pageNumberFormat == fmt,
                            onClick = {
                                viewModel.updateWatermarkFields(
                                    state.watermarkText, state.headerText, state.footerText,
                                    state.includePageNumbers, state.watermarkOpacity, state.watermarkStyle, fmt
                                )
                            },
                            label = { Text(fmt.label) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompareConfigPanel(
    state: StandoutUiState,
    libraryPdfs: List<LibraryDocument>,
    onPickSecondFromSaf: () -> Unit,
    onPickSecondFromLibrary: (LibraryDocument) -> Unit
) {
    Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Document A: ${state.primaryName} (${state.primaryPageCount} pages)", style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (state.secondFile != null) "Document B: ${state.secondName}" else "Document B: Not selected",
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.secondFile != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onPickSecondFromSaf, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.secondFile == null) "Pick Second PDF from Device" else "Change Second PDF")
            }
            if (libraryPdfs.isNotEmpty()) {
                Text("Or choose Document B from Library", style = MaterialTheme.typography.labelLarge)
                libraryPdfs.take(5).forEach { doc ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickSecondFromLibrary(doc) }
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(doc.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompareDiffSummaryCard(diff: PdfCompareDiff) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().testTag("compare_diff_summary_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Side-by-Side Text Diff Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Doc A: ${diff.leftPages}p • Doc B: ${diff.rightPages}p • ${diff.changedPageCount} changed page(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (diff.addedLines.isNotEmpty()) {
                Text("Added (${diff.addedLines.size})", style = MaterialTheme.typography.labelLarge, color = Color(0xFF059669))
                diff.addedLines.take(6).forEach { line ->
                    Text("+ $line", style = MaterialTheme.typography.bodySmall, color = Color(0xFF047857), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (diff.removedLines.isNotEmpty()) {
                Text("Removed (${diff.removedLines.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                diff.removedLines.take(6).forEach { line ->
                    Text("- $line", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (diff.addedLines.isEmpty() && diff.removedLines.isEmpty()) {
                Text("No text differences detected between the two PDFs.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkflowsConfigPanel(
    state: StandoutUiState,
    viewModel: StandoutToolsViewModel
) {
    val cfg = state.workflowConfig
    Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Quick Recipes", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { viewModel.applyWorkflowPreset(0) }, label = { Text("Confidential Share") })
                AssistChip(onClick = { viewModel.applyWorkflowPreset(1) }, label = { Text("Clean Archive") })
                AssistChip(onClick = { viewModel.applyWorkflowPreset(2) }, label = { Text("Stamp & Lock") })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("1. Compress PDF")
                Switch(checked = cfg.compress, onCheckedChange = { c -> viewModel.updateWorkflowConfig { it.copy(compress = c) } })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("2. Strip Metadata")
                Switch(checked = cfg.stripMetadata, onCheckedChange = { s -> viewModel.updateWorkflowConfig { it.copy(stripMetadata = s) } })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("3. Stamp Page Numbers")
                Switch(checked = cfg.includePageNumbers, onCheckedChange = { p -> viewModel.updateWorkflowConfig { it.copy(includePageNumbers = p) } })
            }
            OutlinedTextField(
                value = cfg.watermarkText,
                onValueChange = { w -> viewModel.updateWorkflowConfig { it.copy(watermarkText = w) } },
                label = { Text("4. Optional Watermark Text") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = cfg.password,
                onValueChange = { pw -> viewModel.updateWorkflowConfig { it.copy(password = pw) } },
                label = { Text("5. Optional AES-256 Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchConfigPanel(
    state: StandoutUiState,
    viewModel: StandoutToolsViewModel,
    onAddMoreFromSaf: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Batch Operation", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BatchOperationMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.batchMode == mode,
                            onClick = { viewModel.setBatchMode(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("batch_mode_${mode.name.lowercase()}")
                        )
                    }
                }
                Text(
                    text = state.batchMode.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (state.batchMode) {
                    BatchOperationMode.COMPRESS -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BatchCompressionPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = state.batchCompressionPreset == preset,
                                    onClick = { viewModel.setBatchCompressionPreset(preset) },
                                    label = { Text(preset.label) }
                                )
                            }
                        }
                    }
                    BatchOperationMode.WATERMARK_AND_NUMBERS -> {
                        OutlinedTextField(
                            value = state.watermarkText,
                            onValueChange = { viewModel.updateWatermarkFields(it, state.headerText, state.footerText, state.includePageNumbers) },
                            label = { Text("Watermark text") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Stamp Page X of Y")
                            Switch(
                                checked = state.includePageNumbers,
                                onCheckedChange = { viewModel.updateWatermarkFields(state.watermarkText, state.headerText, state.footerText, it) }
                            )
                        }
                    }
                    BatchOperationMode.LOCK_AES256 -> {
                        OutlinedTextField(
                            value = state.batchPassword,
                            onValueChange = viewModel::setBatchPassword,
                            label = { Text("AES-256 Password (min 4 chars)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("batch_password_input")
                        )
                    }
                    BatchOperationMode.EXTRACT_TEXT -> {
                        Text(
                            "Extracts all pages of each PDF into separate UTF-8 .txt files inside a single ZIP archive.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Batch Queue (${state.batchFiles.size} PDFs)", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = onAddMoreFromSaf) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add PDFs")
                    }
                }
                state.batchFiles.forEachIndexed { idx, (_, name) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeBatchItem(idx) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove $name", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConvertConfigPanel(
    state: StandoutUiState,
    viewModel: StandoutToolsViewModel
) {
    Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.convertSubMode == ConvertSubMode.PDF_TO_PNG_ZIP,
                    onClick = { viewModel.setConvertSubMode(ConvertSubMode.PDF_TO_PNG_ZIP) },
                    enabled = state.primaryFile != null,
                    shape = SegmentedButtonDefaults.itemShape(0, 3)
                ) { Text("PNG Zip") }
                SegmentedButton(
                    selected = state.convertSubMode == ConvertSubMode.PDF_TO_TEXT,
                    onClick = { viewModel.setConvertSubMode(ConvertSubMode.PDF_TO_TEXT) },
                    enabled = state.primaryFile != null,
                    shape = SegmentedButtonDefaults.itemShape(1, 3)
                ) { Text("PDF→Text") }
                SegmentedButton(
                    selected = state.convertSubMode == ConvertSubMode.TEXT_TO_PDF,
                    onClick = { viewModel.setConvertSubMode(ConvertSubMode.TEXT_TO_PDF) },
                    shape = SegmentedButtonDefaults.itemShape(2, 3)
                ) { Text("Text→PDF") }
            }
            when (state.convertSubMode) {
                ConvertSubMode.TEXT_TO_PDF -> {
                    OutlinedTextField(
                        value = state.textToPdfTitle,
                        onValueChange = { viewModel.updateTextToPdf(it, state.textToPdfBody) },
                        label = { Text("PDF Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.textToPdfBody,
                        onValueChange = { viewModel.updateTextToPdf(state.textToPdfTitle, it) },
                        label = { Text("Markdown / Plain Text Content") },
                        minLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ConvertSubMode.PDF_TO_TEXT -> {
                    Text("Extracts all text across ${state.primaryPageCount} page(s) into a clean UTF-8 .txt file.")
                }
                ConvertSubMode.PDF_TO_PNG_ZIP -> {
                    Text("Exports all ${state.primaryPageCount} PDF page(s) as PNG images inside a .zip archive.")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("High Resolution (240 DPI)")
                        Switch(checked = state.pngExportHighDpi, onCheckedChange = viewModel::setPngExportHighDpi)
                    }
                }
            }
        }
    }
}

@Composable
fun IdCardConfigPanel(
    state: StandoutUiState,
    viewModel: StandoutToolsViewModel,
    onPickBackPhoto: () -> Unit
) {
    Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Front side selected: ${state.imageUris.size} image", style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (state.backIdUri != null) "Back side selected" else "Back side optional",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = onPickBackPhoto) {
                Text(if (state.backIdUri == null) "Pick Back Side Photo" else "Replace Back Photo")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("High-contrast B&W scan filter")
                Switch(
                    checked = state.idGrayscaleClean,
                    onCheckedChange = { viewModel.updateIdCardOptions(it, state.idVerificationWatermark) }
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Add \"Copy for Verification\" stamp")
                Switch(
                    checked = state.idVerificationWatermark,
                    onCheckedChange = { viewModel.updateIdCardOptions(state.idGrayscaleClean, it) }
                )
            }
        }
    }
}

@Composable
fun QrBarcodeConfigPanel(
    state: StandoutUiState,
    onCopiedMessage: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Decoded Barcodes (${state.decodedBarcodes.size})", style = MaterialTheme.typography.titleMedium)
            if (state.decodedBarcodes.isEmpty()) {
                Text("No QR or barcode found in selected photo.")
            } else {
                state.decodedBarcodes.forEach { bc ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${bc.displayType} • ${bc.formatLabel}", style = MaterialTheme.typography.labelLarge)
                                Text(bc.rawValue, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(bc.rawValue))
                                    onCopiedMessage("Copied decoded value")
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy value")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanCleanConfigPanel(
    state: StandoutUiState,
    viewModel: StandoutToolsViewModel
) {
    Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${state.imageUris.size} scan page(s) ready", style = MaterialTheme.typography.titleMedium)
            Text("Scan Filter Mode", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScanCleanMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.scanCleanMode == mode,
                        onClick = { viewModel.setScanCleanMode(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
            Text(
                "Enhances page contrast and removes shadows locally before assembling an A4 PDF.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslateConfigPanel(
    state: StandoutUiState,
    viewModel: StandoutToolsViewModel
) {
    Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Source Language", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TranslateTargetLang.entries.forEach { lang ->
                    FilterChip(
                        selected = state.sourceLang == lang,
                        onClick = { viewModel.setTranslateLanguages(source = lang) },
                        label = { Text(lang.displayName) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Target Language", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TranslateTargetLang.entries.forEach { lang ->
                    FilterChip(
                        selected = state.translateLang == lang,
                        onClick = { viewModel.setTranslateLanguages(target = lang) },
                        label = { Text(lang.displayName) }
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bilingual Side-by-Side PDF", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Include original text above each translated page",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.bilingualTranslate,
                    onCheckedChange = { viewModel.setTranslateLanguages(bilingual = it) }
                )
            }
        }
    }
}
