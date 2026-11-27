package com.example.feature.sign

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.security.VaultSignatureEntry

@Composable
fun SignPadSection(
    strokes: List<SignatureStroke>,
    selectedColor: SignaturePenColor,
    strokeWidthDp: Float,
    showGuideLine: Boolean,
    signatureKind: SignatureKind,
    saveForReuse: Boolean,
    vaultUnlocked: Boolean,
    vaultEntries: List<VaultSignatureEntry>,
    onColorSelected: (SignaturePenColor) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onToggleGuideLine: () -> Unit,
    onKindSelected: (SignatureKind) -> Unit,
    onSaveForReuseChange: (Boolean) -> Unit,
    onStrokeCommitted: (SignatureStroke) -> Unit,
    onUndoStroke: () -> Unit,
    onClearPad: () -> Unit,
    onUnlockVault: () -> Unit,
    onPickVaultEntry: (VaultSignatureEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val activePoints = remember { mutableStateListOf<StrokePoint>() }
    var activePointerType by remember { mutableStateOf(PointerType.Touch) }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Saved Vault quick row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.sign_vault_title),
                style = MaterialTheme.typography.titleSmall
            )
            if (!vaultUnlocked) {
                OutlinedButton(
                    onClick = onUnlockVault,
                    modifier = Modifier.testTag("unlock_signature_vault_button")
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Unlock Saved")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${vaultEntries.size} saved",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (vaultUnlocked && vaultEntries.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("vault_signatures_row")
            ) {
                items(vaultEntries, key = { it.metadata.id }) { entry ->
                    val bmp = remember(entry.metadata.id) {
                        BitmapFactory.decodeByteArray(entry.pngBytes, 0, entry.pngBytes.size)?.asImageBitmap()
                    }
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = Color.White,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .size(width = 112.dp, height = 56.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                            .clickable { onPickVaultEntry(entry) }
                            .testTag("vault_entry_${entry.metadata.id}")
                    ) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = entry.metadata.label,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Signature / Initials + Color + Guide controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SignatureKind.entries.forEach { kind ->
                    FilterChip(
                        selected = signatureKind == kind,
                        onClick = { onKindSelected(kind) },
                        label = { Text(kind.label) }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SignaturePenColor.entries.forEach { color ->
                    val selected = color == selectedColor
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(color.composeColor)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(color) }
                            .semantics { contentDescription = "Pen color ${color.label}" }
                            .testTag("pen_color_${color.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Drawing Pad Canvas
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = Color(0xFFFCFCFD),
            tonalElevation = 1.dp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.extraLarge
                )
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("signature_draw_pad")
            ) {
                val widthPx = constraints.maxWidth.coerceAtLeast(1).toFloat()
                val heightPx = constraints.maxHeight.coerceAtLeast(1).toFloat()

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(selectedColor, strokeWidthDp) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    activePoints.clear()
                                    activePoints.add(
                                        StrokePoint(
                                            xFraction = (start.x / widthPx).coerceIn(0f, 1f),
                                            yFraction = (start.y / heightPx).coerceIn(0f, 1f)
                                        )
                                    )
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    activePointerType = change.type
                                    val pressure = change.pressure.takeIf { it > 0f } ?: 1f
                                    activePoints.add(
                                        StrokePoint(
                                            xFraction = (change.position.x / widthPx).coerceIn(0f, 1f),
                                            yFraction = (change.position.y / heightPx).coerceIn(0f, 1f),
                                            pressure = pressure
                                        )
                                    )
                                },
                                onDragEnd = {
                                    if (activePoints.isNotEmpty()) {
                                        val effectiveWidth = if (activePointerType == PointerType.Stylus) {
                                            strokeWidthDp * 0.95f
                                        } else {
                                            strokeWidthDp
                                        }
                                        onStrokeCommitted(
                                            SignatureStroke(
                                                points = activePoints.toList(),
                                                colorArgb = selectedColor.argb,
                                                strokeWidthDp = effectiveWidth
                                            )
                                        )
                                        activePoints.clear()
                                    }
                                },
                                onDragCancel = {
                                    activePoints.clear()
                                }
                            )
                        }
                ) {
                    if (showGuideLine) {
                        val baselineY = size.height * 0.72f
                        drawLine(
                            color = Color(0xFFCBD5E1),
                            start = Offset(28.dp.toPx(), baselineY),
                            end = Offset(size.width - 28.dp.toPx(), baselineY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                        )
                    }

                    val drawSmoothStroke: (List<StrokePoint>, Color, Float) -> Unit = { pts, color, widthDp ->
                        if (pts.size == 1) {
                            val pt = pts.first()
                            drawCircle(
                                color = color,
                                radius = (widthDp.dp.toPx()) / 2f,
                                center = Offset(pt.xFraction * size.width, pt.yFraction * size.height)
                            )
                        } else if (pts.size > 1) {
                            val path = Path()
                            var prevX = pts.first().xFraction * size.width
                            var prevY = pts.first().yFraction * size.height
                            path.moveTo(prevX, prevY)
                            for (i in 1 until pts.size) {
                                val cx = pts[i].xFraction * size.width
                                val cy = pts[i].yFraction * size.height
                                path.quadraticTo(prevX, prevY, (prevX + cx) / 2f, (prevY + cy) / 2f)
                                prevX = cx
                                prevY = cy
                            }
                            path.lineTo(prevX, prevY)
                            drawPath(
                                path = path,
                                color = color,
                                style = Stroke(
                                    width = widthDp.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    strokes.forEach { stroke ->
                        drawSmoothStroke(stroke.points, Color(stroke.colorArgb), stroke.strokeWidthDp)
                    }
                    if (activePoints.isNotEmpty()) {
                        drawSmoothStroke(activePoints, selectedColor.composeColor, strokeWidthDp)
                    }
                }

                if (strokes.isEmpty() && activePoints.isEmpty()) {
                    Text(
                        text = "Sign here with finger or stylus",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        // Width slider + Undo / Clear / Guide + Save for reuse
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Width",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(46.dp)
            )
            Slider(
                value = strokeWidthDp,
                onValueChange = onStrokeWidthChange,
                valueRange = 1.5f..8.0f,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onToggleGuideLine,
                modifier = Modifier.testTag("toggle_guideline_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Straighten,
                    contentDescription = "Toggle guide line",
                    tint = if (showGuideLine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onUndoStroke,
                enabled = strokes.isNotEmpty(),
                modifier = Modifier.testTag("undo_stroke_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo stroke")
            }
            IconButton(
                onClick = onClearPad,
                enabled = strokes.isNotEmpty(),
                modifier = Modifier.testTag("clear_pad_button")
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear pad")
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable { onSaveForReuseChange(!saveForReuse) }
                .padding(horizontal = 4.dp)
        ) {
            Checkbox(
                checked = saveForReuse,
                onCheckedChange = onSaveForReuseChange,
                modifier = Modifier.testTag("save_for_reuse_checkbox")
            )
            Text(
                text = stringResource(R.string.sign_save_for_reuse),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
