package com.example.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.DocumentCategory
import com.example.domain.model.DocumentCollection
import com.example.domain.model.LibraryDocument
import com.example.domain.model.ReadingProgress
import com.example.ui.theme.surfaceHairlineBorder
import com.example.ui.theme.tactilePressScale
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ContinueReadingCarousel(
    items: List<ContinueReadingItem>,
    onOpenReader: (String) -> Unit
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Continue reading",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.document.id }) { item ->
                val total = item.progress.totalPages.coerceAtLeast(1)
                val fraction = ((item.progress.currentPage + 1).toFloat() / total).coerceIn(0f, 1f)
                val pct = (fraction * 100f).roundToInt()

                val cardInteraction = remember { MutableInteractionSource() }
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    border = surfaceHairlineBorder(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .width(216.dp)
                        .tactilePressScale(cardInteraction)
                        .clip(MaterialTheme.shapes.large)
                        .clickable(
                            interactionSource = cardInteraction,
                            indication = androidx.compose.material3.ripple(),
                            onClick = { onOpenReader(item.document.id) }
                        )
                        .testTag("continue_reading_card_${item.document.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BookCoverBox(
                            document = item.document,
                            modifier = Modifier.size(width = 48.dp, height = 64.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = item.document.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Text(
                                text = "Page ${item.progress.currentPage + 1}/$total • $pct%",
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

@Composable
fun CollectionsFilterRow(
    collections: List<DocumentCollection>,
    selectedCollectionId: String?,
    onSelectCollection: (String?) -> Unit,
    onCreateCollectionClick: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "col_new") {
            AssistChip(
                onClick = onCreateCollectionClick,
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("New collection") },
                modifier = Modifier.testTag("library_new_collection_chip")
            )
        }
        items(collections, key = { it.id }) { col ->
            FilterChip(
                selected = selectedCollectionId == col.id,
                onClick = {
                    onSelectCollection(if (selectedCollectionId == col.id) null else col.id)
                },
                leadingIcon = {
                    Icon(Icons.Default.CollectionsBookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text(col.name) },
                modifier = Modifier.testTag("collection_chip_${col.id}")
            )
        }
    }
}

@Composable
fun LibraryCoverGridCard(
    document: LibraryDocument,
    category: DocumentCategory?,
    progress: ReadingProgress?,
    collections: List<DocumentCollection>,
    isActiveTts: Boolean,
    onOpenClick: () -> Unit,
    onListenClick: () -> Unit,
    onMoveCategoryClick: () -> Unit,
    onEditTagsClick: () -> Unit,
    onAddToCollection: (DocumentCollection) -> Unit,
    onLockVaultClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val cardInteraction = remember { MutableInteractionSource() }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isActiveTts) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        border = surfaceHairlineBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .tactilePressScale(cardInteraction)
            .clip(MaterialTheme.shapes.large)
            .clickable(
                interactionSource = cardInteraction,
                indication = androidx.compose.material3.ripple(),
                onClick = onOpenClick
            )
            .testTag("library_cover_card_${document.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                BookCoverBox(
                    document = document,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f)
                )
                if (category != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(category.colorHex).copy(alpha = 0.90f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (progress != null && progress.totalPages > 0) {
                val frac = ((progress.currentPage + 1).toFloat() / progress.totalPages.coerceAtLeast(1)).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("${document.pageCount}p")
                            if (document.tags.isNotEmpty()) {
                                append(" • ")
                                append(document.tags.take(2).joinToString(" ") { "#$it" })
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Read Aloud (TTS)") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) },
                            onClick = { menuOpen = false; onListenClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to category") },
                            onClick = { menuOpen = false; onMoveCategoryClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit tags") },
                            onClick = { menuOpen = false; onEditTagsClick() }
                        )
                        collections.forEach { col ->
                            DropdownMenuItem(
                                text = { Text("Add to \"${col.name}\"") },
                                leadingIcon = { Icon(Icons.Default.CollectionsBookmark, contentDescription = null) },
                                onClick = { menuOpen = false; onAddToCollection(col) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Lock in Vault") },
                            onClick = { menuOpen = false; onLockVaultClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = { menuOpen = false; onShareClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            onClick = { menuOpen = false; onDeleteClick() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BookCoverBox(
    document: LibraryDocument,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        val thumbFile = document.thumbnailPath?.let { File(it) }
        if (thumbFile != null && thumbFile.exists()) {
            AsyncImage(
                model = thumbFile,
                contentDescription = document.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = if (document.mimeType == "application/pdf") {
                    Icons.Default.PictureAsPdf
                } else {
                    Icons.Default.Description
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCollectionDialog(
    onConfirm: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "New Reading Collection",
                style = MaterialTheme.typography.headlineSmall
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Collection name") },
                placeholder = { Text("e.g., Research, Textbooks, Novels") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("collection_name_input")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onConfirm(name.trim())
                            onDismiss()
                        }
                    },
                    enabled = name.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Create")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
