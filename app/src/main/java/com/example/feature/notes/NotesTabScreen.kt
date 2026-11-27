package com.example.feature.notes

import android.content.Intent
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.database.entity.NoteType
import com.example.core.ui.components.EmptyStateView
import com.example.ui.theme.LocalIsDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesTabScreen(
    onOpenLibrary: () -> Unit,
    viewModel: NotesViewModel = hiltViewModel()
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterType by viewModel.filterType.collectAsStateWithLifecycle()
    val draft by viewModel.editorDraft.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val partialVoiceText by viewModel.partialVoiceText.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is NotesEvent.ShowMessage -> {
                    val res = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = if (event.undoNote != null) context.getString(R.string.undo) else null,
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed && event.undoNote != null) {
                        viewModel.restoreDeletedNote(event.undoNote)
                    }
                }
                is NotesEvent.ShareText -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, event.title)
                        putExtra(Intent.EXTRA_TEXT, event.body)
                    }
                    context.startActivity(Intent.createChooser(intent, event.title))
                }
                is NotesEvent.SharePdf -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        putExtra(Intent.EXTRA_SUBJECT, event.title)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, event.title))
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.notes_tab_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Notes, checklists, shopping & voice memos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenLibrary,
                        modifier = Modifier.testTag("notes_open_library_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = stringResource(R.string.see_all_library))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.startNewNote(NoteType.TEXT) },
                modifier = Modifier.testTag("notes_fab_new")
            ) {
                Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "New note")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val pinned = remember(notes) { notes.filter { it.isPinned } }
            val unpinned = remember(notes) { notes.filterNot { it.isPinned } }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = { Text(stringResource(R.string.notes_search_hint)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth().testTag("notes_search_input")
                    )
                }

                // Filter chips + Quick create bar
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = filterType == null,
                                onClick = { viewModel.setFilterType(null) },
                                label = { Text("All") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = filterType == NoteType.TEXT,
                                onClick = { viewModel.setFilterType(NoteType.TEXT) },
                                label = { Text("Text") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = filterType == NoteType.CHECKLIST,
                                onClick = { viewModel.setFilterType(NoteType.CHECKLIST) },
                                label = { Text("To-Do") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = filterType == NoteType.SHOPPING,
                                onClick = { viewModel.setFilterType(NoteType.SHOPPING) },
                                label = { Text("Shopping") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = filterType == NoteType.VOICE,
                                onClick = { viewModel.setFilterType(NoteType.VOICE) },
                                label = { Text("Voice") }
                            )
                        }
                    }
                }

                // Quick creation tiles
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            QuickCreateChip(
                                icon = Icons.AutoMirrored.Filled.NoteAdd,
                                label = "+ Note",
                                tag = "quick_new_text_note",
                                onClick = { viewModel.startNewNote(NoteType.TEXT) }
                            )
                        }
                        item {
                            QuickCreateChip(
                                icon = Icons.Default.Checklist,
                                label = "+ To-Do",
                                tag = "quick_new_checklist",
                                onClick = { viewModel.startNewNote(NoteType.CHECKLIST) }
                            )
                        }
                        item {
                            QuickCreateChip(
                                icon = Icons.Default.ShoppingCart,
                                label = "+ Shopping",
                                tag = "quick_new_shopping",
                                onClick = { viewModel.startNewNote(NoteType.SHOPPING) }
                            )
                        }
                        item {
                            QuickCreateChip(
                                icon = Icons.Default.Mic,
                                label = "+ Voice",
                                tag = "quick_new_voice",
                                onClick = { viewModel.startNewNote(NoteType.VOICE) }
                            )
                        }
                    }
                }

                if (notes.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.AutoMirrored.Filled.NoteAdd,
                            title = if (searchQuery.isNotBlank()) "No matching notes" else "Capture notes offline",
                            subtitle = "Create text notes, to-do checklists, shopping lists, or dictate voice memos — stored privately on your device.",
                            primaryActionLabel = "Create Note",
                            onPrimaryAction = { viewModel.startNewNote(NoteType.TEXT) },
                            secondaryActionLabel = "New To-Do List",
                            onSecondaryAction = { viewModel.startNewNote(NoteType.CHECKLIST) }
                        )
                    }
                } else {
                    if (pinned.isNotEmpty()) {
                        item {
                            Text(
                                text = "Pinned",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(pinned, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                onOpen = { viewModel.openExistingNote(note) },
                                onTogglePin = { viewModel.togglePin(note) },
                                onToggleChecklistItem = { itemId -> viewModel.toggleItemChecked(note, itemId) },
                                onShareText = { viewModel.shareNoteText(note) },
                                onExportPdf = { viewModel.exportNoteToPdf(note) },
                                onDelete = { viewModel.deleteNote(note) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                    if (unpinned.isNotEmpty()) {
                        if (pinned.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Notes",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(unpinned, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                onOpen = { viewModel.openExistingNote(note) },
                                onTogglePin = { viewModel.togglePin(note) },
                                onToggleChecklistItem = { itemId -> viewModel.toggleItemChecked(note, itemId) },
                                onShareText = { viewModel.shareNoteText(note) },
                                onExportPdf = { viewModel.exportNoteToPdf(note) },
                                onDelete = { viewModel.deleteNote(note) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }

    draft?.let { currentDraft ->
        NoteEditorSheet(
            draft = currentDraft,
            isListening = isListening,
            partialVoiceText = partialVoiceText,
            onTitleChange = viewModel::updateDraftTitle,
            onContentChange = viewModel::updateDraftContent,
            onColorChange = viewModel::updateDraftColor,
            onTogglePin = viewModel::toggleDraftPinned,
            onAddChecklistItem = { viewModel.addChecklistItem() },
            onUpdateChecklistItem = viewModel::updateChecklistItemText,
            onToggleChecklistItem = viewModel::toggleDraftChecklistItem,
            onRemoveChecklistItem = viewModel::removeDraftChecklistItem,
            onVoiceListeningChange = viewModel::setVoiceListening,
            onVoiceResult = viewModel::appendDictatedText,
            onSave = viewModel::saveDraft,
            onDismiss = viewModel::closeEditor
        )
    }
}

@Composable
private fun QuickCreateChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        label = { Text(label) },
        modifier = Modifier.testTag(tag)
    )
}

@Composable
private fun NoteCard(
    note: NoteModel,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleChecklistItem: (String) -> Unit,
    onShareText: () -> Unit,
    onExportPdf: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val cardColor = when (note.colorKey) {
        "amber" -> if (isDark) Color(0xFF3A2F1C) else Color(0xFFFFF3DC)
        "sage" -> if (isDark) Color(0xFF1D3529) else Color(0xFFE4F7EC)
        "sky" -> if (isDark) Color(0xFF1D2F42) else Color(0xFFE7F3FF)
        "rose" -> if (isDark) Color(0xFF3C2028) else Color(0xFFFFEBF0)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onOpen)
            .testTag("note_card_${note.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onTogglePin) {
                    Icon(
                        imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (note.isPinned) "Unpin" else "Pin",
                        tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (note.content.isNotBlank()) {
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (note.checklistItems.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                note.checklistItems.take(5).forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = item.isChecked,
                            onCheckedChange = { onToggleChecklistItem(item.id) }
                        )
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (note.checklistItems.size > 5) {
                    Text(
                        text = "+${note.checklistItems.size - 5} more items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onShareText) {
                    Icon(Icons.Default.Share, contentDescription = "Share note")
                }
                IconButton(onClick = onExportPdf) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Export note as PDF")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete note")
                }
            }
        }
    }
}
