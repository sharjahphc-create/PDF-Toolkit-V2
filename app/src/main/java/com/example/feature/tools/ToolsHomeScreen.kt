package com.example.feature.tools

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.datastore.ToolsSortMode
import com.example.core.datastore.ToolsViewMode
import com.example.core.ui.components.ConfirmActionBottomSheet
import com.example.core.ui.components.EmptyStateView
import com.example.core.ui.components.RunsOnDeviceBadge
import com.example.core.ui.components.ToolCard
import com.example.domain.model.LibraryDocument
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.colors
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsHomeRoute(
    widthSizeClass: WindowWidthSizeClass,
    onOpenTool: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenDocument: (String) -> Unit = {},
    viewModel: ToolsHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val reduceMotion = LocalReduceMotion.current

    var favoritePromptTool by remember { mutableStateOf<ToolItemUi?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val columnCount = when (uiState.viewMode) {
        ToolsViewMode.GRID_2 -> when (widthSizeClass) {
            WindowWidthSizeClass.Compact -> 2
            WindowWidthSizeClass.Medium -> 3
            else -> 4
        }
        ToolsViewMode.LIST -> when (widthSizeClass) {
            WindowWidthSizeClass.Expanded -> 2
            else -> 1
        }
        ToolsViewMode.COMPACT_3 -> when (widthSizeClass) {
            WindowWidthSizeClass.Compact -> 3
            WindowWidthSizeClass.Medium -> 4
            else -> 5
        }
    }

    val gridState = rememberLazyGridState()
    val pdfHeaderKey = "header_pdf_tools"
    val docsHeaderKey = "header_documents_notes"

    val stickyHeaderTitle by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val visibleKeys = layoutInfo.visibleItemsInfo
            val firstIdx = gridState.firstVisibleItemIndex
            val pdfHeaderIdx = visibleKeys.firstOrNull { it.key == pdfHeaderKey }?.index
            val docsHeaderIdx = visibleKeys.firstOrNull { it.key == docsHeaderKey }?.index
            when {
                docsHeaderIdx != null && firstIdx >= docsHeaderIdx -> context.getString(R.string.section_documents_notes)
                pdfHeaderIdx != null && firstIdx >= pdfHeaderIdx -> context.getString(R.string.section_pdf_tools)
                firstIdx > 3 && uiState.documentTools.isNotEmpty() && uiState.pdfTools.isEmpty() ->
                    context.getString(R.string.section_documents_notes)
                firstIdx > 3 -> context.getString(R.string.section_pdf_tools)
                else -> null
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.tools_home_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        RunsOnDeviceBadge()
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 980.dp)
            ) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columnCount),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("tools_home_grid")
                ) {
                    // Search Bar + View Mode Toggle
                    item(key = "search_and_mode", span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = uiState.searchQuery,
                                    onValueChange = viewModel::onSearchQueryChange,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("tools_search_input"),
                                    placeholder = { Text(stringResource(R.string.tools_search_hint)) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (uiState.searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.large
                                )

                                val modeDescription = when (uiState.viewMode) {
                                    ToolsViewMode.GRID_2 -> stringResource(R.string.view_mode_grid)
                                    ToolsViewMode.LIST -> stringResource(R.string.view_mode_list)
                                    ToolsViewMode.COMPACT_3 -> stringResource(R.string.view_mode_compact)
                                }

                                FilledTonalIconButton(
                                    onClick = {
                                        val nextMode = viewModel.cycleViewMode()
                                        val announcement = when (nextMode) {
                                            ToolsViewMode.GRID_2 -> context.getString(R.string.view_mode_grid)
                                            ToolsViewMode.LIST -> context.getString(R.string.view_mode_list)
                                            ToolsViewMode.COMPACT_3 -> context.getString(R.string.view_mode_compact)
                                        }
                                        announceToTalkBack(view, announcement)
                                    },
                                    modifier = Modifier
                                        .size(52.dp)
                                        .testTag("view_mode_toggle_button")
                                        .semantics { contentDescription = "Switch layout. Current: $modeDescription" }
                                ) {
                                    Icon(
                                        imageVector = when (uiState.viewMode) {
                                            ToolsViewMode.GRID_2 -> Icons.Default.GridView
                                            ToolsViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
                                            ToolsViewMode.COMPACT_3 -> Icons.Default.Apps
                                        },
                                        contentDescription = null
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${uiState.pdfTools.size + uiState.documentTools.size} offline tools",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box {
                                    AssistChip(
                                        onClick = { sortMenuExpanded = true },
                                        modifier = Modifier.testTag("tools_sort_chip"),
                                        label = {
                                            Text(
                                                when (uiState.sortMode) {
                                                    ToolsSortMode.DEFAULT -> stringResource(R.string.sort_default)
                                                    ToolsSortMode.A_TO_Z -> stringResource(R.string.sort_a_to_z)
                                                    ToolsSortMode.MOST_USED -> stringResource(R.string.sort_most_used)
                                                }
                                            )
                                        }
                                    )
                                    DropdownMenu(
                                        expanded = sortMenuExpanded,
                                        onDismissRequest = { sortMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sort_default)) },
                                            onClick = {
                                                viewModel.setSortMode(ToolsSortMode.DEFAULT)
                                                sortMenuExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sort_a_to_z)) },
                                            onClick = {
                                                viewModel.setSortMode(ToolsSortMode.A_TO_Z)
                                                sortMenuExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.sort_most_used)) },
                                            onClick = {
                                                viewModel.setSortMode(ToolsSortMode.MOST_USED)
                                                sortMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Recent files carousel
                    if (uiState.searchQuery.isBlank()) {
                        item(key = "recent_files_carousel", span = { GridItemSpan(maxLineSpan) }) {
                            RecentFilesCarousel(
                                documents = uiState.recentDocuments,
                                onOpenLibrary = onOpenLibrary,
                                onOpenDocument = onOpenDocument,
                                onLoadSample = viewModel::loadSamplePdf
                            )
                        }

                        // Customizable Favorites row
                        if (uiState.favoriteTools.isNotEmpty()) {
                            item(key = "favorites_row", span = { GridItemSpan(maxLineSpan) }) {
                                FavoritesCarouselSection(
                                    favorites = uiState.favoriteTools,
                                    onToolClick = { item ->
                                        viewModel.recordToolLaunch(item.definition.id)
                                        onOpenTool(item.definition.id)
                                    },
                                    onToolLongClick = { item -> favoritePromptTool = item },
                                    onMoveFavorite = viewModel::moveFavorite
                                )
                            }
                        }
                    }

                    // Section: PDF tools
                    if (uiState.pdfTools.isNotEmpty()) {
                        item(key = pdfHeaderKey, span = { GridItemSpan(maxLineSpan) }) {
                            ToolSectionHeader(title = stringResource(R.string.section_pdf_tools))
                        }
                        items(
                            items = uiState.pdfTools,
                            key = { "pdf_${it.definition.id}" }
                        ) { item ->
                            ToolCard(
                                toolId = item.definition.id,
                                title = item.definition.title,
                                subtitle = item.definition.subtitle,
                                icon = item.definition.icon,
                                category = item.definition.accentCategory,
                                viewMode = uiState.viewMode,
                                isFavorite = item.isFavorite,
                                onClick = {
                                    viewModel.recordToolLaunch(item.definition.id)
                                    onOpenTool(item.definition.id)
                                },
                                onLongClick = { favoritePromptTool = item },
                                modifier = if (reduceMotion) Modifier else Modifier.animateItem()
                            )
                        }
                    }

                    // Section: Documents & notes
                    if (uiState.documentTools.isNotEmpty()) {
                        item(key = docsHeaderKey, span = { GridItemSpan(maxLineSpan) }) {
                            ToolSectionHeader(title = stringResource(R.string.section_documents_notes))
                        }
                        items(
                            items = uiState.documentTools,
                            key = { "doc_${it.definition.id}" }
                        ) { item ->
                            ToolCard(
                                toolId = item.definition.id,
                                title = item.definition.title,
                                subtitle = item.definition.subtitle,
                                icon = item.definition.icon,
                                category = item.definition.accentCategory,
                                viewMode = uiState.viewMode,
                                isFavorite = item.isFavorite,
                                onClick = {
                                    viewModel.recordToolLaunch(item.definition.id)
                                    onOpenTool(item.definition.id)
                                },
                                onLongClick = { favoritePromptTool = item },
                                modifier = if (reduceMotion) Modifier else Modifier.animateItem()
                            )
                        }
                    }

                    if (uiState.pdfTools.isEmpty() && uiState.documentTools.isEmpty()) {
                        item(key = "no_tools_match", span = { GridItemSpan(maxLineSpan) }) {
                            EmptyStateView(
                                icon = Icons.Default.Search,
                                title = "No matching tools",
                                subtitle = "Try searching for Sign, Compress, Merge, Word, or Notes.",
                                primaryActionLabel = "Clear search",
                                onPrimaryAction = { viewModel.onSearchQueryChange("") }
                            )
                        }
                    }
                }

                // Sticky section header overlay
                AnimatedVisibility(
                    visible = stickyHeaderTitle != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    stickyHeaderTitle?.let { header ->
                        Surface(
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = header,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    favoritePromptTool?.let { target ->
        ConfirmActionBottomSheet(
            title = target.definition.title,
            message = if (target.isFavorite) {
                "Remove ${target.definition.title} from your quick-access Favorites row?"
            } else {
                "Pin ${target.definition.title} to your Favorites row? You can long-press and drag items there to reorder."
            },
            confirmLabel = if (target.isFavorite) {
                stringResource(R.string.unpin_from_favorites)
            } else {
                stringResource(R.string.pin_to_favorites)
            },
            onConfirm = { viewModel.toggleFavorite(target) },
            onDismiss = { favoritePromptTool = null }
        )
    }
}

@Composable
private fun ToolSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun RecentFilesCarousel(
    documents: List<LibraryDocument>,
    onOpenLibrary: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onLoadSample: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.recent_files_header),
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(
                onClick = onOpenLibrary,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("open_library_button")
            ) {
                Text(stringResource(R.string.see_all_library))
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (documents.isEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "No recent files yet",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Import PDFs or load the sample offline guide.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = onLoadSample,
                        label = { Text(stringResource(R.string.import_sample)) },
                        modifier = Modifier.testTag("recent_load_sample_chip")
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(documents, key = { _, doc -> doc.id }) { _, doc ->
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .width(148.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onOpenDocument(doc.id) }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(78.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                val thumb = doc.thumbnailPath?.let { File(it) }
                                if (thumb != null && thumb.exists()) {
                                    AsyncImage(
                                        model = thumb,
                                        contentDescription = doc.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (doc.mimeType == "application/pdf") {
                                            Icons.Default.PictureAsPdf
                                        } else {
                                            Icons.Default.Description
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = doc.title,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${doc.pageCount} pages",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoritesCarouselSection(
    favorites: List<ToolItemUi>,
    onToolClick: (ToolItemUi) -> Unit,
    onToolLongClick: (ToolItemUi) -> Unit,
    onMoveFavorite: (Int, Int) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val cardStepPx = with(density) { 148.dp.toPx() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.favorites_header),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.favorites_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.testTag("favorites_row_list")
        ) {
            itemsIndexed(favorites, key = { _, item -> "fav_${item.definition.id}" }) { index, item ->
                val accent = item.definition.accentCategory.colors(isDark)
                var dragOffsetX by remember(item.definition.id) { mutableFloatStateOf(0f) }

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = accent.container.copy(alpha = 0.72f),
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .width(140.dp)
                        .offset { IntOffset(dragOffsetX.roundToInt(), 0) }
                        .clip(MaterialTheme.shapes.large)
                        .testTag("favorite_chip_${item.definition.id}")
                        .pointerInput(index, favorites.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = {
                                    val deltaIndex = (dragOffsetX / cardStepPx).roundToInt()
                                    if (deltaIndex != 0) {
                                        val targetIdx = (index + deltaIndex).coerceIn(0, favorites.lastIndex)
                                        onMoveFavorite(index, targetIdx)
                                    }
                                    dragOffsetX = 0f
                                },
                                onDragCancel = { dragOffsetX = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetX += dragAmount.x
                                }
                            )
                        }
                        .combinedClickable(
                            onClick = { onToolClick(item) },
                            onLongClick = { onToolLongClick(item) }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = item.definition.icon,
                            contentDescription = null,
                            tint = accent.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = item.definition.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = accent.onContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.DragIndicator,
                            contentDescription = "Reorder",
                            tint = accent.onContainer.copy(alpha = 0.55f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun announceToTalkBack(view: android.view.View, message: String) {
    val manager = view.context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    if (manager?.isEnabled == true) {
        view.announceForAccessibility(message)
    }
}
