package br.com.nexo.driver.capture.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualOfferCardDetectorTest {
    private val detector = OfferCardVisualDetector()

    @Test
    fun `finds a light Uber style card against a dark map`() {
        val bitmap = SyntheticBitmap(width = 400, height = 800, fill = gray(30)).apply {
            fillRect(left = 12, top = 430, right = 388, bottom = 760, color = gray(235))
        }

        val result = detector.detect(bitmap)

        assertEquals(VisualCardDetectionStrategy.EDGE_BANDS, result.strategy)
        assertTrue(result.confidence >= 0.18f)
        assertTrue("top=${result.top}", result.top in 420..440)
        assertEquals(12, result.left)
        assertEquals(388, result.right)
    }

    @Test
    fun `finds a radar card that starts in the upper half of the frame`() {
        val bitmap = SyntheticBitmap(width = 400, height = 800, fill = gray(35)).apply {
            fillRect(left = 12, top = 128, right = 388, bottom = 430, color = gray(240))
        }

        val result = detector.detect(bitmap)

        assertEquals(VisualCardDetectionStrategy.EDGE_BANDS, result.strategy)
        assertTrue(result.confidence >= 0.18f)
        assertTrue("top=${result.top}", result.top in 120..140)
    }

    @Test
    fun `finds a dark 99 style card against a light map`() {
        val bitmap = SyntheticBitmap(width = 400, height = 800, fill = gray(225)).apply {
            fillRect(left = 12, top = 486, right = 388, bottom = 760, color = gray(25))
        }

        val result = detector.detect(bitmap)

        assertEquals(VisualCardDetectionStrategy.EDGE_BANDS, result.strategy)
        assertTrue(result.confidence >= 0.18f)
        assertTrue("top=${result.top}", result.top in 475..495)
    }

    @Test
    fun `uses the deterministic fallback crop for a visually uniform frame`() {
        val bitmap = SyntheticBitmap(width = 400, height = 800, fill = gray(110))

        val result = detector.detect(bitmap)

        assertEquals(VisualCardDetectionStrategy.FALLBACK, result.strategy)
        assertEquals(0f, result.confidence)
        assertEquals(400, result.top)
        assertEquals(768, result.bottom)
    }

    @Test
    fun `uses a taller Uber fallback when only a weak low edge is visible`() {
        val bitmap = SyntheticBitmap(width = 400, height = 800, fill = gray(30)).apply {
            fillRect(left = 12, top = 575, right = 388, bottom = 760, color = gray(75))
        }

        val result = detector.detect(bitmap, providerHint = "uber")

        assertEquals(VisualCardDetectionStrategy.FALLBACK, result.strategy)
        assertEquals(224, result.top)
        assertEquals(768, result.bottom)
    }

    @Test
    fun `small frames still return a valid bounded fallback`() {
        val bitmap = SyntheticBitmap(width = 24, height = 24, fill = gray(0))

        val result = detector.detect(bitmap)

        assertEquals(VisualCardDetectionStrategy.FALLBACK, result.strategy)
        assertTrue(result.width > 0)
        assertTrue(result.height > 0)
    }

    private class SyntheticBitmap(
        override val width: Int,
        override val height: Int,
        fill: Int,
    ) : VisualPixelFrame {
        private val pixels = IntArray(width * height) { fill }

        override fun argbAt(x: Int, y: Int): Int = pixels[y * width + x]

        fun fillRect(left: Int, top: Int, right: Int, bottom: Int, color: Int) {
            for (y in top until bottom) {
                for (x in left until right) pixels[y * width + x] = color
            }
        }
    }

    private companion object {
        fun gray(value: Int): Int = 0xff000000.toInt() or (value shl 16) or (value shl 8) or value
    }
}
