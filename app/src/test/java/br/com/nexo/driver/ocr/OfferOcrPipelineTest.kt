package br.com.nexo.driver.ocr

import br.com.nexo.driver.offer.OfferSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferOcrPipelineTest {
    @Test
    fun `creates raw text in reading order and parses Uber offer`() {
        val pipeline = OfferOcrPipeline(clock = SequenceClock(100L, 2_100_100L))

        val result = pipeline.process(
            OcrTextSnapshot(
                blocks = listOf(
                    OcrTextBlock("3 min (1,2 km)", readingOrder = 3),
                    OcrTextBlock("UberX", readingOrder = 0),
                    OcrTextBlock("R$ 13,58", readingOrder = 1),
                    OcrTextBlock("4,89 (245)", readingOrder = 2),
                    OcrTextBlock("19 min (9,3 km)", readingOrder = 4),
                ),
                capturedAtEpochMs = 1_000L,
                layoutHint = "uber",
            ),
        )

        assertEquals("UberX\nR$ 13,58\n4,89 (245)\n3 min (1,2 km)\n19 min (9,3 km)", result.raw.text)
        assertNotNull(result.offer)
        assertEquals(OfferSource.UBER, result.offer?.source)
        assertEquals(2_100_000L, result.processingLatencyNanos)
        assertTrue(result.shouldEmit)
        assertFalse(result.isDuplicate)
    }

    @Test
    fun `suppresses repeated parsed offer only inside deduplication window`() {
        val pipeline = OfferOcrPipeline(deduplicator = OfferDeduplicator(windowMs = 1_000L))
        val snapshot = uberSnapshot(capturedAt = 1_000L)

        assertTrue(pipeline.process(snapshot).shouldEmit)
        val repeated = pipeline.process(snapshot.copy(capturedAtEpochMs = 1_500L))
        assertTrue(repeated.isDuplicate)
        assertFalse(repeated.shouldEmit)
        assertTrue(pipeline.process(snapshot.copy(capturedAtEpochMs = 2_501L)).shouldEmit)
        assertEquals(3L, pipeline.metrics().processedSnapshots)
        assertEquals(3L, pipeline.metrics().parsedOffers)
        assertEquals(1L, pipeline.metrics().duplicateOffers)
    }

    @Test
    fun `preserves unrecognized raw text without emitting an offer`() {
        val result = OfferOcrPipeline().process(
            OcrTextSnapshot(
                blocks = listOf(OcrTextBlock("Texto sem card de oferta", readingOrder = 0)),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals("Texto sem card de oferta", result.raw.text)
        assertEquals(null, result.offer)
        assertFalse(result.isDuplicate)
        assertFalse(result.shouldEmit)
    }

    @Test
    fun `flags a recognized card marker whose fields could not be parsed`() {
        val pipeline = OfferOcrPipeline()

        val result = pipeline.process(
            OcrTextSnapshot(
                blocks = listOf(OcrTextBlock("UberX", readingOrder = 0)),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(null, result.offer)
        assertEquals(OfferSource.UBER, result.unrecognizedLayoutSource)
        assertEquals(1L, pipeline.metrics().unrecognizedLayoutCount)
    }

    @Test
    fun `ignores transient ride app frames before parser classification`() {
        val pipeline = OfferOcrPipeline()

        val result = pipeline.process(
            OcrTextSnapshot(
                blocks = listOf(
                    OcrTextBlock("UberX", 0),
                    OcrTextBlock("Buscando ofertas de corrida", 1),
                ),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(null, result.offer)
        assertEquals(null, result.unrecognizedLayoutSource)
        assertEquals(1L, pipeline.metrics().processedSnapshots)
        assertEquals(0L, pipeline.metrics().unrecognizedLayoutCount)
    }

    @Test
    fun `does not discard a real Uber offer just because the card mentions corrida`() {
        val pipeline = OfferOcrPipeline()

        val result = pipeline.process(
            OcrTextSnapshot(
                blocks = listOf(
                    OcrTextBlock("Solicitação de corrida", 0),
                    OcrTextBlock("UberX", 1),
                    OcrTextBlock("R$ 23,11", 2),
                    OcrTextBlock("R$ 1,76/km est.", 3),
                    OcrTextBlock("5,00 (2)", 4),
                    OcrTextBlock("14 min", 5),
                    OcrTextBlock("6.6 km", 6),
                    OcrTextBlock("11 minutos", 7),
                    OcrTextBlock("6.5 km", 8),
                ),
                capturedAtEpochMs = 1L,
                layoutHint = "uber",
            ),
        )

        assertEquals(2_311L, result.offer?.payout?.value?.cents)
        assertEquals(14 * 60L, result.offer?.pickup?.duration?.value?.seconds)
        assertEquals(6_600L, result.offer?.pickup?.distance?.value?.meters)
        assertEquals(11 * 60L, result.offer?.trip?.duration?.value?.seconds)
        assertEquals(6_500L, result.offer?.trip?.distance?.value?.meters)
    }

    private fun uberSnapshot(capturedAt: Long) = OcrTextSnapshot(
        blocks = listOf(
            OcrTextBlock("UberX", 0),
            OcrTextBlock("R$ 13,58", 1),
            OcrTextBlock("3 min (1,2 km)", 2),
            OcrTextBlock("19 min (9,3 km)", 3),
        ),
        capturedAtEpochMs = capturedAt,
        layoutHint = "uber",
    )

    private class SequenceClock(private vararg val readings: Long) : MonotonicClock {
        private var index = 0

        override fun nowNanos(): Long = readings[index++]
    }
}
