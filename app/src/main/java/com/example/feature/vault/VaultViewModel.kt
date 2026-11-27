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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class VaultCategorySummary(
    val id: String?,
    val name: String,
    val iconName: String,
    val colorHex: Long,
    val documentCount: Int,
    val storageBytes: Long
)

data class VaultUiState(
    val isUnlocked: Boolean = false,
    val isPinConfigured: Boolean = false,
    val pinError: String? = null,
    val lockedDocuments: List<LibraryDocument> = emptyList(),
    val filteredLockedDocuments: List<LibraryDocument> = emptyList(),
    val unlockedDocuments: List<LibraryDocument> = emptyList(),
    val categories: List<DocumentCategory> = emptyList(),
    val lockedCategories: List<DocumentCategory> = emptyList(),
    val categorySummaries: List<VaultCategorySummary> = emptyList(),
    val selectedCategoryFilterId: String? = null,
    val totalVaultStorageBytes: Long = 0L,
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
    private val selectedCategoryFilterId = MutableStateFlow<String?>(null)
    private var backgroundedAtMs: Long? = null

    private val _events = MutableSharedFlow<VaultEvent>()
    val events: SharedFlow<VaultEvent> = _events.asSharedFlow()

    private val authAndFilterFlow = combine(
        isUnlocked,
        pinError,
        isBusy,
        selectedCategoryFilterId
    ) { unlocked, err, busy, catFilter ->
        VaultHeaderState(unlocked, err, busy, catFilter)
    }

    val uiState: StateFlow<VaultUiState> = combine(
        authAndFilterFlow,
        libraryRepository.observeLockedDocuments(),
        libraryRepository.observeDocuments(includeLocked = false),
        libraryRepository.observeCategories(),
        preferencesRepository.preferences
    ) { header, lockedDocs, unlockedDocs, categories, prefs ->
        val lockedCategories = categories.filter { it.isLocked }
        val lockedCatIds = lockedCategories.map { it.id }.toSet()

        fun docSize(doc: LibraryDocument): Long {
            val f = File(doc.localCachePath)
            return if (f.exists()) f.length() else doc.fileSizeBytes
        }

        val summaries = buildList {
            // General Vault card for locked docs not assigned to a locked category
            val uncategorized = lockedDocs.filter { it.categoryId == null || it.categoryId !in lockedCatIds }
            add(
                VaultCategorySummary(
                    id = GENERAL_VAULT_FILTER_ID,
                    name = "General Vault",
                    iconName = "lock",
                    colorHex = 0xFF475569L,
                    documentCount = uncategorized.size,
                    storageBytes = uncategorized.sumOf(::docSize)
                )
            )
            lockedCategories.forEach { cat ->
                val matching = lockedDocs.filter { it.categoryId == cat.id }
                add(
                    VaultCategorySummary(
                        id = cat.id,
                        name = cat.name,
                        iconName = cat.iconName,
                        colorHex = cat.colorHex,
                        documentCount = matching.size,
                        storageBytes = matching.sumOf(::docSize)
                    )
                )
            }
        }

        val filteredDocs = when (val filterId = header.selectedCategoryFilterId) {
            null -> lockedDocs
            GENERAL_VAULT_FILTER_ID -> lockedDocs.filter { it.categoryId == null || it.categoryId !in lockedCatIds }
            else -> lockedDocs.filter { it.categoryId == filterId }
        }

        VaultUiState(
            isUnlocked = header.isUnlocked,
            isPinConfigured = !prefs.vaultPinHash.isNullOrBlank(),
            pinError = header.pinError,
            lockedDocuments = lockedDocs,
            filteredLockedDocuments = filteredDocs,
            unlockedDocuments = unlockedDocs,
            categories = categories,
            lockedCategories = lockedCategories,
            categorySummaries = summaries,
            selectedCategoryFilterId = header.selectedCategoryFilterId,
            totalVaultStorageBytes = lockedDocs.sumOf(::docSize),
            isBusy = header.isBusy,
            prefs = prefs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VaultUiState()
    )

    fun selectCategoryFilter(filterId: String?) {
        selectedCategoryFilterId.value = if (selectedCategoryFilterId.value == filterId) null else filterId
    }

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

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAppLockEnabled(enabled)
            _events.emit(
                VaultEvent.ShowMessage(
                    if (enabled) "Whole-app lock enabled" else "Whole-app lock disabled"
                )
            )
        }
    }

    fun createLockedCategory(name: String, iconName: String, colorHex: Long) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val created = libraryRepository.createCategory(
                name = clean,
                iconName = iconName,
                colorHex = colorHex,
                isLocked = true
            )
            selectedCategoryFilterId.value = created.id
            _events.emit(VaultEvent.ShowMessage("Created locked category \"${created.name}\""))
        }
    }

    fun lockDocument(doc: LibraryDocument, targetCategoryId: String? = null) {
        viewModelScope.launch {
            isBusy.value = true
            val resolvedCatId = targetCategoryId
                ?: selectedCategoryFilterId.value?.takeUnless { it == GENERAL_VAULT_FILTER_ID }
            libraryRepository.moveDocumentToVault(doc.id, targetCategoryId = resolvedCatId)
                .onSuccess { _events.emit(VaultEvent.ShowMessage("Encrypted \"${doc.title}\" in Vault")) }
                .onFailure { _events.emit(VaultEvent.ShowMessage(it.localizedMessage ?: "Failed to encrypt document")) }
            isBusy.value = false
        }
    }

    fun importFromDeviceToVault(uri: Uri, categoryId: String?, deleteOriginal: Boolean) {
        viewModelScope.launch {
            isBusy.value = true
            val result = libraryRepository.importFromUriWithMode(
                uri = uri,
                categoryId = categoryId,
                deleteOriginal = deleteOriginal,
                lockInVault = true
            )
            isBusy.value = false
            result.onSuccess { res ->
                val msg = when {
                    deleteOriginal && res.originalDeleted ->
                        "Moved & encrypted \"${res.document.title}\" in Vault (original deleted)"
                    deleteOriginal ->
                        "Encrypted \"${res.document.title}\" in Vault (provider blocked deleting original)"
                    else ->
                        "Encrypted \"${res.document.title}\" in Vault"
                }
                _events.emit(VaultEvent.ShowMessage(msg))
            }.onFailure { err ->
                _events.emit(VaultEvent.ShowMessage(err.localizedMessage ?: "Failed to import into Vault"))
            }
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

    fun exportEncryptedBackup(
        destUri: Uri,
        passphrase: String,
        includeVaultFiles: Boolean = true
    ) {
        viewModelScope.launch {
            isBusy.value = true
            val res = libraryRepository.exportEncryptedBackup(passphrase, includeVaultFiles)
            res.onSuccess { encryptedBytes ->
                val writeRes = withContext(ioDispatcher) {
                    runCatching {
                        context.contentResolver.openOutputStream(destUri)?.use { it.write(encryptedBytes) }
                            ?: error("Unable to open SAF output stream")
                    }
                }
                writeRes.onSuccess {
                    val scopeLabel = if (includeVaultFiles) "including Vault files" else "excluding Vault files"
                    _events.emit(
                        VaultEvent.ShowMessage(
                            "Encrypted backup saved (${(encryptedBytes.size / 1024).coerceAtLeast(1)} KB, $scopeLabel)"
                        )
                    )
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
                        _events.emit(VaultEvent.ShowMessage("Restored $count item(s) & settings from backup"))
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

    private data class VaultHeaderState(
        val isUnlocked: Boolean,
        val pinError: String?,
        val isBusy: Boolean,
        val selectedCategoryFilterId: String?
    )

    companion object {
        const val GENERAL_VAULT_FILTER_ID = "__general_vault__"
    }
}
