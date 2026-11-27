package com.example.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.datastore.PdfColorFilterMode
import com.example.core.datastore.PdfFitMode
import com.example.core.datastore.ReaderBookPreset
import com.example.core.datastore.ReaderFontChoice
import com.example.core.datastore.ReaderOrientationLock
import com.example.core.datastore.ReaderPageTurnMode
import com.example.core.datastore.ReaderTextAlignment
import com.example.core.datastore.ReaderThemeMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderControlsBottomSheet(
    preset: ReaderBookPreset,
    isPdfFixedMode: Boolean,
    isPerBookPresetActive: Boolean,
    onTogglePerBookPreset: (Boolean) -> Unit,
    onUpdatePreset: ((ReaderBookPreset) -> ReaderBookPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reader Controls", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isPerBookPresetActive) "This book" else "Global",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = isPerBookPresetActive,
                        onCheckedChange = onTogglePerBookPreset,
                        modifier = Modifier.testTag("reader_per_book_preset_switch")
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("Display", "Layout", "Behavior").forEachIndexed { idx, label ->
                    SegmentedButton(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        shape = SegmentedButtonDefaults.itemShape(idx, 3),
                        modifier = Modifier.testTag("reader_controls_tab_$idx")
                    ) {
                        Text(label)
                    }
                }
            }

            when (selectedTab) {
                0 -> DisplayControlsTab(
                    preset = preset,
                    isPdfFixedMode = isPdfFixedMode,
                    onUpdatePreset = onUpdatePreset
                )
                1 -> LayoutControlsTab(
                    preset = preset,
                    onUpdatePreset = onUpdatePreset
                )
                else -> BehaviorControlsTab(
                    preset = preset,
                    onUpdatePreset = onUpdatePreset
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplayControlsTab(
    preset: ReaderBookPreset,
    isPdfFixedMode: Boolean,
    onUpdatePreset: ((ReaderBookPreset) -> ReaderBookPreset) -> Unit
) {
    // Theme swatches (White, Sepia, Gray, Dark, True-Black AMOLED, Follow system)
    Text("Reading Theme", style = MaterialTheme.typography.labelLarge)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val themeOptions = listOf(
            Triple(ReaderThemeMode.PAPER, "White", Color(0xFFFDFCF9)),
            Triple(ReaderThemeMode.SEPIA, "Sepia", Color(0xFFF4EAD5)),
            Triple(ReaderThemeMode.GRAY, "Gray", Color(0xFFE5E7EB)),
            Triple(ReaderThemeMode.SLATE_DARK, "Dark", Color(0xFF1A2029)),
            Triple(ReaderThemeMode.OLED_BLACK, "AMOLED", Color(0xFF000000)),
            Triple(ReaderThemeMode.FOLLOW_SYSTEM, "System", Color(0xFF64748B))
        )
        themeOptions.forEach { (mode, label, swatchColor) ->
            val selected = preset.theme == mode
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .heightIn(min = 48.dp)
                    .clickable { onUpdatePreset { it.copy(theme = mode) } }
                    .testTag("reader_theme_${mode.name}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                    )
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (isPdfFixedMode) {
        HorizontalDivider()
        Text("PDF Color Filter", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                PdfColorFilterMode.LIGHT to "Light",
                PdfColorFilterMode.DARK to "Dark",
                PdfColorFilterMode.SEPIA to "Sepia",
                PdfColorFilterMode.INVERT to "Invert"
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = preset.pdfColorFilter == mode,
                    onClick = { onUpdatePreset { it.copy(pdfColorFilter = mode) } },
                    label = { Text(label) },
                    modifier = Modifier.testTag("reader_pdf_filter_${mode.name}")
                )
            }
        }

        Text("PDF Page Fit & Margins", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(
                PdfFitMode.FIT_PAGE to "Fit Page",
                PdfFitMode.FIT_WIDTH to "Fit Width",
                PdfFitMode.AUTO_CROP to "Auto-Crop"
            ).forEachIndexed { idx, (mode, label) ->
                SegmentedButton(
                    selected = preset.pdfFitMode == mode,
                    onClick = {
                        onUpdatePreset {
                            it.copy(
                                pdfFitMode = mode,
                                autoCropMargins = mode == PdfFitMode.AUTO_CROP
                            )
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(idx, 3),
                    modifier = Modifier.testTag("reader_pdf_fit_${mode.name}")
                ) {
                    Text(label)
                }
            }
        }
    }

    HorizontalDivider()
    // Window Brightness (no system permission needed)
    val isSystemBrightness = preset.brightness < 0f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Brightness6, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isSystemBrightness) "Brightness (Auto / System)"
                else "Brightness (${(preset.brightness * 100).roundToInt()}%)",
                style = MaterialTheme.typography.labelLarge
            )
        }
        Switch(
            checked = isSystemBrightness,
            onCheckedChange = { useAuto ->
                onUpdatePreset { it.copy(brightness = if (useAuto) -1f else 0.65f) }
            },
            modifier = Modifier.testTag("reader_brightness_auto_switch")
        )
    }
    Slider(
        value = if (isSystemBrightness) 0.55f else preset.brightness.coerceIn(0.05f, 1.0f),
        onValueChange = { v -> onUpdatePreset { it.copy(brightness = v) } },
        valueRange = 0.05f..1.0f,
        modifier = Modifier.testTag("reader_brightness_slider")
    )

    // Warm Light Filter + Schedule
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WbTwilight, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Warm Light (${(preset.warmLight * 100).roundToInt()}%)",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
    Slider(
        value = preset.warmLight,
        onValueChange = { v -> onUpdatePreset { it.copy(warmLight = v) } },
        valueRange = 0f..0.42f,
        modifier = Modifier.testTag("reader_warm_light_slider")
    )
    ReaderSwitchRow(
        title = "Schedule Warm Light",
        subtitle = "Auto-tint between ${formatHour(preset.warmLightStartHour)} and ${formatHour(preset.warmLightEndHour)}",
        checked = preset.warmLightScheduleEnabled,
        testTag = "reader_warm_schedule_switch",
        onCheckedChange = { enabled ->
            onUpdatePreset {
                it.copy(
                    warmLightScheduleEnabled = enabled,
                    warmLight = if (enabled && it.warmLight < 0.05f) 0.22f else it.warmLight
                )
            }
        }
    )
    if (preset.warmLightScheduleEnabled) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(19, 7, "7 PM – 7 AM"),
                Triple(20, 6, "8 PM – 6 AM"),
                Triple(21, 6, "9 PM – 6 AM"),
                Triple(22, 7, "10 PM – 7 AM")
            ).forEach { (startH, endH, label) ->
                FilterChip(
                    selected = preset.warmLightStartHour == startH && preset.warmLightEndHour == endH,
                    onClick = {
                        onUpdatePreset { it.copy(warmLightStartHour = startH, warmLightEndHour = endH) }
                    },
                    label = { Text(label) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayoutControlsTab(
    preset: ReaderBookPreset,
    onUpdatePreset: ((ReaderBookPreset) -> ReaderBookPreset) -> Unit
) {
    Text("Font Family (Reading View & EPUB)", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            ReaderFontChoice.SERIF to "Serif",
            ReaderFontChoice.SANS to "Sans",
            ReaderFontChoice.DYSLEXIC to "Dyslexia-Friendly",
            ReaderFontChoice.MONO to "Mono"
        ).forEach { (font, label) ->
            FilterChip(
                selected = preset.fontChoice == font,
                onClick = { onUpdatePreset { it.copy(fontChoice = font) } },
                label = { Text(label) },
                modifier = Modifier.testTag("reader_font_${font.name}")
            )
        }
    }

    Column {
        Text(
            "Font Size (${(preset.fontScale * 100).roundToInt()}%)",
            style = MaterialTheme.typography.labelLarge
        )
        Slider(
            value = preset.fontScale,
            onValueChange = { v -> onUpdatePreset { it.copy(fontScale = v) } },
            valueRange = 0.8f..1.8f,
            modifier = Modifier.testTag("reader_font_size_slider")
        )
    }

    Column {
        Text(
            "Line Spacing (${String.format("%.2fx", preset.lineSpacingMultiplier)})",
            style = MaterialTheme.typography.labelLarge
        )
        Slider(
            value = preset.lineSpacingMultiplier,
            onValueChange = { v -> onUpdatePreset { it.copy(lineSpacingMultiplier = v) } },
            valueRange = 1.15f..2.2f,
            modifier = Modifier.testTag("reader_line_spacing_slider")
        )
    }

    Column {
        Text(
            "Page Margins (${preset.horizontalMarginDp} dp)",
            style = MaterialTheme.typography.labelLarge
        )
        Slider(
            value = preset.horizontalMarginDp.toFloat(),
            onValueChange = { v -> onUpdatePreset { it.copy(horizontalMarginDp = v.roundToInt()) } },
            valueRange = 12f..48f,
            modifier = Modifier.testTag("reader_margin_slider")
        )
    }

    Text("Text Alignment", style = MaterialTheme.typography.labelLarge)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val alignments = listOf(
            Triple(ReaderTextAlignment.START, "Left", Icons.Default.FormatAlignLeft),
            Triple(ReaderTextAlignment.JUSTIFY, "Justify", Icons.Default.FormatAlignJustify),
            Triple(ReaderTextAlignment.CENTER, "Center", Icons.Default.FormatAlignCenter)
        )
        alignments.forEachIndexed { idx, (align, label, icon) ->
            SegmentedButton(
                selected = preset.textAlignment == align,
                onClick = { onUpdatePreset { it.copy(textAlignment = align) } },
                shape = SegmentedButtonDefaults.itemShape(idx, alignments.size),
                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("reader_align_${align.name}")
            ) {
                Text(label)
            }
        }
    }

    ReaderSwitchRow(
        title = "Bold Text",
        subtitle = "Increase stroke weight for easier scanning",
        checked = preset.boldText,
        testTag = "reader_bold_text_switch",
        onCheckedChange = { b -> onUpdatePreset { it.copy(boldText = b) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BehaviorControlsTab(
    preset: ReaderBookPreset,
    onUpdatePreset: ((ReaderBookPreset) -> ReaderBookPreset) -> Unit
) {
    Text("Page-Turn Mode", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            ReaderPageTurnMode.SLIDE to "Slide",
            ReaderPageTurnMode.SCROLL to "Continuous Scroll",
            ReaderPageTurnMode.FADE to "Fade",
            ReaderPageTurnMode.TAP_ONLY to "Tap Only"
        ).forEach { (mode, label) ->
            FilterChip(
                selected = preset.pageTurnMode == mode,
                onClick = { onUpdatePreset { it.copy(pageTurnMode = mode) } },
                label = { Text(label) },
                modifier = Modifier.testTag("reader_pageturn_${mode.name}")
            )
        }
    }

    ReaderSwitchRow(
        title = "Tap Zones",
        subtitle = "Left edge = Prev • Center = Controls • Right edge = Next",
        checked = preset.tapZonesEnabled,
        testTag = "reader_tap_zones_switch",
        onCheckedChange = { enabled -> onUpdatePreset { it.copy(tapZonesEnabled = enabled) } }
    )

    ReaderSwitchRow(
        title = "Volume Keys Turn Pages",
        subtitle = "Volume Up = previous page, Volume Down = next page",
        checked = preset.volumeKeysTurnPage,
        testTag = "reader_volume_keys_switch",
        onCheckedChange = { enabled -> onUpdatePreset { it.copy(volumeKeysTurnPage = enabled) } }
    )

    ReaderSwitchRow(
        title = "Keep Screen On",
        subtitle = "Prevent sleep while reading this document",
        checked = preset.keepScreenOn,
        testTag = "reader_keep_screen_on_switch",
        onCheckedChange = { enabled -> onUpdatePreset { it.copy(keepScreenOn = enabled) } }
    )

    ReaderSwitchRow(
        title = "Immersive Full-Screen",
        subtitle = "Hide status & navigation bars when controls are hidden",
        checked = preset.immersiveMode,
        testTag = "reader_immersive_switch",
        onCheckedChange = { enabled -> onUpdatePreset { it.copy(immersiveMode = enabled) } }
    )

    Text("Orientation Lock", style = MaterialTheme.typography.labelLarge)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf(
            ReaderOrientationLock.AUTO to "Auto",
            ReaderOrientationLock.PORTRAIT to "Portrait",
            ReaderOrientationLock.LANDSCAPE to "Landscape"
        ).forEachIndexed { idx, (lock, label) ->
            SegmentedButton(
                selected = preset.orientationLock == lock,
                onClick = { onUpdatePreset { it.copy(orientationLock = lock) } },
                shape = SegmentedButtonDefaults.itemShape(idx, 3),
                modifier = Modifier.testTag("reader_orientation_${lock.name}")
            ) {
                Text(label)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ReaderSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

private fun formatHour(hour24: Int): String {
    val h = hour24.coerceIn(0, 23)
    val suffix = if (h < 12) "AM" else "PM"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "$h12 $suffix"
}
