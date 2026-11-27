package com.example.feature.sign

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SignPlacementSection(
    pageBitmap: Bitmap?,
    currentPageIndex: Int,
    pageCount: Int,
    placementsOnPage: List<SignaturePlacement>,
    selectedPlacementId: String?,
    canUndo: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onSelectPlacement: (String) -> Unit,
    onUpdatePlacementBounds: (id: String, x: Float, y: Float, w: Float, h: Float, rot: Float) -> Unit,
    onUpdateStampMeta: (id: String, includeDate: Boolean, signedBy: String) -> Unit,
    onUndoPlacement: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDuplicateSelected: () -> Unit,
    onApplyToAllPages: () -> Unit,
    onAddAnotherSignature: () -> Unit,
    onRequestConfirmPlacement: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pageZoom by remember { mutableFloatStateOf(1f) }
    var pagePanX by remember { mutableFloatStateOf(0f) }
    var pagePanY by remember { mutableFloatStateOf(0f) }
    var showSnapGuideX by remember { mutableStateOf(false) }
    var showSnapGuideY by remember { mutableStateOf(false) }
    var showStampFields by remember { mutableStateOf(false) }

    val selected = placementsOnPage.firstOrNull { it.id == selectedPlacementId }
        ?: placementsOnPage.lastOrNull()

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Page switcher + Zoom reset
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPreviousPage,
                    enabled = currentPageIndex > 0,
                    modifier = Modifier.testTag("sign_prev_page_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                }
                Text(
                    text = "Page ${currentPageIndex + 1} / $pageCount",
                    style = MaterialTheme.typography.titleSmall
                )
                IconButton(
                    onClick = onNextPage,
                    enabled = currentPageIndex < pageCount - 1,
                    modifier = Modifier.testTag("sign_next_page_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(
                    onClick = {
                        if (pageZoom > 1.05f) {
                            pageZoom = 1f
                            pagePanX = 0f
                            pagePanY = 0f
                        } else {
                            pageZoom = 1.8f
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("${(pageZoom * 100).roundToInt()}%") }
                )
                FilledTonalButton(
                    onClick = onRequestConfirmPlacement,
                    enabled = selected != null,
                    modifier = Modifier.testTag("open_confirm_placement_button")
                ) {
                    Text(stringResource(R.string.sign_place_signature))
                }
            }
        }

        // PDF Page Preview + Floating Overlays
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextZoom = (pageZoom * zoom).coerceIn(1f, 3f)
                        pageZoom = nextZoom
                        if (nextZoom > 1.02f) {
                            pagePanX = (pagePanX + pan.x).coerceIn(-600f, 600f)
                            pagePanY = (pagePanY + pan.y).coerceIn(-800f, 800f)
                        } else {
                            pagePanX = 0f
                            pagePanY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val pageRatio = if (pageBitmap != null && pageBitmap.height > 0) {
                pageBitmap.width.toFloat() / pageBitmap.height.toFloat()
            } else {
                612f / 792f
            }

            BoxWithConstraints(
                modifier = Modifier
                    .padding(10.dp)
                    .aspectRatio(pageRatio)
                    .graphicsLayer(
                        scaleX = pageZoom,
                        scaleY = pageZoom,
                        translationX = pagePanX,
                        translationY = pagePanY
                    )
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                val pageWidthPx = constraints.maxWidth.coerceAtLeast(1).toFloat()
                val pageHeightPx = constraints.maxHeight.coerceAtLeast(1).toFloat()
                val density = LocalDensity.current

                if (pageBitmap != null) {
                    Image(
                        bitmap = pageBitmap.asImageBitmap(),
                        contentDescription = "PDF page ${currentPageIndex + 1}",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Smart alignment center guides
                if (showSnapGuideX || showSnapGuideY) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        if (showSnapGuideX) {
                            drawLine(
                                color = Color(0xFF2563EB),
                                start = Offset(size.width / 2f, 0f),
                                end = Offset(size.width / 2f, size.height),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = dash
                            )
                        }
                        if (showSnapGuideY) {
                            drawLine(
                                color = Color(0xFF2563EB),
                                start = Offset(0f, size.height / 2f),
                                end = Offset(size.width, size.height / 2f),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = dash
                            )
                        }
                    }
                }

                placementsOnPage.forEach { placement ->
                    val isSelected = placement.id == selected?.id
                    val sigBitmap = remember(placement.id, placement.pngBytes.size) {
                        BitmapFactory.decodeByteArray(placement.pngBytes, 0, placement.pngBytes.size)?.asImageBitmap()
                    }
                    val boxWidthDp = with(density) { (placement.normalizedWidth * pageWidthPx).toDp() }
                    val boxHeightDp = with(density) { (placement.normalizedHeight * pageHeightPx).toDp() }
                    val offsetX = (placement.normalizedX * pageWidthPx).roundToInt()
                    val offsetY = (placement.normalizedY * pageHeightPx).roundToInt()

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(offsetX, offsetY) }
                            .size(width = boxWidthDp, height = boxHeightDp)
                            .graphicsLayer(rotationZ = placement.rotationDegrees)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = when {
                                    placement.isConfirmed -> Color(0xFF0D9488)
                                    isSelected -> MaterialTheme.colorScheme.secondary
                                    else -> Color(0xFF94A3B8)
                                },
                                shape = RoundedCornerShape(4.dp)
                            )
                            .semantics {
                                contentDescription = "Signature overlay on page ${currentPageIndex + 1}"
                                customActions = listOf(
                                    CustomAccessibilityAction("Move up") {
                                        onUpdatePlacementBounds(
                                            placement.id,
                                            placement.normalizedX,
                                            placement.normalizedY - 0.02f,
                                            placement.normalizedWidth,
                                            placement.normalizedHeight,
                                            placement.rotationDegrees
                                        )
                                        true
                                    },
                                    CustomAccessibilityAction("Move down") {
                                        onUpdatePlacementBounds(
                                            placement.id,
                                            placement.normalizedX,
                                            placement.normalizedY + 0.02f,
                                            placement.normalizedWidth,
                                            placement.normalizedHeight,
                                            placement.rotationDegrees
                                        )
                                        true
                                    },
                                    CustomAccessibilityAction("Move left") {
                                        onUpdatePlacementBounds(
                                            placement.id,
                                            placement.normalizedX - 0.02f,
                                            placement.normalizedY,
                                            placement.normalizedWidth,
                                            placement.normalizedHeight,
                                            placement.rotationDegrees
                                        )
                                        true
                                    },
                                    CustomAccessibilityAction("Move right") {
                                        onUpdatePlacementBounds(
                                            placement.id,
                                            placement.normalizedX + 0.02f,
                                            placement.normalizedY,
                                            placement.normalizedWidth,
                                            placement.normalizedHeight,
                                            placement.rotationDegrees
                                        )
                                        true
                                    },
                                    CustomAccessibilityAction("Increase size") {
                                        val nextW = (placement.normalizedWidth * 1.1f).coerceAtMost(0.9f)
                                        val nextH = (nextW * (pageWidthPx / pageHeightPx)) / placement.aspectRatio
                                        onUpdatePlacementBounds(
                                            placement.id,
                                            placement.normalizedX,
                                            placement.normalizedY,
                                            nextW,
                                            nextH,
                                            placement.rotationDegrees
                                        )
                                        true
                                    },
                                    CustomAccessibilityAction("Decrease size") {
                                        val nextW = (placement.normalizedWidth * 0.9f).coerceAtLeast(0.08f)
                                        val nextH = (nextW * (pageWidthPx / pageHeightPx)) / placement.aspectRatio
                                        onUpdatePlacementBounds(
                                            placement.id,
                                            placement.normalizedX,
                                            placement.normalizedY,
                                            nextW,
                                            nextH,
                                            placement.rotationDegrees
                                        )
                                        true
                                    }
                                )
                            }
                            .pointerInput(placement.id, pageWidthPx, pageHeightPx) {
                                detectDragGestures(
                                    onDragStart = { onSelectPlacement(placement.id) },
                                    onDragEnd = {
                                        showSnapGuideX = false
                                        showSnapGuideY = false
                                    },
                                    onDragCancel = {
                                        showSnapGuideX = false
                                        showSnapGuideY = false
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        var nextX = placement.normalizedX + (dragAmount.x / pageWidthPx)
                                        var nextY = placement.normalizedY + (dragAmount.y / pageHeightPx)

                                        val centerX = nextX + placement.normalizedWidth / 2f
                                        val centerY = nextY + placement.normalizedHeight / 2f
                                        if (abs(centerX - 0.5f) < 0.015f) {
                                            nextX = 0.5f - placement.normalizedWidth / 2f
                                            showSnapGuideX = true
                                        } else {
                                            showSnapGuideX = false
                                        }
                                        if (abs(centerY - 0.5f) < 0.015f) {
                                            nextY = 0.5f - placement.normalizedHeight / 2f
                                            showSnapGuideY = true
                                        } else {
                                            showSnapGuideY = false
                                        }

                                        onUpdatePlacementBounds(
                                            placement.id,
                                            nextX,
                                            nextY,
                                            placement.normalizedWidth,
                                            placement.normalizedHeight,
                                            placement.rotationDegrees
                                        )
                                    }
                                )
                            }
                            .clickable { onSelectPlacement(placement.id) }
                            .testTag("signature_overlay_${placement.id}")
                    ) {
                        if (sigBitmap != null) {
                            Image(
                                bitmap = sigBitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(2.dp)
                            )
                        }

                        // Optional caption preview
                        if (placement.signedByText.isNotBlank() || placement.includeDateStamp) {
                            val caption = buildList {
                                if (placement.signedByText.isNotBlank()) add("Signed by ${placement.signedByText}")
                                if (placement.includeDateStamp) add(placement.dateStampText)
                            }.joinToString(" • ")
                            Text(
                                text = caption,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF374151),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.BottomStart)
                            )
                        }

                        // Locked aspect ratio corner resize handle
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary)
                                    .pointerInput(placement.id, pageWidthPx, pageHeightPx) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val deltaW = dragAmount.x / pageWidthPx
                                            val nextW = (placement.normalizedWidth + deltaW).coerceIn(0.08f, 0.9f)
                                            val nextH = ((nextW * pageWidthPx) / placement.aspectRatio) / pageHeightPx
                                            onUpdatePlacementBounds(
                                                placement.id,
                                                placement.normalizedX,
                                                placement.normalizedY,
                                                nextW,
                                                nextH,
                                                placement.rotationDegrees
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInFull,
                                    contentDescription = "Resize signature",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Fine nudge + Stamp options + Action toolbar
        if (selected != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Nudge arrows
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            onUpdatePlacementBounds(
                                selected.id,
                                selected.normalizedX - 0.01f,
                                selected.normalizedY,
                                selected.normalizedWidth,
                                selected.normalizedHeight,
                                selected.rotationDegrees
                            )
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Nudge left")
                    }
                    IconButton(
                        onClick = {
                            onUpdatePlacementBounds(
                                selected.id,
                                selected.normalizedX,
                                selected.normalizedY - 0.01f,
                                selected.normalizedWidth,
                                selected.normalizedHeight,
                                selected.rotationDegrees
                            )
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Nudge up")
                    }
                    IconButton(
                        onClick = {
                            onUpdatePlacementBounds(
                                selected.id,
                                selected.normalizedX,
                                selected.normalizedY + 0.01f,
                                selected.normalizedWidth,
                                selected.normalizedHeight,
                                selected.rotationDegrees
                            )
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Nudge down")
                    }
                    IconButton(
                        onClick = {
                            onUpdatePlacementBounds(
                                selected.id,
                                selected.normalizedX + 0.01f,
                                selected.normalizedY,
                                selected.normalizedWidth,
                                selected.normalizedHeight,
                                selected.rotationDegrees
                            )
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Nudge right")
                    }
                }

                // Rotate 15° + Stamp toggle
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = {
                            val nextRot = (selected.rotationDegrees + 15f) % 360f
                            onUpdatePlacementBounds(
                                selected.id,
                                selected.normalizedX,
                                selected.normalizedY,
                                selected.normalizedWidth,
                                selected.normalizedHeight,
                                nextRot
                            )
                        }
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate signature")
                    }
                    FilterChip(
                        selected = showStampFields || selected.includeDateStamp || selected.signedByText.isNotBlank(),
                        onClick = { showStampFields = !showStampFields },
                        label = { Text("Stamp") }
                    )
                }
            }

            if (showStampFields) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selected.includeDateStamp,
                            onCheckedChange = { checked ->
                                onUpdateStampMeta(selected.id, checked, selected.signedByText)
                            }
                        )
                        Text("Date", style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedTextField(
                        value = selected.signedByText,
                        onValueChange = { onUpdateStampMeta(selected.id, selected.includeDateStamp, it) },
                        label = { Text("Signed by") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Toolbar: Undo, Delete, Duplicate, Apply to all pages, Add another
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = false,
                    enabled = canUndo,
                    onClick = onUndoPlacement,
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Undo") }
                )
            }
            item {
                FilterChip(
                    selected = false,
                    enabled = selected != null,
                    onClick = onDeleteSelected,
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Delete") }
                )
            }
            item {
                FilterChip(
                    selected = false,
                    enabled = selected != null,
                    onClick = onDuplicateSelected,
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Duplicate") }
                )
            }
            item {
                FilterChip(
                    selected = false,
                    enabled = selected != null && pageCount > 1,
                    onClick = onApplyToAllPages,
                    leadingIcon = { Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("All pages") }
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = onAddAnotherSignature,
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Add another") }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSignaturePlacementSheet(
    pageBitmap: Bitmap?,
    placement: SignaturePlacement,
    onConfirm: () -> Unit,
    onAdjust: () -> Unit,
    onCancel: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onAdjust,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.sign_confirm_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Page ${placement.pageIndex + 1} • Nothing is written to the PDF until you save.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Zoomed preview box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center
            ) {
                val pageBmp = pageBitmap?.asImageBitmap()
                val sigBmp = remember(placement.id) {
                    BitmapFactory.decodeByteArray(placement.pngBytes, 0, placement.pngBytes.size)?.asImageBitmap()
                }
                if (pageBmp != null) {
                    Image(
                        bitmap = pageBmp,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = 1.6f,
                                scaleY = 1.6f,
                                translationX = (0.5f - (placement.normalizedX + placement.normalizedWidth / 2f)) * 220f,
                                translationY = (0.5f - (placement.normalizedY + placement.normalizedHeight / 2f)) * 280f
                            )
                    )
                }
                if (sigBmp != null) {
                    Image(
                        bitmap = sigBmp,
                        contentDescription = "Zoomed signature preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(width = 160.dp, height = 74.dp)
                            .graphicsLayer(rotationZ = placement.rotationDegrees)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_sheet_cancel")
                ) {
                    Text(stringResource(R.string.cancel))
                }
                OutlinedButton(
                    onClick = onAdjust,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("confirm_sheet_adjust")
                ) {
                    Text(stringResource(R.string.sign_adjust_button))
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("confirm_sheet_confirm")
                ) {
                    Text(stringResource(R.string.sign_confirm_button))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
