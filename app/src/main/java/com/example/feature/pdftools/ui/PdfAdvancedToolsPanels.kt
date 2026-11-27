package com.example.feature.pdftools.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.ui.components.RunsOnDeviceBadge
import com.example.feature.pdftools.CorePdfToolUiState
import com.example.feature.pdftools.DigitalSigMode
import com.example.feature.pdftools.engine.PdfPageSizeOption
import kotlin.math.roundToInt

@Composable
fun FillFormConfigurePanel(
    state: CorePdfToolUiState,
    onUpdateAcroText: (String, String) -> Unit,
    onUpdateAcroCheckbox: (String, Boolean) -> Unit,
    onFlattenToggle: (Boolean) -> Unit,
    onAddFreeText: (String, Float, Float, Float) -> Unit,
    onRemoveFreeText: (String) -> Unit,
    onChangePage: (Int) -> Unit
) {
    var draftText by remember { mutableStateOf("") }
    var tapNormX by remember { mutableFloatStateOf(0.15f) }
    var tapNormY by remember { mutableFloatStateOf(0.30f) }
    var fontSize by remember { mutableFloatStateOf(13f) }
    val pageCount = state.sourcePdf?.pageCount ?: 1

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (state.acroFields.isNotEmpty()) {
            item {
                Text(
                    "AcroForm fields (${state.acroFields.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(state.acroFields, key = { it.fullyQualifiedName }) { field ->
                if (field.isCheckbox) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = state.checkboxValues[field.fullyQualifiedName] ?: field.isChecked,
                            onCheckedChange = { onUpdateAcroCheckbox(field.fullyQualifiedName, it) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(field.label, style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    OutlinedTextField(
                        value = state.fieldValues[field.fullyQualifiedName] ?: field.value,
                        onValueChange = { onUpdateAcroText(field.fullyQualifiedName, it) },
                        label = { Text(field.label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Flatten form fields on save", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = state.flattenFormOnSave,
                        onCheckedChange = onFlattenToggle
                    )
                }
            }
        } else {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "No AcroForm fields detected — tap anywhere on the page preview below to place free-text.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Free-text overlay • Page ${state.previewPageIndex + 1}/$pageCount",
                    style = MaterialTheme.typography.titleSmall
                )
                Row {
                    IconButton(
                        onClick = { onChangePage(-1) },
                        enabled = state.previewPageIndex > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous page")
                    }
                    IconButton(
                        onClick = { onChangePage(1) },
                        enabled = state.previewPageIndex < pageCount - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next page")
                    }
                }
            }
        }

        item {
            val bmp = state.previewBitmap
            val aspect = if (bmp != null && bmp.height > 0) bmp.width.toFloat() / bmp.height.toFloat() else 0.77f
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(state.previewPageIndex) {
                        detectTapGestures { offset ->
                            if (size.width > 0 && size.height > 0) {
                                tapNormX = (offset.x / size.width.toFloat()).coerceIn(0.05f, 0.85f)
                                tapNormY = (offset.y / size.height.toFloat()).coerceIn(0.05f, 0.90f)
                            }
                        }
                    }
            ) {
                if (bmp != null && !bmp.isRecycled) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Tap to set text placement",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                val pxW = constraints.maxWidth.toFloat()
                val pxH = constraints.maxHeight.toFloat()
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier.offset {
                        IntOffset((tapNormX * pxW).roundToInt(), (tapNormY * pxH).roundToInt())
                    }
                ) {
                    Text(
                        text = draftText.ifBlank { "Tap target" },
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = draftText,
                onValueChange = { draftText = it },
                label = { Text("Type text to place at target") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("fill_form_freetext_input")
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Size ${fontSize.roundToInt()}pt", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 9f..24f,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        onAddFreeText(draftText, tapNormX, tapNormY, fontSize)
                        draftText = ""
                    },
                    enabled = draftText.isNotBlank(),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("fill_form_add_text_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Place")
                }
            }
        }

        items(state.freeTextOverlays, key = { it.id }) { overlay ->
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(overlay.text, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Page ${overlay.pageIndex + 1} • ${overlay.fontSizePt.roundToInt()}pt",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onRemoveFreeText(overlay.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove text overlay")
                    }
                }
            }
        }
    }
}

@Composable
fun DigitalSignatureConfigurePanel(
    state: CorePdfToolUiState,
    onModeChange: (DigitalSigMode) -> Unit,
    onPickPkcs12: () -> Unit,
    onGeneratePkcs12: (String, String) -> Unit,
    onUpdateInputs: (String?, String?, String?) -> Unit,
    onRunVerify: () -> Unit
) {
    var certCn by remember { mutableStateOf("Alex Rivera") }
    var certPass by remember { mutableStateOf("") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.digital_sig_distinct_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.digitalSigMode == DigitalSigMode.SIGN,
                    onClick = { onModeChange(DigitalSigMode.SIGN) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Default.Key, contentDescription = null) },
                    modifier = Modifier.testTag("dsig_mode_sign")
                ) { Text("Sign with PKCS#12") }

                SegmentedButton(
                    selected = state.digitalSigMode == DigitalSigMode.VERIFY,
                    onClick = { onModeChange(DigitalSigMode.VERIFY) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Default.Verified, contentDescription = null) },
                    modifier = Modifier.testTag("dsig_mode_verify")
                ) { Text("Verify PDF") }
            }
        }

        if (state.digitalSigMode == DigitalSigMode.SIGN) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Step A: PKCS#12 Digital ID (.p12 / .pfx)", style = MaterialTheme.typography.titleSmall)
                        if (state.pkcs12File != null) {
                            Text(
                                "Active ID: ${state.pkcs12DisplayName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedButton(
                            onClick = onPickPkcs12,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text("Import .p12 / .pfx from device")
                        }

                        Text("Or generate a local self-signed ID", style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(
                            value = certCn,
                            onValueChange = { certCn = it },
                            label = { Text("Signer Common Name (CN)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = certPass,
                            onValueChange = { certPass = it },
                            label = { Text("New ID password (4+ chars)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onGeneratePkcs12(certCn, certPass) },
                            enabled = certCn.isNotBlank() && certPass.length >= 4,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("dsig_generate_p12_button")
                        ) {
                            Text("Generate Offline Digital ID")
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.pkcs12Password,
                    onValueChange = { onUpdateInputs(it, null, null) },
                    label = { Text("Keystore password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dsig_password_input")
                )
            }
            item {
                OutlinedTextField(
                    value = state.digitalSigReason,
                    onValueChange = { onUpdateInputs(null, it, null) },
                    label = { Text("Signing reason") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = state.digitalSigLocation,
                    onValueChange = { onUpdateInputs(null, null, it) },
                    label = { Text("Location (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            item {
                OutlinedButton(
                    onClick = onRunVerify,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("dsig_verify_refresh_button")
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Run Cryptographic Verification")
                }
            }

            val results = state.verificationResults
            if (results != null) {
                if (results.isEmpty()) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "No PKCS#7 / CMS digital signature dictionaries were found in this PDF.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(results) { sig ->
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = if (sig.isCryptographicallyValid) {
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = if (sig.isCryptographicallyValid) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (sig.isCryptographicallyValid) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (sig.isCryptographicallyValid) "Signature Valid (Unmodified)" else "Signature Invalid or Tampered",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text("Signer: ${sig.signerName}", style = MaterialTheme.typography.bodySmall)
                                    if (sig.reason.isNotBlank()) {
                                        Text("Reason: ${sig.reason}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (sig.signDate.isNotBlank()) {
                                        Text("Date: ${sig.signDate}", style = MaterialTheme.typography.bodySmall)
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

@Composable
fun PhotosToPdfConfigurePanel(
    state: CorePdfToolUiState,
    onAddGalleryPhotos: () -> Unit,
    onCaptureCameraPhoto: () -> Unit,
    onPageSizeSelected: (PdfPageSizeOption) -> Unit,
    onToggleAutoCrop: (String) -> Unit,
    onSetManualInset: (String, Int) -> Unit,
    onMovePhoto: (Int, Int) -> Unit,
    onRemovePhoto: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onAddGalleryPhotos,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Photos")
            }
            OutlinedButton(
                onClick = onCaptureCameraPhoto,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Camera")
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Page size:", style = MaterialTheme.typography.labelLarge)
            PdfPageSizeOption.entries.forEach { opt ->
                FilterChip(
                    selected = state.pdfPageSize == opt,
                    onClick = { onPageSizeSelected(opt) },
                    label = { Text(opt.label) }
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            itemsIndexed(state.photoPages, key = { _, item -> item.id }) { idx, photo ->
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().animateItem()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${idx + 1}. ${photo.displayName}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onMovePhoto(idx, -1) }, enabled = idx > 0) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move photo up")
                            }
                            IconButton(
                                onClick = { onMovePhoto(idx, 1) },
                                enabled = idx < state.photoPages.lastIndex
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move photo down")
                            }
                            IconButton(onClick = { onRemovePhoto(photo.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove photo")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Auto edge-detect crop", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = photo.autoCropEdges,
                                onCheckedChange = { onToggleAutoCrop(photo.id) }
                            )
                        }
                        Text(
                            "Manual margin trim: ${photo.manualCropInsetPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = photo.manualCropInsetPercent.toFloat(),
                            onValueChange = { onSetManualInset(photo.id, it.roundToInt()) },
                            valueRange = 0f..30f
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CopyTextOcrConfigurePanel(
    state: CorePdfToolUiState,
    onEditableTextChange: (String) -> Unit,
    onCopyText: () -> Unit,
    onShareText: () -> Unit,
    onChangePdfPage: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(state.ocrSourceLabel, style = MaterialTheme.typography.titleSmall)
                RunsOnDeviceBadge()
            }
            if (state.sourcePdf != null && state.sourcePdf.pageCount > 1) {
                Row {
                    IconButton(
                        onClick = { onChangePdfPage(-1) },
                        enabled = state.previewPageIndex > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous page")
                    }
                    IconButton(
                        onClick = { onChangePdfPage(1) },
                        enabled = state.previewPageIndex < state.sourcePdf.pageCount - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next page")
                    }
                }
            }
        }

        if (state.ocrRunning) {
            Box(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(10.dp))
                    Text("Recognizing text on device…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            OutlinedTextField(
                value = state.ocrEditableText,
                onValueChange = onEditableTextChange,
                label = { Text("Recognized text (${state.ocrResult?.lines?.size ?: 0} lines)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("ocr_result_textfield")
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onCopyText,
                    enabled = state.ocrEditableText.isNotBlank(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ocr_copy_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy text")
                }
                OutlinedButton(
                    onClick = onShareText,
                    enabled = state.ocrEditableText.isNotBlank(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("ocr_share_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share text")
                }
            }
        }
    }
}
