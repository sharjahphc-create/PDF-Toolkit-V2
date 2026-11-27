package com.example.feature.sign

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.OperationProgress
import com.example.core.security.BiometricSignatureVault
import com.example.core.security.VaultSignatureEntry
import com.example.data.repository.LibraryRepository
import com.example.domain.model.DocumentCategory
import com.example.domain.model.LibraryDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

enum class SignWizardStep {
    PICK,
    DRAW,
    PLACE,
    SAVE_PROGRESS,
    DONE
}

data class SignUiState(
    val step: SignWizardStep = SignWizardStep.PICK,
    val sourceFile: File? = null,
    val sourceDisplayName: String = "",
    val sourceUri: Uri? = null,
    val pdfPassword: String? = null,
    val pageCount: Int = 1,
    val currentPageIndex: Int = 0,
    val currentPageBitmap: Bitmap? = null,
    val passwordPromptFile: File? = null,
    val passwordPromptName: String = "",
    val passwordError: String? = null,
    // Draw pad
    val strokes: List<SignatureStroke> = emptyList(),
    val selectedColor: SignaturePenColor = SignaturePenColor.BLACK,
    val strokeWidthDp: Float = 3.5f,
    val showGuideLine: Boolean = true,
    val signatureKind: SignatureKind = SignatureKind.SIGNATURE,
    val saveForReuse: Boolean = true,
    val vaultUnlocked: Boolean = false,
    val vaultEntries: List<VaultSignatureEntry> = emptyList(),
    // Placement
    val placements: List<SignaturePlacement> = emptyList(),
    val undoStack: List<List<SignaturePlacement>> = emptyList(),
    val selectedPlacementId: String? = null,
    val showConfirmSheet: Boolean = false,
    val showReplaceConfirmSheet: Boolean = false,
    // Save & Done
    val saveProgress: OperationProgress = OperationProgress(),
    val outputFile: File? = null,
    val outputDocumentId: String? = null
)

sealed interface SignEvent {
    data class ShowMessage(val message: String, val allowUndoPlacement: Boolean = false) : SignEvent
    data class LaunchShare(val uri: Uri, val title: String) : SignEvent
}

@HiltViewModel
class SignViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val flattener: PdfSignatureFlattener,
    private val vault: BiometricSignatureVault,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignUiState())
    val uiState: StateFlow<SignUiState> = _uiState.asStateFlow()

    val categories: StateFlow<List<DocumentCategory>> = libraryRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val libraryDocuments: StateFlow<List<LibraryDocument>> = libraryRepository.observeDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<SignEvent>()
    val events: SharedFlow<SignEvent> = _events.asSharedFlow()

    private var saveJob: Job? = null

    fun pickPdfFromUri(uri: Uri) {
        viewModelScope.launch {
            handleOpenResult(flattener.preparePdfFromUri(uri), sourceUri = uri)
        }
    }

    fun pickPdfFromLibrary(doc: LibraryDocument) {
        viewModelScope.launch {
            handleOpenResult(flattener.preparePdfFromFile(File(doc.localCachePath), doc.fileName), sourceUri = null)
        }
    }

    fun pickBundledSample() {
        viewModelScope.launch {
            libraryRepository.importBundledSamplePdf().onSuccess { doc ->
                pickPdfFromLibrary(doc)
            }.onFailure { err ->
                _events.emit(SignEvent.ShowMessage(err.localizedMessage ?: "Failed to load sample PDF"))
            }
        }
    }

    fun submitPdfPassword(password: String) {
        val file = _uiState.value.passwordPromptFile ?: return
        val name = _uiState.value.passwordPromptName
        viewModelScope.launch {
            val result = flattener.preparePdfFromFile(file, name, password)
            if (result is PdfOpenResult.PasswordRequired) {
                _uiState.update { it.copy(passwordError = "Incorrect password. Try again.") }
            } else {
                _uiState.update { it.copy(passwordPromptFile = null, passwordError = null) }
                handleOpenResult(result, sourceUri = _uiState.value.sourceUri)
            }
        }
    }

    fun dismissPasswordPrompt() {
        _uiState.update { it.copy(passwordPromptFile = null, passwordError = null) }
    }

    private suspend fun handleOpenResult(result: PdfOpenResult, sourceUri: Uri?) {
        when (result) {
            is PdfOpenResult.Ready -> {
                _uiState.update {
                    it.copy(
                        step = SignWizardStep.DRAW,
                        sourceFile = result.workingCopy,
                        sourceDisplayName = result.displayName,
                        sourceUri = sourceUri,
                        pdfPassword = result.password,
                        pageCount = result.pageCount,
                        currentPageIndex = 0,
                        placements = emptyList(),
                        undoStack = emptyList()
                    )
                }
                loadPageBitmap(result.workingCopy, 0)
            }
            is PdfOpenResult.PasswordRequired -> {
                _uiState.update {
                    it.copy(
                        passwordPromptFile = result.tempLockedFile,
                        passwordPromptName = result.displayName,
                        passwordError = null
                    )
                }
            }
            is PdfOpenResult.Error -> {
                _events.emit(SignEvent.ShowMessage(result.message))
            }
        }
    }

    private fun loadPageBitmap(file: File, pageIndex: Int) {
        viewModelScope.launch {
            val bmp = flattener.renderPageBitmap(file, pageIndex, targetWidthPx = 1080)
            _uiState.update { it.copy(currentPageBitmap = bmp) }
        }
    }

    fun selectPenColor(color: SignaturePenColor) = _uiState.update { it.copy(selectedColor = color) }
    fun setStrokeWidth(width: Float) = _uiState.update { it.copy(strokeWidthDp = width) }
    fun toggleGuideLine() = _uiState.update { it.copy(showGuideLine = !it.showGuideLine) }
    fun setSignatureKind(kind: SignatureKind) = _uiState.update { it.copy(signatureKind = kind) }
    fun setSaveForReuse(save: Boolean) = _uiState.update { it.copy(saveForReuse = save) }

    fun addStroke(stroke: SignatureStroke) {
        _uiState.update { it.copy(strokes = it.strokes + stroke) }
    }

    fun undoStroke() {
        _uiState.update { state ->
            if (state.strokes.isEmpty()) state else state.copy(strokes = state.strokes.dropLast(1))
        }
    }

    fun clearPad() {
        _uiState.update { it.copy(strokes = emptyList()) }
    }

    fun unlockVault(activity: FragmentActivity) {
        viewModelScope.launch {
            val ok = vault.authenticate(activity)
            if (ok) {
                val entries = vault.loadAllEntries()
                _uiState.update { it.copy(vaultUnlocked = true, vaultEntries = entries) }
            } else {
                _events.emit(SignEvent.ShowMessage("Authentication cancelled"))
            }
        }
    }

    fun useVaultEntry(entry: VaultSignatureEntry) {
        val aspect = SignatureRenderer.decodeAspectRatio(entry.pngBytes)
        val asset = DrawnSignatureAsset(
            id = entry.metadata.id,
            pngBytes = entry.pngBytes,
            aspectRatio = aspect,
            kind = if (entry.metadata.kind == "INITIALS") SignatureKind.INITIALS else SignatureKind.SIGNATURE
        )
        addPlacementAndEnterPlaceStep(asset)
    }

    fun finishDrawingAndProceed() {
        val state = _uiState.value
        val rendered = SignatureRenderer.renderAutoTrimmedPng(state.strokes)
        if (rendered == null) {
            viewModelScope.launch { _events.emit(SignEvent.ShowMessage("Please draw your signature first")) }
            return
        }
        val (pngBytes, aspectRatio) = rendered
        val asset = DrawnSignatureAsset(
            pngBytes = pngBytes,
            aspectRatio = aspectRatio,
            strokes = state.strokes,
            kind = state.signatureKind
        )
        if (state.saveForReuse) {
            viewModelScope.launch {
                val saved = vault.saveSignature(
                    label = "${state.signatureKind.label} ${LocalDate.now()}",
                    kind = state.signatureKind.name,
                    pngBytes = pngBytes
                )
                _uiState.update { current ->
                    current.copy(
                        vaultUnlocked = true,
                        vaultEntries = current.vaultEntries + saved
                    )
                }
            }
        }
        addPlacementAndEnterPlaceStep(asset)
    }

    private fun addPlacementAndEnterPlaceStep(asset: DrawnSignatureAsset) {
        val state = _uiState.value
        val pageBmp = state.currentPageBitmap
        val pageAspect = if (pageBmp != null && pageBmp.height > 0) {
            pageBmp.width.toFloat() / pageBmp.height.toFloat()
        } else 612f / 792f

        val normW = 0.34f
        val normH = ((normW * pageAspect) / asset.aspectRatio).coerceIn(0.05f, 0.45f)
        val placement = SignaturePlacement(
            pageIndex = state.currentPageIndex,
            assetId = asset.id,
            pngBytes = asset.pngBytes,
            normalizedX = (0.5f - normW / 2f).coerceIn(0f, 1f - normW),
            normalizedY = 0.65f.coerceIn(0f, 1f - normH),
            normalizedWidth = normW,
            normalizedHeight = normH,
            aspectRatio = asset.aspectRatio,
            dateStampText = LocalDate.now().toString()
        )
        _uiState.update {
            it.copy(
                step = SignWizardStep.PLACE,
                undoStack = it.undoStack + listOf(it.placements),
                placements = it.placements + placement,
                selectedPlacementId = placement.id
            )
        }
    }

    fun goToPreviousPage() {
        val state = _uiState.value
        if (state.currentPageIndex <= 0) return
        val next = state.currentPageIndex - 1
        _uiState.update { it.copy(currentPageIndex = next) }
        state.sourceFile?.let { loadPageBitmap(it, next) }
    }

    fun goToNextPage() {
        val state = _uiState.value
        if (state.currentPageIndex >= state.pageCount - 1) return
        val next = state.currentPageIndex + 1
        _uiState.update { it.copy(currentPageIndex = next) }
        state.sourceFile?.let { loadPageBitmap(it, next) }
    }

    fun selectPlacement(id: String) {
        _uiState.update { it.copy(selectedPlacementId = id) }
    }

    fun updatePlacementBounds(
        id: String,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        rot: Float
    ) {
        val clamped = PdfCoordinateMapper.clampNormalized(x, y, w, h)
        _uiState.update { state ->
            state.copy(
                placements = state.placements.map {
                    if (it.id == id) {
                        it.copy(
                            normalizedX = clamped.x,
                            normalizedY = clamped.y,
                            normalizedWidth = clamped.width,
                            normalizedHeight = clamped.height,
                            rotationDegrees = rot
                        )
                    } else it
                }
            )
        }
    }

    fun updateStampMeta(id: String, includeDate: Boolean, signedBy: String) {
        _uiState.update { state ->
            state.copy(
                placements = state.placements.map {
                    if (it.id == id) {
                        it.copy(includeDateStamp = includeDate, signedByText = signedBy)
                    } else it
                }
            )
        }
    }

    fun undoPlacementChange() {
        _uiState.update { state ->
            val prev = state.undoStack.lastOrNull() ?: return@update state
            state.copy(
                placements = prev,
                undoStack = state.undoStack.dropLast(1),
                selectedPlacementId = prev.lastOrNull()?.id
            )
        }
    }

    fun deleteSelectedPlacement() {
        val state = _uiState.value
        val targetId = state.selectedPlacementId ?: return
        val next = state.placements.filterNot { it.id == targetId }
        _uiState.update {
            it.copy(
                undoStack = it.undoStack + listOf(it.placements),
                placements = next,
                selectedPlacementId = next.lastOrNull()?.id
            )
        }
    }

    fun duplicateSelectedPlacement() {
        val state = _uiState.value
        val target = state.placements.firstOrNull { it.id == state.selectedPlacementId } ?: return
        val clamped = PdfCoordinateMapper.clampNormalized(
            target.normalizedX + 0.04f,
            target.normalizedY + 0.04f,
            target.normalizedWidth,
            target.normalizedHeight
        )
        val copy = target.copy(
            id = UUID.randomUUID().toString(),
            normalizedX = clamped.x,
            normalizedY = clamped.y,
            isConfirmed = false
        )
        _uiState.update {
            it.copy(
                undoStack = it.undoStack + listOf(it.placements),
                placements = it.placements + copy,
                selectedPlacementId = copy.id
            )
        }
    }

    fun applySelectedToAllPages() {
        val state = _uiState.value
        val target = state.placements.firstOrNull { it.id == state.selectedPlacementId } ?: return
        val newCopies = (0 until state.pageCount)
            .filter { it != target.pageIndex }
            .map { pageIdx ->
                target.copy(
                    id = UUID.randomUUID().toString(),
                    pageIndex = pageIdx,
                    isConfirmed = false
                )
            }
        _uiState.update {
            it.copy(
                undoStack = it.undoStack + listOf(it.placements),
                placements = it.placements + newCopies
            )
        }
        viewModelScope.launch {
            _events.emit(SignEvent.ShowMessage("Applied overlay to all ${state.pageCount} pages"))
        }
    }

    fun addAnotherSignature() {
        _uiState.update { it.copy(step = SignWizardStep.DRAW, strokes = emptyList()) }
    }

    fun openConfirmSheet() {
        _uiState.update { it.copy(showConfirmSheet = true) }
    }

    fun adjustFromConfirmSheet() {
        _uiState.update { it.copy(showConfirmSheet = false) }
    }

    fun cancelFromConfirmSheet() {
        // Cancel writes nothing and removes unconfirmed active overlay
        val state = _uiState.value
        val targetId = state.selectedPlacementId
        val updated = if (targetId != null) {
            state.placements.map {
                if (it.id == targetId) it.copy(isConfirmed = false) else it
            }
        } else state.placements
        _uiState.update {
            it.copy(
                showConfirmSheet = false,
                placements = updated
            )
        }
    }

    fun confirmActivePlacement() {
        val state = _uiState.value
        val targetId = state.selectedPlacementId
        _uiState.update { current ->
            current.copy(
                showConfirmSheet = false,
                undoStack = current.undoStack + listOf(current.placements),
                placements = current.placements.map {
                    if (targetId == null || it.id == targetId || it.pageIndex == current.currentPageIndex) {
                        it.copy(isConfirmed = true)
                    } else it
                }
            )
        }
        viewModelScope.launch {
            _events.emit(SignEvent.ShowMessage("Signature placed", allowUndoPlacement = true))
        }
    }

    fun saveSignedPdf() {
        val state = _uiState.value
        val src = state.sourceFile ?: return
        val confirmed = state.placements.filter { it.isConfirmed }
        if (confirmed.isEmpty()) {
            viewModelScope.launch {
                _events.emit(SignEvent.ShowMessage("Tap 'Place signature' and Confirm before saving"))
            }
            return
        }

        val baseName = state.sourceDisplayName.substringBeforeLast('.').ifBlank { "Document" }
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outFile = File(exportDir, "${baseName}_signed_${System.currentTimeMillis()}.pdf")

        _uiState.update {
            it.copy(
                step = SignWizardStep.SAVE_PROGRESS,
                saveProgress = OperationProgress(0.1f, "Flattening signature into PDF…")
            )
        }

        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            val result = flattener.flattenToNewPdf(
                sourceFile = src,
                outputFile = outFile,
                placements = confirmed,
                password = state.pdfPassword
            ) { pct ->
                _uiState.update {
                    it.copy(saveProgress = OperationProgress(pct, "Flattening page signatures…"))
                }
            }
            result.onSuccess { savedFile ->
                val imported = libraryRepository.importFromUri(
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", savedFile)
                ).getOrNull()
                _uiState.update {
                    it.copy(
                        step = SignWizardStep.DONE,
                        outputFile = savedFile,
                        outputDocumentId = imported?.id
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(step = SignWizardStep.PLACE) }
                _events.emit(SignEvent.ShowMessage(err.localizedMessage ?: "Failed to flatten PDF"))
            }
        }
    }

    fun cancelSave() {
        saveJob?.cancel()
        _uiState.update { it.copy(step = SignWizardStep.PLACE) }
    }

    fun shareOutput() {
        val out = _uiState.value.outputFile ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
        viewModelScope.launch {
            _events.emit(SignEvent.LaunchShare(uri, out.name))
        }
    }

    fun exportOutputToSafUri(destUri: Uri) {
        val out = _uiState.value.outputFile ?: return
        viewModelScope.launch {
            runCatching {
                context.contentResolver.openOutputStream(destUri)?.use { os ->
                    out.inputStream().use { input -> input.copyTo(os) }
                } ?: error("Cannot open destination URI")
            }.onSuccess {
                _events.emit(SignEvent.ShowMessage("Saved signed PDF copy"))
            }.onFailure { err ->
                _events.emit(SignEvent.ShowMessage(err.localizedMessage ?: "Export failed"))
            }
        }
    }

    fun requestReplaceOriginal() {
        _uiState.update { it.copy(showReplaceConfirmSheet = true) }
    }

    fun dismissReplaceOriginal() {
        _uiState.update { it.copy(showReplaceConfirmSheet = false) }
    }

    fun confirmReplaceOriginal() {
        val state = _uiState.value
        val out = state.outputFile ?: return
        val destUri = state.sourceUri
        _uiState.update { it.copy(showReplaceConfirmSheet = false) }
        if (destUri != null) {
            exportOutputToSafUri(destUri)
        } else {
            val src = state.sourceFile
            if (src != null && src.canWrite()) {
                out.copyTo(src, overwrite = true)
                viewModelScope.launch { _events.emit(SignEvent.ShowMessage("Replaced original PDF")) }
            }
        }
    }

    fun assignOutputToCategory(categoryId: String?) {
        val docId = _uiState.value.outputDocumentId ?: return
        viewModelScope.launch {
            libraryRepository.moveToCategory(docId, categoryId)
            _events.emit(SignEvent.ShowMessage("Saved to category"))
        }
    }

    fun stepBackOrExit(onExit: () -> Unit) {
        when (_uiState.value.step) {
            SignWizardStep.PICK -> onExit()
            SignWizardStep.DRAW -> _uiState.update { it.copy(step = SignWizardStep.PICK) }
            SignWizardStep.PLACE -> _uiState.update { it.copy(step = SignWizardStep.DRAW) }
            SignWizardStep.SAVE_PROGRESS -> cancelSave()
            SignWizardStep.DONE -> onExit()
        }
    }

    override fun onCleared() {
        super.onCleared()
        flattener.clearCache()
    }
}
