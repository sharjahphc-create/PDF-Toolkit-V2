package com.example.feature.pdftools.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import com.example.core.common.IoDispatcher
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

enum class PdfPageSizeOption(val label: String) {
    A4("A4"),
    LETTER("US Letter"),
    FIT_IMAGE("Fit Photo")
}

data class PhotoPageConfig(
    val id: String = UUID.randomUUID().toString(),
    val file: File,
    val displayName: String,
    val autoCropEdges: Boolean = true,
    val manualCropInsetPercent: Int = 0
)

data class SignatureVerificationInfo(
    val signerName: String,
    val reason: String,
    val location: String,
    val signDate: String,
    val isCryptographicallyValid: Boolean
)

data class OcrLineBox(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class OcrExtractionResult(
    val fullText: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val lines: List<OcrLineBox>
)

@Singleton
class PdfCryptoAndImageEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    suspend fun generateSelfSignedPkcs12(
        commonName: String,
        password: String,
        outFile: File
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            require(commonName.isNotBlank()) { "Enter signer name for certificate" }
            require(password.length >= 4) { "Use a keystore password of at least 4 characters" }

            val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val now = Date()
            val expiry = Calendar.getInstance().apply {
                time = now
                add(Calendar.YEAR, 3)
            }.time
            val subject = X500Name("CN=${commonName.trim()}, O=PDF Toolkit Offline")
            val serial = BigInteger.valueOf(System.currentTimeMillis())

            val certBuilder = JcaX509v3CertificateBuilder(
                subject,
                serial,
                now,
                expiry,
                subject,
                keyPair.public
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
            val certHolder = certBuilder.build(signer)
            val cert = JcaX509CertificateConverter().getCertificate(certHolder)

            val ks = KeyStore.getInstance("PKCS12")
            ks.load(null, null)
            ks.setKeyEntry("signing_id", keyPair.private, password.toCharArray(), arrayOf(cert))
            outFile.parentFile?.mkdirs()
            FileOutputStream(outFile).use { fos ->
                ks.store(fos, password.toCharArray())
            }
            outFile
        }
    }

    suspend fun signPdfPkcs12(
        sourceFile: File,
        pkcs12File: File,
        pkcs12Password: String,
        reason: String,
        location: String,
        outputFile: File
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val ks = KeyStore.getInstance("PKCS12")
            FileInputStream(pkcs12File).use { fis ->
                ks.load(fis, pkcs12Password.toCharArray())
            }
            val alias = ks.aliases().asSequence().firstOrNull { ks.isKeyEntry(it) }
                ?: error("No private key entry found in PKCS#12 file")
            val privateKey = ks.getKey(alias, pkcs12Password.toCharArray()) as PrivateKey
            val chain = ks.getCertificateChain(alias).map { it as X509Certificate }
            val signingCert = chain.first()

            outputFile.parentFile?.mkdirs()
            val tempCopy = File(context.cacheDir, "dsig_tmp_${UUID.randomUUID()}.pdf")
            sourceFile.copyTo(tempCopy, overwrite = true)

            try {
                PDDocument.load(tempCopy, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                    val sig = PDSignature().apply {
                        setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
                        setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED)
                        name = signingCert.subjectX500Principal.name
                        this.reason = reason.ifBlank { "Digitally signed on device" }
                        this.location = location.ifBlank { "Offline Device" }
                        signDate = Calendar.getInstance()
                    }
                    doc.addSignature(sig, SignatureInterface { content: InputStream ->
                        val bytes = content.readBytes()
                        val gen = CMSSignedDataGenerator()
                        val shaSigner = JcaContentSignerBuilder("SHA256withRSA").build(privateKey)
                        gen.addSignerInfoGenerator(
                            JcaSignerInfoGeneratorBuilder(
                                JcaDigestCalculatorProviderBuilder().build()
                            ).build(shaSigner, signingCert)
                        )
                        gen.addCertificates(JcaCertStore(chain))
                        val signedData = gen.generate(CMSProcessableByteArray(bytes), false)
                        signedData.encoded
                    })
                    FileOutputStream(outputFile).use { fos ->
                        doc.saveIncremental(fos)
                    }
                }
            } finally {
                tempCopy.delete()
            }
            outputFile
        }
    }

    suspend fun verifyPdfSignatures(sourceFile: File): List<SignatureVerificationInfo> = withContext(ioDispatcher) {
        runCatching {
            val allBytes = sourceFile.readBytes()
            PDDocument.load(sourceFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                doc.signatureDictionaries.map { sig ->
                    val valid = runCatching {
                        val contents = sig.getContents(allBytes)
                        val signedContent = sig.getSignedContent(allBytes)
                        val cms = CMSSignedData(CMSProcessableByteArray(signedContent), contents)
                        val store = cms.certificates
                        val signerInfos = cms.signerInfos.signers
                        signerInfos.isNotEmpty() && signerInfos.all { signerInfo: SignerInformation ->
                            @Suppress("UNCHECKED_CAST")
                            val selector = signerInfo.sid as org.bouncycastle.util.Selector<X509CertificateHolder>
                            val matches = store.getMatches(selector)
                            val certHolder = matches.firstOrNull() ?: return@all false
                            val cert = JcaX509CertificateConverter().getCertificate(certHolder)
                            signerInfo.verify(JcaSimpleSignerInfoVerifierBuilder().build(cert))
                        }
                    }.getOrDefault(false)

                    SignatureVerificationInfo(
                        signerName = sig.name ?: "Unknown Signer",
                        reason = sig.reason.orEmpty(),
                        location = sig.location.orEmpty(),
                        signDate = sig.signDate?.time?.toString().orEmpty(),
                        isCryptographicallyValid = valid
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun imagesToPdf(
        photos: List<PhotoPageConfig>,
        pageSize: PdfPageSizeOption,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            require(photos.isNotEmpty()) { "Add at least 1 photo" }
            outputFile.parentFile?.mkdirs()

            PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                photos.forEachIndexed { idx, config ->
                    coroutineContext.ensureActive()
                    val decoded = decodeSampledBitmap(config.file, maxDimension = 1800)
                        ?: error("Could not decode ${config.displayName}")
                    val cropped = applyCrop(decoded, config.autoCropEdges, config.manualCropInsetPercent)
                    if (cropped !== decoded) decoded.recycle()

                    try {
                        val pageRect = when (pageSize) {
                            PdfPageSizeOption.A4 -> PDRectangle.A4
                            PdfPageSizeOption.LETTER -> PDRectangle.LETTER
                            PdfPageSizeOption.FIT_IMAGE -> PDRectangle(cropped.width.toFloat(), cropped.height.toFloat())
                        }
                        val page = PDPage(pageRect)
                        doc.addPage(page)

                        val imgObj = JPEGFactory.createFromImage(doc, cropped, 0.85f)
                        val margin = if (pageSize == PdfPageSizeOption.FIT_IMAGE) 0f else 24f
                        val availW = (pageRect.width - 2f * margin).coerceAtLeast(10f)
                        val availH = (pageRect.height - 2f * margin).coerceAtLeast(10f)
                        val scale = min(availW / cropped.width.toFloat(), availH / cropped.height.toFloat())
                        val drawW = cropped.width * scale
                        val drawH = cropped.height * scale
                        val drawX = (pageRect.width - drawW) / 2f
                        val drawY = (pageRect.height - drawH) / 2f

                        PDPageContentStream(doc, page).use { cs ->
                            cs.drawImage(imgObj, drawX, drawY, drawW, drawH)
                        }
                    } finally {
                        cropped.recycle()
                    }
                    onProgress((idx + 1).toFloat() / photos.size.toFloat())
                }
                doc.save(outputFile)
            }
            outputFile
        }
    }

    suspend fun extractTextWithOcr(imageFile: File): Result<OcrExtractionResult> = withContext(ioDispatcher) {
        runCatching {
            val bitmap = decodeSampledBitmap(imageFile, maxDimension = 1800)
                ?: error("Could not read image for OCR")
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val visionText = recognizer.process(inputImage).await()
                val lines = visionText.textBlocks.flatMap { block ->
                    block.lines.mapNotNull { line ->
                        val box = line.boundingBox ?: return@mapNotNull null
                        OcrLineBox(
                            text = line.text,
                            left = box.left,
                            top = box.top,
                            right = box.right,
                            bottom = box.bottom
                        )
                    }
                }
                OcrExtractionResult(
                    fullText = visionText.text,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    lines = lines
                )
            } finally {
                bitmap.recycle()
            }
        }
    }

    suspend fun createSearchablePdfFromOcr(
        imageFile: File,
        ocrResult: OcrExtractionResult,
        outputFile: File
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            outputFile.parentFile?.mkdirs()
            val bitmap = decodeSampledBitmap(imageFile, maxDimension = 1800)
                ?: error("Could not decode source image")
            try {
                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                    val pageRect = PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat())
                    val page = PDPage(pageRect)
                    doc.addPage(page)

                    val pdImg = JPEGFactory.createFromImage(doc, bitmap, 0.85f)
                    PDPageContentStream(doc, page).use { cs ->
                        cs.drawImage(pdImg, 0f, 0f, pageRect.width, pageRect.height)

                        // Invisible searchable OCR text layer aligned over the image
                        cs.setRenderingMode(RenderingMode.NEITHER)
                        ocrResult.lines.forEach { line ->
                            val lineHeight = (line.bottom - line.top).coerceAtLeast(8).toFloat()
                            val fontSize = (lineHeight * 0.82f).coerceIn(6f, 48f)
                            val pdfX = line.left.toFloat().coerceIn(0f, pageRect.width - 10f)
                            val pdfY = (pageRect.height - line.bottom.toFloat()).coerceIn(0f, pageRect.height - 6f)
                            val cleanText = line.text.replace(Regex("[^\\x20-\\x7E]"), " ").trim()
                            if (cleanText.isNotEmpty()) {
                                cs.beginText()
                                cs.setFont(PDType1Font.HELVETICA, fontSize)
                                cs.newLineAtOffset(pdfX, pdfY)
                                runCatching { cs.showText(cleanText) }
                                cs.endText()
                            }
                        }
                    }
                    doc.save(outputFile)
                }
            } finally {
                bitmap.recycle()
            }
            outputFile
        }
    }

    fun copyUriToTempFile(uri: Uri, prefix: String = "tmp", ext: String = "bin"): File? {
        val dir = File(context.cacheDir, "pdftools_work").apply { mkdirs() }
        val out = File(dir, "${prefix}_${UUID.randomUUID()}.$ext")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { fos -> input.copyTo(fos) }
            } ?: return null
            out
        }.getOrNull()
    }

    private fun decodeSampledBitmap(file: File, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun applyCrop(source: Bitmap, autoCrop: Boolean, manualInsetPercent: Int): Bitmap {
        var rect = if (autoCrop) detectDocumentBounds(source) else Rect(0, 0, source.width, source.height)
        if (manualInsetPercent > 0) {
            val dx = (rect.width() * (manualInsetPercent / 100f)).toInt()
            val dy = (rect.height() * (manualInsetPercent / 100f)).toInt()
            rect = Rect(
                (rect.left + dx).coerceAtMost(rect.right - 20),
                (rect.top + dy).coerceAtMost(rect.bottom - 20),
                (rect.right - dx).coerceAtLeast(rect.left + 20),
                (rect.bottom - dy).coerceAtLeast(rect.top + 20)
            )
        }
        if (rect.left == 0 && rect.top == 0 && rect.width() == source.width && rect.height() == source.height) {
            return source
        }
        return Bitmap.createBitmap(
            source,
            rect.left.coerceIn(0, source.width - 1),
            rect.top.coerceIn(0, source.height - 1),
            rect.width().coerceIn(1, source.width - rect.left),
            rect.height().coerceIn(1, source.height - rect.top)
        )
    }

    /**
     * Fast contrast-based bounding box detector for document photos on contrasting backgrounds.
     */
    private fun detectDocumentBounds(bitmap: Bitmap): Rect {
        val w = bitmap.width
        val h = bitmap.height
        val step = 6
        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0
        var foregroundCount = 0

        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luma = (0.299 * r + 0.587 * g + 0.114 * b)
                if (luma > 135) {
                    foregroundCount++
                    minX = min(minX, x)
                    maxX = max(maxX, x)
                    minY = min(minY, y)
                    maxY = max(maxY, y)
                }
            }
        }

        val totalSamples = (w / step) * (h / step)
        if (foregroundCount < totalSamples * 0.15f || maxX - minX < w * 0.25f || maxY - minY < h * 0.25f) {
            return Rect(0, 0, w, h)
        }
        return Rect(
            (minX - 12).coerceAtLeast(0),
            (minY - 12).coerceAtLeast(0),
            (maxX + 12).coerceAtMost(w),
            (maxY + 12).coerceAtMost(h)
        )
    }
}
