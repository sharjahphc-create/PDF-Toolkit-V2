package com.example.feature.pdftools

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.IoDispatcher
import com.example.core.common.OperationProgress
import com.example.core.ui.components.ToolStep
import com.example.data.repository.LibraryRepository
import com.example.data.repository.ToolUsageRepository
import com.example.domain.model.DocumentCategory
import com.example.domain.model.LibraryDocument
import com.example.feature.pdftools.engine.AcroFormFieldItem
import com.example.feature.pdftools.engine.CompressionPreset
import com.example.feature.pdftools.engine.FreeTextOverlay
import com.example.feature.pdftools.engine.OcrExtractionResult
import com.example.feature.pdftools.engine.PageOrganizeSpec
import com.example.feature.pdftools.engine.PdfCoreEngine
import com.example.feature.pdftools.engine.PdfCryptoAndImageEngine
import com.example.feature.pdftools.engine.PdfPageSizeOption
import com.example.feature.pdftools.engine.PhotoPageConfig
import com.example.feature.pdftools.engine.SignatureVerificationInfo
import com.example.feature.sign.PdfOpenResult
import com.example.feature.sign.PdfSignatureFlattener
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

data class PickedPdfSource(
    val id: String = UUID.randomUUID().toString(),
    val file: File,
    val displayName: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val sourceUri: Uri? = null
)

enum class DigitalSigMode { SIGN, VERIFY }
enum class PasswordToolMode { LOCK_AES256, UNLOCK }

data class CorePdfToolUiState(
    val toolId: String = "",
    val step: ToolStep = ToolStep.PICK,
    // Single-PDF tools
    val sourcePdf: PickedPdfSource? = null,
    val passwordPromptFile: File? = null,
    val passwordPromptName: String = "",
    val passwordPromptError: String? = null,
    val pendingUriForPassword: Uri? = null,
    // Lazy page thumbnails
    val thumbnails: Map<Int, Bitmap> = emptyMap(),
    val previewPageIndex: Int = 0,
    val previewBitmap: Bitmap? = null,
    // Merge
    val mergeSources: List<PickedPdfSource> = emptyList(),
    // Split
    val splitRangeInput: String = "1",
    val selectedSplitPages: Set<Int> = setOf(0),
    // Compress
    val compressionPreset: CompressionPreset = CompressionPreset.BALANCED,
    val beforeSizeBytes: Long = 0L,
    val afterSizeBytes: Long = 0L,
    // Organize pages
    val organizeSpecs: List<PageOrganizeSpec> = emptyList(),
    // Fill a form
    val acroFields: List<AcroFormFieldItem> = emptyList(),
    val fieldValues: Map<String, String> = emptyMap(),
    val checkboxValues: Map<String, Boolean> = emptyMap(),
    val freeTextOverlays: List<FreeTextOverlay> = emptyList(),
    val flattenFormOnSave: Boolean = true,
    // Digital signature (PKCS#12)
    val digitalSigMode: DigitalSigMode = DigitalSigMode.SIGN,
    val pkcs12File: File? = null,
    val pkcs12DisplayName: String = "",
    val pkcs12Password: String = "",
    val digitalSigReason: String = "Approved",
    val digitalSigLocation: String = "",
    val generatedCertCommonName: String = "",
    val verificationResults: List<SignatureVerificationInfo>? = null,
    // Password (AES-256 Lock / Unlock)
    val passwordMode: PasswordToolMode = PasswordToolMode.LOCK_AES256,
    val lockUserPassword: String = "",
    val lockOwnerPassword: String = "",
    val allowPrinting: Boolean = true,
    val allowCopy: Boolean = false,
    val unlockPasswordInput: String = "",
    // Photos -> PDF
    val photoPages: List<PhotoPageConfig> = emptyList(),
    val pdfPageSize: PdfPageSizeOption = PdfPageSizeOption.A4,
    // Copy text (OCR)
    val ocrSourceImageFile: File? = null,
    val ocrSourceLabel: String = "",
    val ocrRunning: Boolean = false,
    val ocrResult: OcrExtractionResult? = null,
    val ocrEditableText: String = "",
    // Process & Done
    val progress: OperationProgress = OperationProgress(),
    val outputFile: File? = null,
    val outputDocumentId: String? = null,
    val summaryMetrics: List<Pair<String, String>> = emptyList(),
    val showReplaceOriginalSheet: Boolean = false
)

sealed interface CorePdfToolEvent {
    data class ShowMessage(val message: String) : CorePdfToolEvent
    data class SharePdf(val uri: Uri, val title: String) : CorePdfToolEvent
    data class ShareText(val text: String, val title: String) : CorePdfToolEvent
    data class CopyToClipboard(val text: String) : CorePdfToolEvent
}

@HiltViewModel
class CorePdfToolsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val coreEngine: PdfCoreEngine,
    private val cryptoImageEngine: PdfCryptoAndImageEngine,
    private val flattener: PdfSignatureFlattener,
    private val libraryRepository: LibraryRepository,
    private val toolUsageRepository: ToolUsageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CorePdfToolUiState())
    val uiState: StateFlow<CorePdfToolUiState> = _uiState.asStateFlow()

    val categories: StateFlow<List<DocumentCategory>> = libraryRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val libraryDocuments: StateFlow<List<LibraryDocument>> = libraryRepository.observeDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<CorePdfToolEvent>()
    val events: SharedFlow<CorePdfToolEvent> = _events.asSharedFlow()

    private var activeJob: Job? = null
    private var pendingCameraFile: File? = null

    fun initTool(toolId: String) {
        if (_uiState.value.toolId == toolId) return
        activeJob?.cancel()
        _uiState.value = CorePdfToolUiState(toolId = toolId)
    }

    fun pickBundledSamplePdf() {
        viewModelScope.launch {
            libraryRepository.importBundledSamplePdf().onSuccess { doc ->
                pickPdfFromLibrary(doc)
            }.onFailure { err ->
                _events.emit(CorePdfToolEvent.ShowMessage(err.localizedMessage ?: "Failed to load sample PDF"))
            }
        }
    }

    fun pickPdfFromLibrary(doc: LibraryDocument) {
        val file = File(doc.localCachePath)
        if (!file.exists()) {
            viewModelScope.launch { _events.emit(CorePdfToolEvent.ShowMessage("File unavailable on disk")) }
            return
        }
        val state = _uiState.value
        if (state.toolId == "password" && state.passwordMode == PasswordToolMode.UNLOCK) {
            attachRawSourceForUnlock(file, doc.fileName, sourceUri = null)
            return
        }
        viewModelScope.launch {
            handleOpenResult(flattener.preparePdfFromFile(file, doc.fileName), sourceUri = null)
        }
    }

    fun pickPdfFromUri(uri: Uri) {
        val state = _uiState.value
        if (state.toolId == "password" && state.passwordMode == PasswordToolMode.UNLOCK) {
            viewModelScope.launch {
                val copied = withContext(ioDispatcher) { cryptoImageEngine.copyUriToTempFile(uri, "unlock_src", "pdf") }
                if (copied != null) {
                    val name = queryDisplayName(uri) ?: "Locked.pdf"
                    attachRawSourceForUnlock(copied, name, uri)
                } else {
                    _events.emit(CorePdfToolEvent.ShowMessage("Unable to read PDF"))
                }
            }
            return
        }
        viewModelScope.launch {
            handleOpenResult(flattener.preparePdfFromUri(uri), sourceUri = uri)
        }
    }

    fun pickMultiplePdfsForMerge(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val loaded = mutableListOf<PickedPdfSource>()
            for (uri in uris) {
                when (val res = flattener.preparePdfFromUri(uri)) {
                    is PdfOpenResult.Ready -> {
                        loaded.add(
                            PickedPdfSource(
                                file = res.workingCopy,
                                displayName = res.displayName,
                                pageCount = res.pageCount,
                                sizeBytes = res.workingCopy.length(),
                                sourceUri = uri
                            )
                        )
                    }
                    is PdfOpenResult.PasswordRequired -> {
                        _events.emit(CorePdfToolEvent.ShowMessage("${res.displayName} is password-protected; unlock it first"))
                    }
                    is PdfOpenResult.Error -> {
                        _events.emit(CorePdfToolEvent.ShowMessage(res.message))
                    }
                }
            }
            if (loaded.isNotEmpty()) {
                _uiState.update { current ->
                    val mergedList = current.mergeSources + loaded
                    current.copy(
                        mergeSources = mergedList,
                        step = if (mergedList.size >= 2) ToolStep.CONFIGURE else ToolStep.PICK
                    )
                }
            }
        }
    }

    fun addLibraryDocToMerge(doc: LibraryDocument) {
        val file = File(doc.localCachePath)
        if (!file.exists()) return
        viewModelScope.launch {
            when (val res = flattener.preparePdfFromFile(file, doc.fileName)) {
                is PdfOpenResult.Ready -> {
                    val item = PickedPdfSource(
                        file = res.workingCopy,
                        displayName = res.displayName,
                        pageCount = res.pageCount,
                        sizeBytes = res.workingCopy.length()
                    )
                    _uiState.update { current ->
                        val next = current.mergeSources + item
                        current.copy(mergeSources = next, step = ToolStep.CONFIGURE)
                    }
                }
                is PdfOpenResult.PasswordRequired -> {
                    _events.emit(CorePdfToolEvent.ShowMessage("${res.displayName} is password-protected; unlock it first"))
                }
                is PdfOpenResult.Error -> {
                    _events.emit(CorePdfToolEvent.ShowMessage(res.message))
                }
            }
        }
    }

    fun moveMergeSource(index: Int, delta: Int) {
        _uiState.update { state ->
            val list = state.mergeSources.toMutableList()
            val target = index + delta
            if (index in list.indices && target in list.indices) {
                val moved = list.removeAt(index)
                list.add(target, moved)
            }
            state.copy(mergeSources = list)
        }
    }

    fun removeMergeSource(id: String) {
        _uiState.update { state ->
            val next = state.mergeSources.filterNot { it.id == id }
            state.copy(
                mergeSources = next,
                step = if (next.isEmpty()) ToolStep.PICK else state.step
            )
        }
    }

    fun submitPdfPassword(password: String) {
        val file = _uiState.value.passwordPromptFile ?: return
        val name = _uiState.value.passwordPromptName
        val pendingUri = _uiState.value.pendingUriForPassword
        viewModelScope.launch {
            val res = flattener.preparePdfFromFile(file, name, password)
            if (res is PdfOpenResult.PasswordRequired) {
                _uiState.update { it.copy(passwordPromptError = "Incorrect password. Try again.") }
            } else {
                _uiState.update {
                    it.copy(
                        passwordPromptFile = null,
                        passwordPromptError = null,
                        pendingUriForPassword = null
                    )
                }
                handleOpenResult(res, sourceUri = pendingUri)
            }
        }
    }

    fun dismissPasswordPrompt() {
        _uiState.update {
            it.copy(
                passwordPromptFile = null,
                passwordPromptError = null,
                pendingUriForPassword = null
            )
        }
    }

    private fun attachRawSourceForUnlock(file: File, name: String, sourceUri: Uri?) {
        val src = PickedPdfSource(
            file = file,
            displayName = name,
            pageCount = 0,
            sizeBytes = file.length(),
            sourceUri = sourceUri
        )
        _uiState.update {
            it.copy(
                sourcePdf = src,
                step = ToolStep.CONFIGURE
            )
        }
    }

    private suspend fun handleOpenResult(result: PdfOpenResult, sourceUri: Uri?) {
        when (result) {
            is PdfOpenResult.Ready -> {
                val picked = PickedPdfSource(
                    file = result.workingCopy,
                    displayName = result.displayName,
                    pageCount = result.pageCount,
                    sizeBytes = result.workingCopy.length(),
                    sourceUri = sourceUri
                )
                val toolId = _uiState.value.toolId
                if (toolId == "merge") {
                    _uiState.update { state ->
                        state.copy(
                            mergeSources = state.mergeSources + picked,
                            step = ToolStep.CONFIGURE
                        )
                    }
                    return
                }
                if (toolId == "copy_text") {
                    // Render page 0 as a high-res image for OCR
                    _uiState.update {
                        it.copy(
                            sourcePdf = picked,
                            previewPageIndex = 0,
                            step = ToolStep.CONFIGURE
                        )
                    }
                    runOcrOnPdfPage(picked.file, 0, picked.displayName)
                    return
                }

                val defaultSplit = if (picked.pageCount > 0) setOf(0) else emptySet()
                val organizeList = (0 until picked.pageCount).map { PageOrganizeSpec(originalPageIndex = it) }

                _uiState.update {
                    it.copy(
                        sourcePdf = picked,
                        step = ToolStep.CONFIGURE,
                        thumbnails = emptyMap(),
                        previewPageIndex = 0,
                        beforeSizeBytes = picked.sizeBytes,
                        selectedSplitPages = defaultSplit,
                        splitRangeInput = if (picked.pageCount > 0) "1" else "",
                        organizeSpecs = organizeList,
                        verificationResults = null,
                        freeTextOverlays = emptyList()
                    )
                }

                if (toolId == "fill_form") {
                    val fields = coreEngine.readAcroFormFields(picked.file)
                    val initialValues = fields.filterNot { it.isCheckbox }
                        .associate { it.fullyQualifiedName to it.value }
                    val initialChecks = fields.filter { it.isCheckbox }
                        .associate { it.fullyQualifiedName to it.isChecked }
                    _uiState.update {
                        it.copy(
                            acroFields = fields,
                            fieldValues = initialValues,
                            checkboxValues = initialChecks
                        )
                    }
                    loadPreviewPage(picked.file, 0)
                } else if (toolId == "digital_signature" && _uiState.value.digitalSigMode == DigitalSigMode.VERIFY) {
                    verifyDigitalSignatures()
                }
            }
            is PdfOpenResult.PasswordRequired -> {
                _uiState.update {
                    it.copy(
                        passwordPromptFile = result.tempLockedFile,
                        passwordPromptName = result.displayName,
                        passwordPromptError = null,
                        pendingUriForPassword = sourceUri
                    )
                }
            }
            is PdfOpenResult.Error -> {
                _events.emit(CorePdfToolEvent.ShowMessage(result.message))
            }
        }
    }

    fun requestThumbnail(pageIndex: Int) {
        val state = _uiState.value
        val file = state.sourcePdf?.file ?: return
        if (state.thumbnails.containsKey(pageIndex)) return
        viewModelScope.launch {
            val bmp = flattener.renderPageBitmap(file, pageIndex, targetWidthPx = 360) ?: return@launch
            _uiState.update { current ->
                current.copy(thumbnails = current.thumbnails + (pageIndex to bmp))
            }
        }
    }

    fun loadPreviewPage(file: File, pageIndex: Int) {
        viewModelScope.launch {
            val bmp = flattener.renderPageBitmap(file, pageIndex, targetWidthPx = 900)
            _uiState.update { it.copy(previewPageIndex = pageIndex, previewBitmap = bmp) }
        }
    }

    fun changePreviewPage(delta: Int) {
        val src = _uiState.value.sourcePdf ?: return
        val next = (_uiState.value.previewPageIndex + delta).coerceIn(0, (src.pageCount - 1).coerceAtLeast(0))
        if (_uiState.value.toolId == "copy_text") {
            runOcrOnPdfPage(src.file, next, src.displayName)
        } else {
            loadPreviewPage(src.file, next)
        }
    }

    // --- Split ---
    fun toggleSplitPage(pageIndex: Int) {
        _uiState.update { state ->
            val next = if (state.selectedSplitPages.contains(pageIndex)) {
                state.selectedSplitPages - pageIndex
            } else {
                state.selectedSplitPages + pageIndex
            }
            val sorted1Based = next.sorted().map { it + 1 }.joinToString(", ")
            state.copy(selectedSplitPages = next, splitRangeInput = sorted1Based)
        }
    }

    fun updateSplitRangeInput(input: String) {
        val total = _uiState.value.sourcePdf?.pageCount ?: 0
        val parsed = PdfCoreEngine.parsePageRanges(input, total).toSet()
        _uiState.update { it.copy(splitRangeInput = input, selectedSplitPages = parsed) }
    }

    // --- Compress ---
    fun selectCompressionPreset(preset: CompressionPreset) {
        _uiState.update { it.copy(compressionPreset = preset) }
    }

    // --- Organize pages ---
    fun rotateOrganizePage(index: Int, deltaDegrees: Int = 90) {
        _uiState.update { state ->
            val list = state.organizeSpecs.toMutableList()
            if (index in list.indices) {
                val cur = list[index]
                list[index] = cur.copy(rotationDelta = (cur.rotationDelta + deltaDegrees) % 360)
            }
            state.copy(organizeSpecs = list)
        }
    }

    fun toggleDeleteOrganizePage(index: Int) {
        _uiState.update { state ->
            val list = state.organizeSpecs.toMutableList()
            if (index in list.indices) {
                val cur = list[index]
                list[index] = cur.copy(isDeleted = !cur.isDeleted)
            }
            state.copy(organizeSpecs = list)
        }
    }

    fun moveOrganizePage(index: Int, delta: Int) {
        _uiState.update { state ->
            val list = state.organizeSpecs.toMutableList()
            val target = index + delta
            if (index in list.indices && target in list.indices) {
                val item = list.removeAt(index)
                list.add(target, item)
            }
            state.copy(organizeSpecs = list)
        }
    }

    fun setOrganizeCropInset(index: Int, insetPercent: Int) {
        _uiState.update { state ->
            val list = state.organizeSpecs.toMutableList()
            if (index in list.indices) {
                list[index] = list[index].copy(cropInsetPercent = insetPercent.coerceIn(0, 35))
            }
            state.copy(organizeSpecs = list)
        }
    }

    // --- Fill a form ---
    fun updateAcroFieldText(fqName: String, value: String) {
        _uiState.update { it.copy(fieldValues = it.fieldValues + (fqName to value)) }
    }

    fun updateAcroCheckbox(fqName: String, checked: Boolean) {
        _uiState.update { it.copy(checkboxValues = it.checkboxValues + (fqName to checked)) }
    }

    fun setFlattenForm(flatten: Boolean) {
        _uiState.update { it.copy(flattenFormOnSave = flatten) }
    }

    fun addFreeTextOverlay(text: String, normX: Float, normY: Float, fontSizePt: Float) {
        if (text.isBlank()) return
        val pageIdx = _uiState.value.previewPageIndex
        val overlay = FreeTextOverlay(
            pageIndex = pageIdx,
            text = text.trim(),
            normalizedX = normX.coerceIn(0.05f, 0.85f),
            normalizedY = normY.coerceIn(0.05f, 0.90f),
            fontSizePt = fontSizePt.coerceIn(9f, 28f)
        )
        _uiState.update { it.copy(freeTextOverlays = it.freeTextOverlays + overlay) }
    }

    fun removeFreeTextOverlay(id: String) {
        _uiState.update { state ->
            state.copy(freeTextOverlays = state.freeTextOverlays.filterNot { it.id == id })
        }
    }

    // --- Digital signature ---
    fun setDigitalSigMode(mode: DigitalSigMode) {
        _uiState.update { it.copy(digitalSigMode = mode) }
        if (mode == DigitalSigMode.VERIFY && _uiState.value.sourcePdf != null) {
            verifyDigitalSignatures()
        }
    }

    fun pickPkcs12FromUri(uri: Uri) {
        viewModelScope.launch {
            val file = withContext(ioDispatcher) { cryptoImageEngine.copyUriToTempFile(uri, "id", "p12") }
            if (file != null) {
                val name = queryDisplayName(uri) ?: "Certificate.p12"
                _uiState.update { it.copy(pkcs12File = file, pkcs12DisplayName = name) }
            } else {
                _events.emit(CorePdfToolEvent.ShowMessage("Could not load PKCS#12 file"))
            }
        }
    }

    fun generateLocalPkcs12(commonName: String, password: String) {
        viewModelScope.launch {
            val certDir = File(context.filesDir, "certificates").apply { mkdirs() }
            val out = File(certDir, "digital_id_${System.currentTimeMillis()}.p12")
            cryptoImageEngine.generateSelfSignedPkcs12(commonName, password, out)
                .onSuccess { p12 ->
                    _uiState.update {
                        it.copy(
                            pkcs12File = p12,
                            pkcs12DisplayName = p12.name,
                            pkcs12Password = password,
                            generatedCertCommonName = commonName
                        )
                    }
                    _events.emit(CorePdfToolEvent.ShowMessage("Created offline Digital ID: ${p12.name}"))
                }
                .onFailure { err ->
                    _events.emit(CorePdfToolEvent.ShowMessage(err.localizedMessage ?: "Failed to create Digital ID"))
                }
        }
    }

    fun updateDigitalSigInputs(password: String? = null, reason: String? = null, location: String? = null) {
        _uiState.update {
            it.copy(
                pkcs12Password = password ?: it.pkcs12Password,
                digitalSigReason = reason ?: it.digitalSigReason,
                digitalSigLocation = location ?: it.digitalSigLocation
            )
        }
    }

    fun verifyDigitalSignatures() {
        val file = _uiState.value.sourcePdf?.file ?: return
        viewModelScope.launch {
            val results = cryptoImageEngine.verifyPdfSignatures(file)
            _uiState.update { it.copy(verificationResults = results) }
        }
    }

    // --- Password (AES-256) ---
    fun setPasswordMode(mode: PasswordToolMode) {
        _uiState.update {
            it.copy(
                passwordMode = mode,
                sourcePdf = null,
                step = ToolStep.PICK,
                lockUserPassword = "",
                lockOwnerPassword = "",
                unlockPasswordInput = ""
            )
        }
    }

    fun updatePasswordFields(
        userPass: String? = null,
        ownerPass: String? = null,
        allowPrint: Boolean? = null,
        allowCopy: Boolean? = null,
        unlockPass: String? = null
    ) {
        _uiState.update {
            it.copy(
                lockUserPassword = userPass ?: it.lockUserPassword,
                lockOwnerPassword = ownerPass ?: it.lockOwnerPassword,
                allowPrinting = allowPrint ?: it.allowPrinting,
                allowCopy = allowCopy ?: it.allowCopy,
                unlockPasswordInput = unlockPass ?: it.unlockPasswordInput
            )
        }
    }

    // --- Photos -> PDF & OCR ---
    fun prepareCameraPhotoUri(): Uri {
        val dir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
        val photoFile = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        pendingCameraFile = photoFile
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
    }

    fun onCameraPhotoCaptured(success: Boolean) {
        val file = pendingCameraFile
        pendingCameraFile = null
        if (!success || file == null || !file.exists()) return
        if (_uiState.value.toolId == "copy_text") {
            runOcrOnImageFile(file, "Camera photo")
        } else {
            val config = PhotoPageConfig(file = file, displayName = file.name)
            _uiState.update {
                it.copy(
                    photoPages = it.photoPages + config,
                    step = ToolStep.CONFIGURE
                )
            }
        }
    }

    fun addGalleryPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            if (_uiState.value.toolId == "copy_text") {
                val uri = uris.first()
                val file = withContext(ioDispatcher) { cryptoImageEngine.copyUriToTempFile(uri, "ocr_img", "jpg") }
                if (file != null) {
                    runOcrOnImageFile(file, queryDisplayName(uri) ?: "Selected image")
                }
                return@launch
            }
            val configs = withContext(ioDispatcher) {
                uris.mapIndexedNotNull { idx, uri ->
                    val file = cryptoImageEngine.copyUriToTempFile(uri, "photo_$idx", "jpg") ?: return@mapIndexedNotNull null
                    PhotoPageConfig(
                        file = file,
                        displayName = queryDisplayName(uri) ?: "Photo ${idx + 1}"
                    )
                }
            }
            if (configs.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        photoPages = it.photoPages + configs,
                        step = ToolStep.CONFIGURE
                    )
                }
            }
        }
    }

    fun togglePhotoAutoCrop(id: String) {
        _uiState.update { state ->
            state.copy(
                photoPages = state.photoPages.map {
                    if (it.id == id) it.copy(autoCropEdges = !it.autoCropEdges) else it
                }
            )
        }
    }

    fun setPhotoManualInset(id: String, percent: Int) {
        _uiState.update { state ->
            state.copy(
                photoPages = state.photoPages.map {
                    if (it.id == id) it.copy(manualCropInsetPercent = percent.coerceIn(0, 30)) else it
                }
            )
        }
    }

    fun movePhoto(index: Int, delta: Int) {
        _uiState.update { state ->
            val list = state.photoPages.toMutableList()
            val target = index + delta
            if (index in list.indices && target in list.indices) {
                val item = list.removeAt(index)
                list.add(target, item)
            }
            state.copy(photoPages = list)
        }
    }

    fun removePhoto(id: String) {
        _uiState.update { state ->
            val next = state.photoPages.filterNot { it.id == id }
            state.copy(
                photoPages = next,
                step = if (next.isEmpty()) ToolStep.PICK else state.step
            )
        }
    }

    fun setPdfPageSize(option: PdfPageSizeOption) {
        _uiState.update { it.copy(pdfPageSize = option) }
    }

    private fun runOcrOnPdfPage(pdfFile: File, pageIndex: Int, docName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(ocrRunning = true, previewPageIndex = pageIndex) }
            val bmp = flattener.renderPageBitmap(pdfFile, pageIndex, targetWidthPx = 1400)
            if (bmp == null) {
                _uiState.update { it.copy(ocrRunning = false) }
                _events.emit(CorePdfToolEvent.ShowMessage("Failed to render PDF page for OCR"))
                return@launch
            }
            val imgFile = withContext(ioDispatcher) {
                val dir = File(context.cacheDir, "ocr_pages").apply { mkdirs() }
                val f = File(dir, "page_${pageIndex}_${System.currentTimeMillis()}.jpg")
                FileOutputStream(f).use { fos -> bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos) }
                f
            }
            runOcrOnImageFile(imgFile, "$docName • Page ${pageIndex + 1}")
        }
    }

    private fun runOcrOnImageFile(imageFile: File, label: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    step = ToolStep.CONFIGURE,
                    ocrSourceImageFile = imageFile,
                    ocrSourceLabel = label,
                    ocrRunning = true
                )
            }
            cryptoImageEngine.extractTextWithOcr(imageFile)
                .onSuccess { res ->
                    _uiState.update {
                        it.copy(
                            ocrRunning = false,
                            ocrResult = res,
                            ocrEditableText = res.fullText
                        )
                    }
                    toolUsageRepository.recordToolLaunch("copy_text")
                }
                .onFailure { err ->
                    _uiState.update { it.copy(ocrRunning = false) }
                    _events.emit(CorePdfToolEvent.ShowMessage(err.localizedMessage ?: "OCR failed"))
                }
        }
    }

    fun updateOcrEditableText(text: String) {
        _uiState.update { it.copy(ocrEditableText = text) }
    }

    fun copyOcrText() {
        val text = _uiState.value.ocrEditableText
        if (text.isBlank()) return
        viewModelScope.launch {
            _events.emit(CorePdfToolEvent.CopyToClipboard(text))
            _events.emit(CorePdfToolEvent.ShowMessage("Text copied to clipboard"))
        }
    }

    fun shareOcrText() {
        val text = _uiState.value.ocrEditableText
        if (text.isBlank()) return
        viewModelScope.launch {
            _events.emit(CorePdfToolEvent.ShareText(text, "Extracted text"))
        }
    }

    // --- Execute Process ---
    fun runToolProcess() {
        val state = _uiState.value
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val baseName = state.sourcePdf?.displayName?.substringBeforeLast('.')?.ifBlank { "Document" } ?: "Document"

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    step = ToolStep.PROCESS,
                    progress = OperationProgress(0.05f, "Working on device…", isIndeterminate = false, isCancellable = true)
                )
            }

            val result: Result<Pair<File, List<Pair<String, String>>>> = when (state.toolId) {
                "merge" -> {
                    val out = File(exportDir, "Merged_${System.currentTimeMillis()}.pdf")
                    coreEngine.mergePdfs(state.mergeSources.map { it.file }, out) { pct ->
                        _uiState.update { it.copy(progress = OperationProgress(pct, "Merging ${state.mergeSources.size} PDFs…")) }
                    }.map { file ->
                        val totalPages = state.mergeSources.sumOf { it.pageCount }
                        file to listOf("Files" to "${state.mergeSources.size}", "Pages" to "$totalPages")
                    }
                }

                "split" -> {
                    val src = state.sourcePdf?.file ?: return@launch
                    val indices = state.selectedSplitPages.sorted()
                    val out = File(exportDir, "${baseName}_split_${System.currentTimeMillis()}.pdf")
                    coreEngine.splitPdf(src, indices, out) { pct ->
                        _uiState.update { it.copy(progress = OperationProgress(pct, "Extracting ${indices.size} pages…")) }
                    }.map { file ->
                        file to listOf("Extracted" to "${indices.size} pages", "Size" to formatKb(file.length()))
                    }
                }

                "compress" -> {
                    val src = state.sourcePdf?.file ?: return@launch
                    val out = File(exportDir, "${baseName}_compressed_${System.currentTimeMillis()}.pdf")
                    coreEngine.compressPdf(src, state.compressionPreset, out) { pct ->
                        _uiState.update { it.copy(progress = OperationProgress(pct, "Compressing (${state.compressionPreset.label})…")) }
                    }.map { file ->
                        val before = state.beforeSizeBytes.coerceAtLeast(1L)
                        val after = file.length()
                        val savedPct = (((before - after).toFloat() / before.toFloat()) * 100f).toInt()
                        _uiState.update { it.copy(afterSizeBytes = after) }
                        file to listOf(
                            "Before" to formatKb(before),
                            "After" to formatKb(after),
                            "Saved" to "${savedPct}%"
                        )
                    }
                }

                "organize_pages" -> {
                    val src = state.sourcePdf?.file ?: return@launch
                    val out = File(exportDir, "${baseName}_organized_${System.currentTimeMillis()}.pdf")
                    coreEngine.organizePages(src, state.organizeSpecs, out) { pct ->
                        _uiState.update { it.copy(progress = OperationProgress(pct, "Rebuilding page layout…")) }
                    }.map { file ->
                        val kept = state.organizeSpecs.count { !it.isDeleted }
                        file to listOf("Pages kept" to "$kept", "Size" to formatKb(file.length()))
                    }
                }

                "fill_form" -> {
                    val src = state.sourcePdf?.file ?: return@launch
                    val out = File(exportDir, "${baseName}_filled_${System.currentTimeMillis()}.pdf")
                    coreEngine.fillFormAndOverlays(
                        sourceFile = src,
                        fieldValues = state.fieldValues,
                        checkboxValues = state.checkboxValues,
                        freeTextOverlays = state.freeTextOverlays,
                        flattenForm = state.flattenFormOnSave,
                        outputFile = out
                    ) { pct ->
                        _uiState.update { it.copy(progress = OperationProgress(pct, "Saving form fields & annotations…")) }
                    }.map { file ->
                        val entriesCount = state.acroFields.size + state.freeTextOverlays.size
                        file to listOf("Fields" to "$entriesCount", "Size" to formatKb(file.length()))
                    }
                }

                "digital_signature" -> {
                    val src = state.sourcePdf?.file ?: return@launch
                    val p12 = state.pkcs12File
                    if (p12 == null) {
                        _uiState.update { it.copy(step = ToolStep.CONFIGURE) }
                        _events.emit(CorePdfToolEvent.ShowMessage("Generate or pick a PKCS#12 (.p12) Digital ID first"))
                        return@launch
                    }
                    val out = File(exportDir, "${baseName}_digitally_signed_${System.currentTimeMillis()}.pdf")
                    cryptoImageEngine.signPdfPkcs12(
                        sourceFile = src,
                        pkcs12File = p12,
                        pkcs12Password = state.pkcs12Password,
                        reason = state.digitalSigReason,
                        location = state.digitalSigLocation,
                        outputFile = out
                    ).map { file ->
                        file to listOf("Algorithm" to "SHA-256 RSA", "Size" to formatKb(file.length()))
                    }
                }

                "password" -> {
                    val src = state.sourcePdf?.file ?: return@launch
                    if (state.passwordMode == PasswordToolMode.LOCK_AES256) {
                        val out = File(exportDir, "${baseName}_locked_${System.currentTimeMillis()}.pdf")
                        coreEngine.lockPdfAes256(
                            sourceFile = src,
                            userPassword = state.lockUserPassword,
                            ownerPassword = state.lockOwnerPassword,
                            allowPrinting = state.allowPrinting,
                            allowCopy = state.allowCopy,
                            outputFile = out
                        ).map { file ->
                            file to listOf("Cipher" to "AES-256", "Size" to formatKb(file.length()))
                        }
                    } else {
                        val out = File(exportDir, "${baseName}_unlocked_${System.currentTimeMillis()}.pdf")
                        coreEngine.unlockPdf(
                            sourceFile = src,
                            password = state.unlockPasswordInput,
                            outputFile = out
                        ).map { file ->
                            file to listOf("Protection" to "Removed", "Size" to formatKb(file.length()))
                        }
                    }
                }

                "photos_to_pdf" -> {
                    val out = File(exportDir, "Scan_${System.currentTimeMillis()}.pdf")
                    cryptoImageEngine.imagesToPdf(state.photoPages, state.pdfPageSize, out) { pct ->
                        _uiState.update { it.copy(progress = OperationProgress(pct, "Encoding pages into PDF…")) }
                    }.map { file ->
                        file to listOf("Photos" to "${state.photoPages.size}", "Size" to formatKb(file.length()))
                    }
                }

                "copy_text" -> {
                    val img = state.ocrSourceImageFile ?: return@launch
                    val ocr = state.ocrResult ?: return@launch
                    val out = File(exportDir, "Searchable_OCR_${System.currentTimeMillis()}.pdf")
                    cryptoImageEngine.createSearchablePdfFromOcr(img, ocr, out).map { file ->
                        file to listOf("Lines indexed" to "${ocr.lines.size}", "Size" to formatKb(file.length()))
                    }
                }

                else -> Result.failure(IllegalArgumentException("Unsupported tool"))
            }

            result.onSuccess { (file, metrics) ->
                val docUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val imported = libraryRepository.importFromUri(docUri).getOrNull()
                toolUsageRepository.recordToolLaunch(state.toolId)
                _uiState.update {
                    it.copy(
                        step = ToolStep.DONE,
                        outputFile = file,
                        outputDocumentId = imported?.id,
                        summaryMetrics = metrics
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(step = ToolStep.CONFIGURE) }
                _events.emit(CorePdfToolEvent.ShowMessage(err.localizedMessage ?: "Operation failed"))
            }
        }
    }

    fun cancelProcess() {
        activeJob?.cancel()
        _uiState.update { it.copy(step = ToolStep.CONFIGURE) }
    }

    fun shareOutputPdf() {
        val out = _uiState.value.outputFile ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
        viewModelScope.launch {
            _events.emit(CorePdfToolEvent.SharePdf(uri, out.name))
        }
    }

    fun exportOutputToSaf(destUri: Uri) {
        val out = _uiState.value.outputFile ?: return
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    context.contentResolver.openOutputStream(destUri)?.use { os ->
                        out.inputStream().use { input -> input.copyTo(os) }
                    } ?: error("Cannot write to selected location")
                }
            }.onSuccess {
                _events.emit(CorePdfToolEvent.ShowMessage("Saved copy to device"))
            }.onFailure { err ->
                _events.emit(CorePdfToolEvent.ShowMessage(err.localizedMessage ?: "Save failed"))
            }
        }
    }

    fun assignOutputToCategory(categoryId: String?) {
        val docId = _uiState.value.outputDocumentId ?: return
        viewModelScope.launch {
            libraryRepository.moveToCategory(docId, categoryId)
            _events.emit(CorePdfToolEvent.ShowMessage("Saved to category"))
        }
    }

    fun requestReplaceOriginal() {
        _uiState.update { it.copy(showReplaceOriginalSheet = true) }
    }

    fun dismissReplaceOriginal() {
        _uiState.update { it.copy(showReplaceOriginalSheet = false) }
    }

    fun confirmReplaceOriginal() {
        val state = _uiState.value
        val out = state.outputFile ?: return
        val srcUri = state.sourcePdf?.sourceUri
        _uiState.update { it.copy(showReplaceOriginalSheet = false) }
        if (srcUri != null) {
            exportOutputToSaf(srcUri)
        } else {
            viewModelScope.launch {
                _events.emit(CorePdfToolEvent.ShowMessage("Original source cannot be overwritten directly; use 'Save as'"))
            }
        }
    }

    fun stepBackOrExit(onExit: () -> Unit) {
        when (_uiState.value.step) {
            ToolStep.PICK -> onExit()
            ToolStep.CONFIGURE -> _uiState.update { it.copy(step = ToolStep.PICK) }
            ToolStep.PROCESS -> cancelProcess()
            ToolStep.DONE -> onExit()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        }
    }

    private fun formatKb(bytes: Long): String {
        return if (bytes >= 1024 * 1024) {
            String.format("%.2f MB", bytes / (1024f * 1024f))
        } else {
            "${(bytes / 1024L).coerceAtLeast(1L)} KB"
        }
    }
}
