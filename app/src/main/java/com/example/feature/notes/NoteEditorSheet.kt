package com.example.feature.notes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.database.entity.NoteType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorSheet(
    draft: NoteEditorDraft,
    isListening: Boolean,
    partialVoiceText: String,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onTogglePin: () -> Unit,
    onAddChecklistItem: () -> Unit,
    onUpdateChecklistItem: (String, String) -> Unit,
    onToggleChecklistItem: (String) -> Unit,
    onRemoveChecklistItem: (String) -> Unit,
    onVoiceListeningChange: (Boolean, String) -> Unit,
    onVoiceResult: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var voiceStatusMessage by remember { mutableStateOf<String?>(null) }

    val recognizerHolder = remember { mutableStateOf<SpeechRecognizer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            recognizerHolder.value?.destroy()
            recognizerHolder.value = null
        }
    }

    val startRecognition: () -> Unit = {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            voiceStatusMessage = "On-device speech recognition is unavailable on this device. Type below instead."
        } else {
            val sr = recognizerHolder.value ?: SpeechRecognizer.createSpeechRecognizer(context).also {
                recognizerHolder.value = it
            }
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    voiceStatusMessage = null
                    onVoiceListeningChange(true, "")
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    onVoiceListeningChange(false, "")
                    voiceStatusMessage = "No speech captured (code $error). Try again or type manually."
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull().orEmpty()
                    onVoiceListeningChange(false, "")
                    if (spoken.isNotBlank()) {
                        onVoiceResult(spoken)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        onVoiceListeningChange(true, partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            sr.startListening(intent)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecognition()
        } else {
            voiceStatusMessage = "Microphone permission denied. You can still type notes manually."
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (draft.noteType) {
                        NoteType.TEXT -> stringResource(R.string.note_type_text)
                        NoteType.CHECKLIST -> stringResource(R.string.note_type_checklist)
                        NoteType.SHOPPING -> stringResource(R.string.note_type_shopping)
                        NoteType.VOICE -> stringResource(R.string.note_type_voice)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.testTag("note_editor_pin_button")
                ) {
                    Icon(
                        imageVector = if (draft.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (draft.isPinned) "Unpin note" else "Pin note",
                        tint = if (draft.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = draft.title,
                onValueChange = onTitleChange,
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("note_editor_title_input")
            )

            // Color tag swatches
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tag:", style = MaterialTheme.typography.labelMedium)
                listOf(
                    "default" to MaterialTheme.colorScheme.surfaceVariant,
                    "amber" to Color(0xFFFFE6B8),
                    "sage" to Color(0xFFCFF2DF),
                    "sky" to Color(0xFFD5E9FF),
                    "rose" to Color(0xFFFFD9E2)
                ).forEach { (key, swatch) ->
                    val selected = draft.colorKey == key
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .clickable { onColorChange(key) }
                    )
                }
            }

            // Voice-to-text dictation bar
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isListening) "Listening offline…" else "Voice-to-text dictation",
                            style = MaterialTheme.typography.labelLarge
                        )
                        FilledTonalButton(
                            onClick = {
                                if (isListening) {
                                    recognizerHolder.value?.stopListening()
                                    onVoiceListeningChange(false, "")
                                } else {
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) startRecognition()
                                    else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("note_voice_dictate_button")
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isListening) stringResource(R.string.voice_stop)
                                else stringResource(R.string.voice_dictate)
                            )
                        }
                    }
                    if (partialVoiceText.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = partialVoiceText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    voiceStatusMessage?.let { msg ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (draft.noteType == NoteType.CHECKLIST || draft.noteType == NoteType.SHOPPING) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    draft.checklistItems.forEachIndexed { idx, item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = item.isChecked,
                                onCheckedChange = { onToggleChecklistItem(item.id) }
                            )
                            OutlinedTextField(
                                value = item.text,
                                onValueChange = { onUpdateChecklistItem(item.id, it) },
                                placeholder = {
                                    Text(
                                        if (draft.noteType == NoteType.SHOPPING) "Grocery item ${idx + 1}"
                                        else "Task ${idx + 1}"
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("checklist_item_input_$idx")
                            )
                            IconButton(onClick = { onRemoveChecklistItem(item.id) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove item")
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = onAddChecklistItem,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("note_add_checklist_item")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (draft.noteType == NoteType.SHOPPING) "Add shopping item" else "Add to-do item")
                    }
                }
            } else {
                OutlinedTextField(
                    value = draft.content,
                    onValueChange = onContentChange,
                    label = { Text("Note") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag("note_editor_content_input")
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("note_editor_save_button")
                ) {
                    Text("Save Note")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
