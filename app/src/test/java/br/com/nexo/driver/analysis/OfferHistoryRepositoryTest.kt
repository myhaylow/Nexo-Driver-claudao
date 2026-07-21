package br.com.nexo.driver.analysis

import br.com.nexo.driver.overlay.OverlayStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfferHistoryRepositoryTest {
    @Before
    fun reset() {
        OfferHistoryRepository.clear()
    }

    @After
    fun cleanup() {
        OfferHistoryRepository.clear()
    }

    private fun entry(
        payoutCents: Long = 3_850L,
        atEpochMs: Long = 1_000L,
        provider: String = "UberX",
    ) = OfferHistoryEntry(
        provider = provider,
        payoutCents = payoutCents,
        totalDistanceMeters = 12_300L,
        totalDurationSeconds = 1_320L,
        ratePerKmCents = 285L,
        status = OverlayStatus.ACCEPT,
        atEpochMs = atEpochMs,
    )

    @Test
    fun `records entries newest first`() {
        OfferHistoryRepository.record(entry(payoutCents = 1_000L, atEpochMs = 1_000L))
        OfferHistoryRepository.record(entry(payoutCents = 2_000L, atEpochMs = 2_000L))

        val history = OfferHistoryRepository.current()
        assertEquals(2, history.size)
        assertEquals(2_000L, history.first().payoutCents)
    }

    @Test
    fun `deduplicates the same offer re-rendered within the window`() {
        OfferHistoryRepository.record(entry(atEpochMs = 1_000L))
        OfferHistoryRepository.record(entry(atEpochMs = 2_000L))

        assertEquals(1, OfferHistoryRepository.current().size)
    }

    @Test
    fun `the same offer after the dedup window counts again`() {
        OfferHistoryRepository.record(entry(atEpochMs = 1_000L))
        OfferHistoryRepository.record(entry(atEpochMs = 1_000L + 31_000L))

        assertEquals(2, OfferHistoryRepository.current().size)
    }

    @Test
    fun `caps the history at fifty entries`() {
        repeat(60) { index ->
            OfferHistoryRepository.record(
                entry(payoutCents = index * 100L, atEpochMs = index * 40_000L),
            )
        }
        assertEquals(50, OfferHistoryRepository.current().size)
        // As entradas mais novas sobrevivem ao corte.
        assertEquals(59 * 100L, OfferHistoryRepository.current().first().payoutCents)
    }

    @Test
    fun `subscribers see updates and initial state`() {
        val seen = mutableListOf<List<OfferHistoryEntry>>()
        val subscription = OfferHistoryRepository.subscribe { seen += it }
        OfferHistoryRepository.record(entry())
        subscription.close()
        OfferHistoryRepository.record(entry(provider = "99", atEpochMs = 90_000L))

        assertTrue(seen.first().isEmpty())
        assertEquals(1, seen.last().size)
        // Depois de close() não recebe mais.
        assertEquals(2, seen.size)
    }
}
