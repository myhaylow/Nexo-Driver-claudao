package br.com.nexo.driver.ocr.mlkit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures real on-device ML Kit latency for a synthetic offer-card frame, at the production
 * capture size and at a smaller candidate size. Not a correctness test: it exists to put real
 * numbers behind the one-second budget (throttle + OCR + parse + render) on actual hardware.
 * Read results from logcat tag [TAG].
 */
@RunWith(AndroidJUnit4::class)
class MlKitOcrLatencyBenchmark {

    @Test
    fun measuresRecognitionLatencyAtProductionAndReducedCaptureSizes() {
        MlKitBitmapOcrEngine().use { engine ->
            // First recognition loads the model; measure it separately as the cold-start cost.
            val coldFrame = syntheticOfferCard(width = 1080, height = 2340)
            val coldStart = System.nanoTime()
            val coldBlocks = engine.recognize(coldFrame)
            val coldMillis = (System.nanoTime() - coldStart) / 1_000_000
            coldFrame.recycle()
            Log.i(TAG, "cold-start: ${coldMillis}ms (${coldBlocks.size} blocks)")

            for ((label, width, height) in listOf(
                Triple("production-1080", 1080, 2340),
                Triple("reduced-720", 720, 1560),
            )) {
                val samples = LongArray(RUNS_PER_SIZE) {
                    val frame = syntheticOfferCard(width, height)
                    val start = System.nanoTime()
                    val blocks = engine.recognize(frame)
                    val elapsed = (System.nanoTime() - start) / 1_000_000
                    frame.recycle()
                    assertTrue(
                        "$label: OCR must still read the payout line",
                        blocks.any { it.text.contains("13,58") },
                    )
                    elapsed
                }
                samples.sort()
                Log.i(
                    TAG,
                    "$label: p50=${samples[samples.size / 2]}ms " +
                        "min=${samples.first()}ms max=${samples.last()}ms " +
                        "samples=${samples.joinToString()}",
                )
            }
        }
    }

    /** Draws a plausible Uber-style offer card: service line, payout, rating, two legs. */
    private fun syntheticOfferCard(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        val scale = width / 1080f
        var y = height * 0.45f
        for ((text, sizePx) in listOf(
            "UberX" to 56f,
            "R$ 13,58" to 96f,
            "4,89 (245)" to 48f,
            "3 min (1,2 km)" to 52f,
            "Rua Arthur Manoel Iwersen, Curitiba" to 44f,
            "19 min (9,3 km)" to 52f,
            "Rua Adolfo Saviski, São José dos Pinhais" to 44f,
        )) {
            paint.textSize = sizePx * scale
            canvas.drawText(text, width * 0.08f, y, paint)
            y += paint.textSize * 1.7f
        }
        return bitmap
    }

    private companion object {
        const val TAG = "OcrLatencyBenchmark"
        const val RUNS_PER_SIZE = 8
    }
}
