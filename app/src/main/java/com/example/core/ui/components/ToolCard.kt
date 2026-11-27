package com.example.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.datastore.ToolsViewMode
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.ToolAccentCategory
import com.example.ui.theme.colors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolCard(
    toolId: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    category: ToolAccentCategory,
    viewMode: ToolsViewMode,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val reduceMotion = LocalReduceMotion.current
    val accentColors = category.colors(isDark)
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reduceMotion) 0.96f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "toolCardScale"
    )

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .scale(scale)
            .clip(MaterialTheme.shapes.large)
            .testTag("tool_card_$toolId")
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $subtitle"
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = onLongClick?.let { callback ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        callback()
                    }
                }
            )
    ) {
        when (viewMode) {
            ToolsViewMode.GRID_2 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 132.dp)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = accentColors.container,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = accentColors.accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        if (isFavorite) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Favorite",
                                tint = accentColors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            ToolsViewMode.LIST -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 68.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = accentColors.container,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accentColors.accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isFavorite) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favorite",
                            tint = accentColors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            ToolsViewMode.COMPACT_3 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 104.dp)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = accentColors.container,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = accentColors.accent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        if (isFavorite) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(accentColors.accent)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
