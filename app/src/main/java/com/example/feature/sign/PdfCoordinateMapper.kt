package com.example.feature.sign

/**
 * Represents a PDF page's CropBox/MediaBox geometry in PDF points (1/72 inch, origin bottom-left).
 */
data class PdfPageBox(
    val lowerLeftX: Float,
    val lowerLeftY: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Int // 0, 90, 180, 270 clockwise
)

/**
 * Mapped placement target in PDF user space (origin bottom-left of unrotated page),
 * plus the draw angle needed so the signature reads upright in the rotated viewer.
 */
data class PdfUserSpaceRect(
    val bottomLeftX: Float,
    val bottomLeftY: Float,
    val width: Float,
    val height: Float,
    val centerX: Float,
    val centerY: Float,
    val totalRotationDegrees: Float
)

object PdfCoordinateMapper {

    /**
     * Clamps normalized placement coordinates so the signature box always remains inside [0..1] bounds.
     */
    fun clampNormalized(
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ): NormalizedBox {
        val safeW = width.coerceIn(0.05f, 0.95f)
        val safeH = height.coerceIn(0.03f, 0.95f)
        val safeX = x.coerceIn(0f, (1f - safeW).coerceAtLeast(0f))
        val safeY = y.coerceIn(0f, (1f - safeH).coerceAtLeast(0f))
        return NormalizedBox(safeX, safeY, safeW, safeH)
    }

    /**
     * Converts visual normalized coordinates (origin top-left, 0..1 on the displayed rotated page)
     * into PDF user space coordinates (origin bottom-left, accounting for CropBox offsets and page rotation).
     */
    fun toPdfUserSpace(
        normalizedX: Float,
        normalizedY: Float,
        normalizedWidth: Float,
        normalizedHeight: Float,
        overlayRotationDegrees: Float,
        pageBox: PdfPageBox
    ): PdfUserSpaceRect {
        val clamped = clampNormalized(normalizedX, normalizedY, normalizedWidth, normalizedHeight)
        val normRot = ((pageBox.rotationDegrees % 360) + 360) % 360
        val cropX = pageBox.lowerLeftX
        val cropY = pageBox.lowerLeftY
        val cropW = pageBox.width
        val cropH = pageBox.height

        // Map visual top-left (vx1, vy1) and bottom-right (vx2, vy2) corners into unrotated PDF space
        val vx1 = clamped.x
        val vy1 = clamped.y
        val vx2 = clamped.x + clamped.width
        val vy2 = clamped.y + clamped.height

        val p1 = visualPointToPdf(vx1, vy1, normRot, cropX, cropY, cropW, cropH)
        val p2 = visualPointToPdf(vx2, vy2, normRot, cropX, cropY, cropW, cropH)

        val minX = minOf(p1.first, p2.first)
        val maxX = maxOf(p1.first, p2.first)
        val minY = minOf(p1.second, p2.second)
        val maxY = maxOf(p1.second, p2.second)

        // When page is rotated clockwise by normRot, PdfBox content must be rotated counter-clockwise
        // (or +normRot in PDF CCW user-space? Let's verify: PDF user space rotates CCW for positive angles;
        // a page rotated 90 CW displays unrotated +Y pointing right, so to appear upright in the viewer,
        // the image's +X axis must point along unrotated +Y, which is +90 CCW in PDF user space!)
        val baseCounterClockwiseFix = when (normRot) {
            90 -> 90f
            180 -> 180f
            270 -> 270f
            else -> 0f
        }
        // Overlay rotation is clockwise on screen -> subtract in CCW PDF user space
        val totalCcwRotation = baseCounterClockwiseFix - overlayRotationDegrees

        // Unrotated visual width/height in PDF points before rotation
        val visualWidthPts = when (normRot) {
            90, 270 -> clamped.width * cropH
            else -> clamped.width * cropW
        }
        val visualHeightPts = when (normRot) {
            90, 270 -> clamped.height * cropW
            else -> clamped.height * cropH
        }

        return PdfUserSpaceRect(
            bottomLeftX = minX,
            bottomLeftY = minY,
            width = visualWidthPts,
            height = visualHeightPts,
            centerX = (minX + maxX) / 2f,
            centerY = (minY + maxY) / 2f,
            totalRotationDegrees = totalCcwRotation
        )
    }

    private fun visualPointToPdf(
        vx: Float,
        vy: Float,
        normRot: Int,
        cropX: Float,
        cropY: Float,
        cropW: Float,
        cropH: Float
    ): Pair<Float, Float> {
        return when (normRot) {
            90 -> {
                val px = cropX + vy * cropW
                val py = cropY + vx * cropH
                px to py
            }
            180 -> {
                val px = cropX + (1f - vx) * cropW
                val py = cropY + vy * cropH
                px to py
            }
            270 -> {
                val px = cropX + (1f - vy) * cropW
                val py = cropY + (1f - vx) * cropH
                px to py
            }
            else -> {
                val px = cropX + vx * cropW
                val py = cropY + (1f - vy) * cropH
                px to py
            }
        }
    }

    data class NormalizedBox(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )
}
