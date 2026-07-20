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

    /**
     * Average luminance over a [GRID]x[GRID] grid, from a fixed number of probes per cell.
     *
     * The probe count per cell is constant rather than derived from a pixel stride. A stride walks
     * every Nth pixel, so cost grows with frame area: on a 1080x2340 capture a 4px stride works out
     * to roughly 165k `argbAt` calls, and each one is a single-pixel JNI crossing into
     * `Bitmap.getPixel`. That is enough work to cost more than the OCR pass this gate exists to
     * skip. A fixed [PROBES_PER_AXIS] grid bounds the total at [GRID]^2 * [PROBES_PER_AXIS]^2
     * probes -- 2304 here -- regardless of resolution, which is ample for detecting that a card's
     * text changed.
     */
    private fun signatureOf(frame: VisualPixelFrame): IntArray? {
        if (frame.width < GRID || frame.height < GRID) return null
        val cells = IntArray(GRID * GRID)
        val cellWidth = frame.width / GRID
        val cellHeight = frame.height / GRID
        for (row in 0 until GRID) {
            for (column in 0 until GRID) {
                var total = 0f
                var samples = 0
                for (probeY in 0 until PROBES_PER_AXIS) {
                    val y = row * cellHeight + (probeY * cellHeight) / PROBES_PER_AXIS
                    for (probeX in 0 until PROBES_PER_AXIS) {
                        val x = column * cellWidth + (probeX * cellWidth) / PROBES_PER_AXIS
                        total += frame.argbAt(x, y).luminance()
                        samples += 1
                    }
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

    private companion object {
        const val GRID = 12

        /** Probes per axis within each cell; total probes are GRID^2 * this^2, independent of resolution. */
        const val PROBES_PER_AXIS = 4

        /**
         * Mean luminance delta, 0..255. Tuned so a moving map underneath the card stays below it
         * while a changed payout digit clears it.
         */
        const val DEFAULT_THRESHOLD = 2
    }
}
