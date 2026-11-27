package com.example.feature.documents

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Title
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun WordEditorPanel(
    state: OfficeToolUiState,
    onExportFormatChange: (OfficeExportFormat) -> Unit,
    onParagraphTextChange: (String, String) -> Unit,
    onToggleHeading: (String) -> Unit,
    onToggleBold: (String) -> Unit,
    onToggleItalic: (String) -> Unit,
    onAddParagraph: () -> Unit,
    onRemoveParagraph: (String) -> Unit
) {
    val doc = state.docxModel ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.exportFormat == OfficeExportFormat.NATIVE,
                onClick = { onExportFormatChange(OfficeExportFormat.NATIVE) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                modifier = Modifier.testTag("word_format_docx")
            ) { Text("Save .docx") }
            SegmentedButton(
                selected = state.exportFormat == OfficeExportFormat.PDF,
                onClick = { onExportFormatChange(OfficeExportFormat.PDF) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                modifier = Modifier.testTag("word_format_pdf")
            ) { Text("Export PDF") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${doc.paragraphs.size} paragraphs" +
                    if (doc.tableCount > 0) " • ${doc.tableCount} tables preserved" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onAddParagraph,
                modifier = Modifier.heightIn(min = 48.dp).testTag("word_add_paragraph")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Paragraph")
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(doc.paragraphs, key = { _, p -> p.id }) { idx, para ->
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = para.isHeading,
                                onClick = { onToggleHeading(para.id) },
                                label = { Text("H") },
                                leadingIcon = { Icon(Icons.Default.Title, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            FilterChip(
                                selected = para.isBold,
                                onClick = { onToggleBold(para.id) },
                                label = { Icon(Icons.Default.FormatBold, contentDescription = "Bold", modifier = Modifier.size(16.dp)) }
                            )
                            FilterChip(
                                selected = para.isItalic,
                                onClick = { onToggleItalic(para.id) },
                                label = { Icon(Icons.Default.FormatItalic, contentDescription = "Italic", modifier = Modifier.size(16.dp)) }
                            )
                            Spacer(Modifier.weight(1f))
                            if (doc.paragraphs.size > 1) {
                                IconButton(onClick = { onRemoveParagraph(para.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete paragraph")
                                }
                            }
                        }
                        OutlinedTextField(
                            value = para.text,
                            onValueChange = { onParagraphTextChange(para.id, it) },
                            placeholder = { Text("Paragraph ${idx + 1}") },
                            textStyle = (if (para.isHeading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
                                .copy(
                                    fontWeight = if (para.isBold || para.isHeading) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (para.isItalic) FontStyle.Italic else FontStyle.Normal
                                ),
                            modifier = Modifier.fillMaxWidth().testTag("word_para_$idx")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExcelEditorPanel(
    state: OfficeToolUiState,
    onExportFormatChange: (OfficeExportFormat) -> Unit,
    onSelectSheet: (Int) -> Unit,
    onSelectCell: (Int, Int) -> Unit,
    onUpdateCellValue: (String) -> Unit,
    onAddRow: () -> Unit,
    onAddColumn: () -> Unit
) {
    val wb = state.xlsxModel ?: return
    val sheet = wb.sheets.getOrNull(state.activeSheetIndex) ?: return
    val colCount = (sheet.rows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
    val selectedValue = sheet.rows.getOrNull(state.selectedRow)?.getOrNull(state.selectedCol).orEmpty()
    val cellRef = "${excelColName(state.selectedCol)}${state.selectedRow + 1}"

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.exportFormat == OfficeExportFormat.NATIVE,
                onClick = { onExportFormatChange(OfficeExportFormat.NATIVE) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                modifier = Modifier.testTag("excel_format_xlsx")
            ) { Text("Save .xlsx") }
            SegmentedButton(
                selected = state.exportFormat == OfficeExportFormat.PDF,
                onClick = { onExportFormatChange(OfficeExportFormat.PDF) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                modifier = Modifier.testTag("excel_format_pdf")
            ) { Text("Export PDF") }
        }

        if (wb.sheets.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(wb.sheets) { idx, s ->
                    FilterChip(
                        selected = idx == state.activeSheetIndex,
                        onClick = { onSelectSheet(idx) },
                        label = { Text(s.name) }
                    )
                }
            }
        }

        OutlinedTextField(
            value = selectedValue,
            onValueChange = onUpdateCellValue,
            label = { Text("Cell $cellRef (text, number, or =SUM(...))") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("excel_formula_input")
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onAddRow,
                modifier = Modifier.heightIn(min = 48.dp).testTag("excel_add_row")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Row")
            }
            OutlinedButton(
                onClick = onAddColumn,
                modifier = Modifier.heightIn(min = 48.dp).testTag("excel_add_col")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Column")
            }
        }

        val hScroll = rememberScrollState()
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                .horizontalScroll(hScroll)
        ) {
            item {
                Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 36.dp)
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("", style = MaterialTheme.typography.labelSmall)
                    }
                    for (c in 0 until colCount) {
                        Box(
                            modifier = Modifier
                                .size(width = 112.dp, height = 36.dp)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(excelColName(c), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            itemsIndexed(sheet.rows) { rIdx, row ->
                Row {
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 42.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${rIdx + 1}", style = MaterialTheme.typography.labelSmall)
                    }
                    for (cIdx in 0 until colCount) {
                        val isSel = rIdx == state.selectedRow && cIdx == state.selectedCol
                        val valText = row.getOrNull(cIdx).orEmpty()
                        Box(
                            modifier = Modifier
                                .size(width = 112.dp, height = 42.dp)
                                .background(
                                    if (isSel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    width = if (isSel) 2.dp else 0.5.dp,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                                .clickable { onSelectCell(rIdx, cIdx) }
                                .padding(horizontal = 6.dp)
                                .testTag("excel_cell_${rIdx}_$cIdx"),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = valText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PowerPointConvertPanel(
    state: OfficeToolUiState,
    onSelectSlide: (Int) -> Unit,
    onToggleNotes: (Boolean) -> Unit
) {
    val pptx = state.pptxModel ?: return
    val currentSlide = pptx.slides.getOrNull(state.activeSlideIndex)
    val slideAspect = (pptx.slideWidthPt / pptx.slideHeightPt).coerceIn(0.6f, 2.2f)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Slide ${state.activeSlideIndex + 1} of ${pptx.slides.size}",
                style = MaterialTheme.typography.titleMedium
            )
            Row {
                IconButton(
                    onClick = { onSelectSlide(state.activeSlideIndex - 1) },
                    enabled = state.activeSlideIndex > 0
                ) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous slide")
                }
                IconButton(
                    onClick = { onSelectSlide(state.activeSlideIndex + 1) },
                    enabled = state.activeSlideIndex < pptx.slides.lastIndex
                ) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next slide")
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(slideAspect)
                .clip(MaterialTheme.shapes.large)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val bmp = state.slidePreviewBitmap
            if (bmp != null && !bmp.isRecycled) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Slide ${state.activeSlideIndex + 1} preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(currentSlide?.title ?: "Slide preview")
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(pptx.slides) { idx, slide ->
                FilterChip(
                    selected = idx == state.activeSlideIndex,
                    onClick = { onSelectSlide(idx) },
                    label = { Text("${idx + 1}. ${slide.title.take(18)}") }
                )
            }
        }

        if (currentSlide != null && currentSlide.notes.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Speaker notes", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(currentSlide.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Include speaker notes in exported PDF", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = state.includeSpeakerNotesInPdf,
                onCheckedChange = onToggleNotes
            )
        }
    }
}

private fun excelColName(index: Int): String {
    var n = index
    val sb = StringBuilder()
    while (n >= 0) {
        sb.insert(0, ('A' + (n % 26)))
        n = (n / 26) - 1
    }
    return sb.toString()
}
