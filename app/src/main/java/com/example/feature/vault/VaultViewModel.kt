package com.example.feature.vault

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.IoDispatcher
import com.example.core.datastore.PreferencesRepository
import com.example.core.datastore.UserPreferences
import com.example.core.security.VaultCryptoManager
import com.example.data.repository.LibraryRepository
import com.example.domain.model.DocumentCategory
import com.example.domain.model.LibraryDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class VaultUiState(
    val isUnlocked: Boolean = false,
    val isPinConfigured: Boolean = false,
    val pinError: String? = null,
    val lockedDocuments: List<LibraryDocument> = emptyList(),
    val unlockedDocuments: List<LibraryDocument> = emptyList(),
    val categories: List<DocumentCategory> = emptyList(),
    val isBusy: Boolean = false,
    val prefs: UserPreferences = UserPreferences()
)

sealed interface VaultEvent {
    data class ShowMessage(val message: String) : VaultEvent
}

@HiltViewModel
class VaultViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val vaultCryptoManager: VaultCryptoManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val isUnlocked = MutableStateFlow(false)
    private val pinError = MutableStateFlow<String?>(null)
    private val isBusy = MutableStateFlow(false)
    private var backgroundedAtMs: Long? = null

    private val _events = MutableSharedFlow<VaultEvent>()
    val events: SharedFlow<VaultEvent> = _events.asSharedFlow()

    val uiState: StateFlow<VaultUiState> = combine(
        isUnlocked,
        pinError,
        libraryRepository.observeLockedDocuments(),
        libraryRepository.observeDocuments(includeLocked = false),
        libraryRepository.observeCategories(),
        isBusy,
        preferencesRepository.preferences
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val prefs = values[6] as UserPreferences
        VaultUiState(
            isUnlocked = values[0] as Boolean,
            isPinConfigured = !prefs.vaultPinHash.isNullOrBlank(),
            pinError = values[1] as String?,
            lockedDocuments = values[2] as List<LibraryDocument>,
            unlockedDocuments = values[3] as List<LibraryDocument>,
            categories = values[4] as List<DocumentCategory>,
            isBusy = values[5] as Boolean,
            prefs = prefs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VaultUiState()
    )

    fun createOrUpdatePin(pin: String) {
        if (pin.length < 4) {
            pinError.value = "PIN must be at least 4 digits"
            return
        }
        viewModelScope.launch {
            val hash = vaultCryptoManager.hashPin(pin)
            preferencesRepository.setVaultPinHash(hash)
            pinError.value = null
            isUnlocked.value = true
            _events.emit(VaultEvent.ShowMessage("Vault PIN configured and unlocked"))
        }
    }

    fun verifyPin(pin: String) {
        val storedHash = uiState.value.prefs.vaultPinHash
        if (vaultCryptoManager.verifyPin(pin, storedHash)) {
            pinError.value = null
            isUnlocked.value = true
        } else {
            pinError.value = "Incorrect Vault PIN"
        }
    }

    fun onBiometricSuccess() {
        pinError.value = null
        isUnlocked.value = true
    }

    fun lockVault() {
        isUnlocked.value = false
    }

    fun onAppBackgrounded() {
        if (isUnlocked.value) {
            backgroundedAtMs = System.currentTimeMillis()
        }
    }

    fun onAppForegrounded() {
        val bgAt = backgroundedAtMs ?: return
        backgroundedAtMs = null
        val timeoutMin = uiState.value.prefs.vaultAutoLockMinutes
        if (timeoutMin == 0 || (System.currentTimeMillis() - bgAt) >= timeoutMin * 60_000L) {
            isUnlocked.value = false
        }
    }

    fun setAutoLockMinutes(minutes: Int) {
        viewModelScope.launch { preferencesRepository.setVaultAutoLockMinutes(minutes) }
    }

    fun setHideLockedThumbnails(hide: Boolean) {
        viewModelScope.launch { preferencesRepository.setHideLockedThumbnails(hide) }
    }

    fun lockDocument(doc: LibraryDocument) {
        viewModelScope.launch {
            isBusy.value = true
            libraryRepository.moveDocumentToVault(doc.id)
                .onSuccess { _events.emit(VaultEvent.ShowMessage("Encrypted \"${doc.title}\" in Vault")) }
                .onFailure { _events.emit(VaultEvent.ShowMessage(it.localizedMessage ?: "Failed to encrypt document")) }
            isBusy.value = false
        }
    }

    fun unlockDocument(doc: LibraryDocument) {
        viewModelScope.launch {
            isBusy.value = true
            libraryRepository.unlockDocumentFromVault(doc.id, targetCategoryId = "cat_personal")
                .onSuccess { _events.emit(VaultEvent.ShowMessage("Moved \"${doc.title}\" back to Library")) }
                .onFailure { _events.emit(VaultEvent.ShowMessage(it.localizedMessage ?: "Failed to unlock document")) }
            isBusy.value = false
        }
    }

    fun exportEncryptedBackup(destUri: Uri, passphrase: String) {
        viewModelScope.launch {
            isBusy.value = true
            val res = libraryRepository.exportEncryptedBackup(passphrase)
            res.onSuccess { encryptedBytes ->
                val writeRes = withContext(ioDispatcher) {
                    runCatching {
                        context.contentResolver.openOutputStream(destUri)?.use { it.write(encryptedBytes) }
                            ?: error("Unable to open SAF output stream")
                    }
                }
                writeRes.onSuccess {
                    _events.emit(VaultEvent.ShowMessage("Encrypted backup saved (${encryptedBytes.size / 1024} KB)"))
                }.onFailure { err ->
                    _events.emit(VaultEvent.ShowMessage(err.localizedMessage ?: "Failed to write backup"))
                }
            }.onFailure { err ->
                _events.emit(VaultEvent.ShowMessage(err.localizedMessage ?: "Backup failed"))
            }
            isBusy.value = false
        }
    }

    fun restoreEncryptedBackup(sourceUri: Uri, passphrase: String) {
        viewModelScope.launch {
            isBusy.value = true
            val bytesRes = withContext(ioDispatcher) {
                runCatching {
                    context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                        ?: error("Unable to read backup file")
                }
            }
            bytesRes.onSuccess { bytes ->
                libraryRepository.restoreEncryptedBackup(bytes, passphrase)
                    .onSuccess { count ->
                        _events.emit(VaultEvent.ShowMessage("Restored $count item(s) from encrypted backup"))
                    }
                    .onFailure { err ->
                        _events.emit(VaultEvent.ShowMessage(err.localizedMessage ?: "Wrong passphrase or invalid backup"))
                    }
            }.onFailure { err ->
                _events.emit(VaultEvent.ShowMessage(err.localizedMessage ?: "Failed to open backup file"))
            }
            isBusy.value = false
        }
    }
}
