package br.com.nexo.driver.screenreader.orchestration

import br.com.nexo.driver.screenreader.domain.CaptureMethod
import br.com.nexo.driver.screenreader.domain.RideOffer
import br.com.nexo.driver.screenreader.domain.RideSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class WindowOfferStateTest {
    private fun offer(windowId: Int, at: Long) = RideOffer(RideSource.UBER, windowId, "UberX", BigDecimal("18.72"), 1.2, 3, 9.3, 19, 10.5, 22, 4.9, .9f, CaptureMethod.ACCESSIBILITY_TREE, at)
    @Test fun duplicateIsScopedToWindowAndTimeWindow() {
        val deduplicator = WindowOfferDeduplicator()
        assertFalse(deduplicator.isDuplicate(offer(1, 1_000)))
        assertTrue(deduplicator.isDuplicate(offer(1, 2_000)))
        assertFalse(deduplicator.isDuplicate(offer(2, 2_000)))
    }
    @Test fun expirationWaitsBeforeRemovingDisappearedWindow() {
        val expiry = OfferExpirationManager(650)
        expiry.markSeen(1, 1_000)
        assertTrue(expiry.expiredWindows(1_500, emptySet()).isEmpty())
        assertTrue(1 in expiry.expiredWindows(1_650, emptySet()))
    }
}
