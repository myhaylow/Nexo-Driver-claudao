package br.com.nexo.driver.capture.visual

import kotlin.math.abs

/**
 * Skips OCR when a frame is visually indistinguishable from the one already recognised.
 *
 * Deduplication used to happen only after OCR and parsing, so a stationary offer card cost a full
 * recognition pass (~190ms on a Galaxy S23) every throttle tick just to conclude nothing had
 * changed. This gate makes that conclusion up front from a coarse luminance signature, which is
 * orders of magnitude cheaper than recognising the text again.
 *
 * The signature is deliberately coarse: it must be stable against compression noise and the ride
 * app's map animating underneath, while still reacting to the card's text changing. Comparison is
 * a mean absolute difference over the signature cells -- the same idea as the SAD threshold used
 * by comparable apps.
 *
 * This never suppresses a *new* card: a changed payout or leg alters several cells well beyond
 * the threshold. When in doubt the gate admits the frame, because a redundant OCR pass is far
 * cheaper than a missed offer.
 */
class FrameSimilarityGate(
    private val threshold: Int = DEFAULT_THRESHOLD,
) {
    init {
        require(threshold >= 0) { "Similarity threshold cannot be negative." }
    }

    private var previous: IntArray? = null

    /**
     * Returns true when [frame] should be recognised. Records the signature either way, so the
     * next call compares against the most recent frame rather than the last admitted one.
     */
    @Synchronized
    fun shouldProcess(frame: VisualPixelFrame): Boolean {
        val signature = signatureOf(frame) ?: return true
        val last = previous
        previous = signature
        if (last == null || last.size != signature.size) return true
        return meanAbsoluteDifference(last, signature) > threshold
    }

    @Synchronized
    fun reset() {
        previous = null
    }

    /** Average luminance over a [GRID]x[GRID] grid of the frame. */
    private fun signatureOf(frame: VisualPixelFrame): IntArray? {
        if (frame.width < GRID || frame.height < GRID) return null
        val cells = IntArray(GRID * GRID)
        val cellWidth = frame.width / GRID
        val cellHeight = frame.height / GRID
        for (row in 0 until GRID) {
            for (column in 0 until GRID) {
                var total = 0L
                var samples = 0
                var y = row * cellHeight
                while (y < (row + 1) * cellHeight) {
                    var x = column * cellWidth
                    while (x < (column + 1) * cellWidth) {
                        total += luminance(frame.argbAt(x, y))
                        samples += 1
                        x += SAMPLE_STEP
                    }
                    y += SAMPLE_STEP
                }
                cells[row * GRID + column] = if (samples == 0) 0 else (total / samples).toInt()
            }
        }
        return cells
    }

    private fun meanAbsoluteDifference(first: IntArray, second: IntArray): Int {
        var total = 0L
        for (index in first.indices) {
            total += abs(first[index] - second[index])
        }
        return (total / first.size).toInt()
    }

    /** Rec. 601 luma, matching the luminance-only approach used by the card detector. */
    private fun luminance(argb: Int): Int {
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        return (red * 299 + green * 587 + blue * 114) / 1000
    }

    private companion object {
        const val GRID = 12
        const val SAMPLE_STEP = 4

        /**
         * Mean luminance delta, 0..255. Tuned so a moving map underneath the card stays below it
         * while a changed payout digit clears it.
         */
        const val DEFAULT_THRESHOLD = 2
    }
}
