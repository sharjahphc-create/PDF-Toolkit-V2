package com.example.system

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot in-memory bridge that passes incoming ACTION_SEND / ACTION_SEND_MULTIPLE
 * image URIs and plain text payloads from MainActivity into Photos -> PDF and Notes.
 */
@Singleton
class SharedIntentBridge @Inject constructor() {

    private val _pendingSharedImages = MutableStateFlow<List<Uri>>(emptyList())
    val pendingSharedImages: StateFlow<List<Uri>> = _pendingSharedImages.asStateFlow()

    private val _pendingSharedText = MutableStateFlow<String?>(null)
    val pendingSharedText: StateFlow<String?> = _pendingSharedText.asStateFlow()

    fun enqueueSharedImages(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            _pendingSharedImages.value = uris
        }
    }

    fun consumeSharedImages(): List<Uri> {
        val current = _pendingSharedImages.value
        if (current.isNotEmpty()) {
            _pendingSharedImages.value = emptyList()
        }
        return current
    }

    fun enqueueSharedText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            _pendingSharedText.value = trimmed
        }
    }

    fun consumeSharedText(): String? {
        val current = _pendingSharedText.value
        if (current != null) {
            _pendingSharedText.value = null
        }
        return current
    }
}
