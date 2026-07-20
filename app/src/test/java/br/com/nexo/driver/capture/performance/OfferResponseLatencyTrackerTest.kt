package br.com.nexo.driver.capture.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferResponseLatencyTrackerTest {
    @Test
    fun `empty snapshot has no percentiles or violations`() {
        val snapshot = OfferResponseLatencyTracker().snapshot()

        assertEquals(1_000L, snapshot.targetMillis)
        assertEquals(0, snapshot.sampleCount)
        assertNull(snapshot.latestMillis)
        assertNull(snapshot.p50Millis)
        assertNull(snapshot.p95Millis)
        assertNull(snapshot.maxMillis)
        assertEquals(0, snapshot.violationCount)
        assertEquals(0.0, snapshot.violationRate, 0.0)
        assertTrue(snapshot.meetsTarget)
    }

    @Test
    fun `collects nearest-rank percentiles and budget violations`() {
        val tracker = OfferResponseLatencyTracker(targetMillis = 1_000L)
        listOf(100L, 300L, 500L, 900L, 1_000L, 1_250L).forEachIndexed { index, latency ->
            tracker.record(frameReceivedAtMillis = index * 10_000L, overlayShownAtMillis = index * 10_000L + latency)
        }

        val snapshot = tracker.snapshot()

        assertEquals(6, snapshot.sampleCount)
        assertEquals(1_250L, snapshot.latestMillis)
        assertEquals(500L, snapshot.p50Millis)
        assertEquals(1_250L, snapshot.p95Millis)
        assertEquals(1_250L, snapshot.maxMillis)
        assertEquals(1, snapshot.violationCount)
        assertEquals(1.0 / 6.0, snapshot.violationRate, 0.0001)
        assertFalse(snapshot.meetsTarget)
    }

    @Test
    fun `keeps only the most recent rolling window`() {
        val tracker = OfferResponseLatencyTracker(targetMillis = 1_000L, windowSize = 3)
        tracker.record(0L, 1_500L)
        tracker.record(10_000L, 10_100L)
        tracker.record(20_000L, 20_200L)
        val snapshot = tracker.record(30_000L, 30_300L)

        assertEquals(3, snapshot.sampleCount)
        assertEquals(300L, snapshot.latestMillis)
        assertEquals(200L, snapshot.p50Millis)
        assertEquals(300L, snapshot.p95Millis)
        assertEquals(300L, snapshot.maxMillis)
        assertEquals(0, snapshot.violationCount)
        assertTrue(snapshot.meetsTarget)
    }

    @Test
    fun `convenience overload uses injected monotonic clock`() {
        val tracker = OfferResponseLatencyTracker(nowMillis = { 42_500L })

        val snapshot = tracker.recordOverlayShown(frameReceivedAtMillis = 42_000L)

        assertEquals(500L, snapshot.latestMillis)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects timestamps that go backwards`() {
        OfferResponseLatencyTracker().record(frameReceivedAtMillis = 2_000L, overlayShownAtMillis = 1_999L)
    }
}
