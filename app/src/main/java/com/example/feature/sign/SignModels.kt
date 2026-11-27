package com.example.feature.sign

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

enum class SignaturePenColor(val label: String, val argb: Int, val composeColor: Color) {
    BLACK("Black", 0xFF111827.toInt(), Color(0xFF111827)),
    BLUE("Ink Blue", 0xFF1D4ED8.toInt(), Color(0xFF1D4ED8)),
    DARK_GRAY("Charcoal", 0xFF4B5563.toInt(), Color(0xFF4B5563))
}

enum class SignatureKind(val label: String) {
    SIGNATURE("Signature"),
    INITIALS("Initials")
}

data class StrokePoint(
    val xFraction: Float,
    val yFraction: Float,
    val pressure: Float = 1f
)

data class SignatureStroke(
    val points: List<StrokePoint>,
    val colorArgb: Int,
    val strokeWidthDp: Float
)

data class DrawnSignatureAsset(
    val id: String = UUID.randomUUID().toString(),
    val pngBytes: ByteArray,
    val aspectRatio: Float,
    val strokes: List<SignatureStroke> = emptyList(),
    val kind: SignatureKind = SignatureKind.SIGNATURE
)

data class SignaturePlacement(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int, // 0-based
    val assetId: String,
    val pngBytes: ByteArray,
    /** Normalized top-left X [0..1] in displayed (rotated) page space */
    val normalizedX: Float,
    /** Normalized top-left Y [0..1] in displayed (rotated) page space */
    val normalizedY: Float,
    /** Normalized width [0..1] */
    val normalizedWidth: Float,
    /** Normalized height [0..1] */
    val normalizedHeight: Float,
    /** Overlay clockwise rotation in degrees */
    val rotationDegrees: Float = 0f,
    val aspectRatio: Float = 2.5f,
    val includeDateStamp: Boolean = false,
    val dateStampText: String = "",
    val signedByText: String = "",
    val isConfirmed: Boolean = false
)

object SignatureRenderer {

    /**
     * Renders normalized strokes with quadratic Bezier smoothing and auto-trims transparent padding.
     */
    fun renderAutoTrimmedPng(
        strokes: List<SignatureStroke>,
        canvasWidth: Int = 1200,
        canvasHeight: Int = 520
    ): Pair<ByteArray, Float>? {
        if (strokes.isEmpty()) return null
        val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            paint.color = stroke.colorArgb
            paint.strokeWidth = stroke.strokeWidthDp * 3.2f

            if (stroke.points.size == 1) {
                val pt = stroke.points.first()
                paint.style = Paint.Style.FILL
                canvas.drawCircle(
                    pt.xFraction * canvasWidth,
                    pt.yFraction * canvasHeight,
                    paint.strokeWidth / 2f,
                    paint
                )
                paint.style = Paint.Style.STROKE
            } else {
                val path = Path()
                val first = stroke.points.first()
                var prevX = first.xFraction * canvasWidth
                var prevY = first.yFraction * canvasHeight
                path.moveTo(prevX, prevY)

                for (i in 1 until stroke.points.size) {
                    val curr = stroke.points[i]
                    val currX = curr.xFraction * canvasWidth
                    val currY = curr.yFraction * canvasHeight
                    val midX = (prevX + currX) / 2f
                    val midY = (prevY + currY) / 2f
                    path.quadTo(prevX, prevY, midX, midY)
                    prevX = currX
                    prevY = currY
                }
                path.lineTo(prevX, prevY)
                canvas.drawPath(path, paint)
            }
        }

        val trimmed = autoTrimTransparent(bitmap, paddingPx = 16) ?: run {
            bitmap.recycle()
            return null
        }
        if (trimmed !== bitmap) {
            bitmap.recycle()
        }

        val out = ByteArrayOutputStream()
        trimmed.compress(Bitmap.CompressFormat.PNG, 100, out)
        val aspect = trimmed.width.toFloat() / trimmed.height.coerceAtLeast(1).toFloat()
        trimmed.recycle()
        return out.toByteArray() to aspect.coerceIn(0.3f, 8f)
    }

    fun autoTrimTransparent(source: Bitmap, paddingPx: Int = 12): Bitmap? {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val alpha = (pixels[rowOffset + x] ushr 24) and 0xFF
                if (alpha > 4) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX < minX || maxY < minY) return null

        val cropLeft = max(0, minX - paddingPx)
        val cropTop = max(0, minY - paddingPx)
        val cropRight = min(width, maxX + 1 + paddingPx)
        val cropBottom = min(height, maxY + 1 + paddingPx)

        return Bitmap.createBitmap(
            source,
            cropLeft,
            cropTop,
            (cropRight - cropLeft).coerceAtLeast(1),
            (cropBottom - cropTop).coerceAtLeast(1)
        )
    }

    fun decodeAspectRatio(pngBytes: ByteArray): Float {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size, opts)
        return if (opts.outHeight > 0) {
            (opts.outWidth.toFloat() / opts.outHeight.toFloat()).coerceIn(0.3f, 8f)
        } else 2.5f
    }
}
