package br.com.nexo.driver.capture.visual

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Minimal pixel source so card detection remains deterministic outside Android instrumentation. */
interface VisualPixelFrame {
    val width: Int
    val height: Int
    fun argbAt(x: Int, y: Int): Int
}

/** Android adapter used by capture code without leaking [Bitmap] into the detector's core. */
class BitmapVisualPixelFrame(private val bitmap: Bitmap) : VisualPixelFrame {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
    override fun argbAt(x: Int, y: Int): Int = bitmap.getPixel(x, y)
}

data class VisualCardRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float,
    val strategy: VisualCardDetectionStrategy,
) {
    init {
        require(left >= 0 && top >= 0 && right > left && bottom > top)
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

enum class VisualCardDetectionStrategy { EDGE_BANDS, FALLBACK }

/**
 * Rec. 709 luma of a packed ARGB pixel. Shared by every luminance-only consumer in this package so
 * the card detector and the similarity gate cannot drift onto different coefficients.
 */
internal fun Int.luminance(): Float {
    val red = this shr 16 and 0xff
    val green = this shr 8 and 0xff
    val blue = this and 0xff
    return red * 0.2126f + green * 0.7152f + blue * 0.0722f
}

/**
 * Fast, provider-agnostic visual locator for the offer cards used by Uber and 99.
 *
 * It samples horizontal bands through the lower 12–76% of the frame. A real offer card creates
 * a long, high-contrast top edge whether the card is light on a map (Uber) or dark on a map
 * (99). Only luminance is used, deliberately avoiding provider colours and any OCR text. The
 * returned crop keeps small side/bottom safety margins. If the edge score is not convincing, a
 * deterministic lower-screen crop is returned so OCR still has a bounded, useful fallback.
 */
class OfferCardVisualDetector(
    private val minimumConfidence: Float = MINIMUM_CONFIDENCE,
) {
    init {
        require(minimumConfidence in 0f..1f)
    }

    fun detect(bitmap: Bitmap, providerHint: String? = null): VisualCardRegion =
        detect(BitmapVisualPixelFrame(bitmap), providerHint)

    /** [providerHint] only selects conservative fallback geometry when visual confidence is weak. */
    fun detect(frame: VisualPixelFrame, providerHint: String? = null): VisualCardRegion {
        val fallback = fallback(frame.width, frame.height, providerHint)
        if (frame.width < MIN_FRAME_EDGE || frame.height < MIN_FRAME_EDGE) {
            return fallback
        }

        val verticalStride = max(3, frame.height / VERTICAL_SAMPLE_TARGET)
        val xSamples = sampleXs(frame.width)
        var bestTop = -1
        var bestConfidence = 0f
        val startY = (frame.height * SEARCH_TOP_FRACTION).toInt()
        val endY = (frame.height * SEARCH_BOTTOM_FRACTION).toInt()
        for (y in startY + verticalStride until endY - verticalStride step verticalStride) {
            val above = meanLuminance(frame, xSamples, y - verticalStride)
            val below = meanLuminance(frame, xSamples, y + verticalStride)
            val wholeBandContrast = abs(below - above) / MAX_LUMINANCE
            if (wholeBandContrast < bestConfidence) continue

            // A second, adjacent measurement rejects short text/route edges that happen to cross
            // one sampled row. Actual cards retain the contrast just inside their top edge.
            val inside = meanLuminance(frame, xSamples, y + 2 * verticalStride)
            val sustainedContrast = abs(inside - above) / MAX_LUMINANCE
            val confidence = (wholeBandContrast * WHOLE_BAND_WEIGHT +
                sustainedContrast * SUSTAINED_EDGE_WEIGHT).coerceIn(0f, 1f)
            if (confidence > bestConfidence) {
                bestTop = y
                bestConfidence = confidence
            }
        }

        if (bestTop < 0 || bestConfidence < minimumConfidence) {
            return fallback.copy(confidence = bestConfidence)
        }
        if (
            providerHint == "uber" &&
            bestTop > frame.height * LOW_UBER_EDGE_FRACTION &&
            bestConfidence < LOW_UBER_EDGE_MAX_CONFIDENCE
        ) {
            return fallback.copy(confidence = bestConfidence)
        }
        val crop = cropFromTop(frame.width, frame.height, bestTop)
        return crop.copy(
            confidence = bestConfidence,
        )
    }

    fun fallback(width: Int, height: Int, providerHint: String? = null): VisualCardRegion =
        fallbackCrop(width, height, providerHint)

    private fun sampleXs(width: Int): IntArray = IntArray(HORIZONTAL_SAMPLES) { index ->
        val fraction = SAMPLE_LEFT_FRACTION +
            (SAMPLE_RIGHT_FRACTION - SAMPLE_LEFT_FRACTION) * index / (HORIZONTAL_SAMPLES - 1)
        (width * fraction).toInt().coerceIn(0, width - 1)
    }

    private fun meanLuminance(frame: VisualPixelFrame, xs: IntArray, y: Int): Float {
        val safeY = y.coerceIn(0, frame.height - 1)
        var total = 0f
        xs.forEach { x -> total += frame.argbAt(x, safeY).luminance() }
        return total / xs.size
    }

    private fun cropFromTop(width: Int, height: Int, detectedTop: Int): VisualCardRegion {
        val marginX = (width * HORIZONTAL_MARGIN_FRACTION).toInt().coerceAtLeast(1)
        val bottom = (height * BOTTOM_FRACTION).toInt().coerceAtMost(height)
        val top = detectedTop.coerceIn(
            (height * SEARCH_TOP_FRACTION).toInt(),
            bottom - MIN_CROP_HEIGHT,
        )
        return VisualCardRegion(
            left = marginX,
            top = top,
            right = width - marginX,
            bottom = bottom,
            confidence = 0f,
            strategy = VisualCardDetectionStrategy.EDGE_BANDS,
        )
    }

    private fun fallbackCrop(width: Int, height: Int, providerHint: String?): VisualCardRegion {
        val marginX = (width * HORIZONTAL_MARGIN_FRACTION).toInt().coerceAtLeast(1)
        val topFraction = when (providerHint) {
            "uber" -> UBER_FALLBACK_TOP_FRACTION
            else -> FALLBACK_TOP_FRACTION
        }
        val top = (height * topFraction).toInt().coerceAtMost(height - 1)
        val bottom = max(top + 1, (height * BOTTOM_FRACTION).toInt().coerceAtMost(height))
        return VisualCardRegion(
            left = marginX,
            top = top,
            right = width - marginX,
            bottom = bottom,
            confidence = 0f,
            strategy = VisualCardDetectionStrategy.FALLBACK,
        )
    }

    private companion object {
        const val MIN_FRAME_EDGE = 32
        const val MAX_LUMINANCE = 255f
        const val VERTICAL_SAMPLE_TARGET = 180
        const val HORIZONTAL_SAMPLES = 24
        const val SEARCH_TOP_FRACTION = 0.12f
        const val SEARCH_BOTTOM_FRACTION = 0.76f
        const val FALLBACK_TOP_FRACTION = 0.50f
        const val UBER_FALLBACK_TOP_FRACTION = 0.28f
        const val BOTTOM_FRACTION = 0.96f
        const val HORIZONTAL_MARGIN_FRACTION = 0.03f
        const val SAMPLE_LEFT_FRACTION = 0.10f
        const val SAMPLE_RIGHT_FRACTION = 0.90f
        const val WHOLE_BAND_WEIGHT = 0.45f
        const val SUSTAINED_EDGE_WEIGHT = 0.55f
        const val MINIMUM_CONFIDENCE = 0.18f
        const val LOW_UBER_EDGE_FRACTION = 0.62f
        const val LOW_UBER_EDGE_MAX_CONFIDENCE = 0.28f
        const val MIN_CROP_HEIGHT = 24
    }
}
