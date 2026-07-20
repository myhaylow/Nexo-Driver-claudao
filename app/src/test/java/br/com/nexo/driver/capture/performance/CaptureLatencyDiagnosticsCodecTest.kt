package br.com.nexo.driver.capture.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureLatencyDiagnosticsCodecTest {
    @Test
    fun `round trip preserves only aggregate latency evidence`() {
        val diagnostics = CaptureLatencyDiagnostics(
            isSessionActive = false,
            updatedAtEpochMillis = 1_735_000_000_000L,
            snapshot = OfferResponseLatencySnapshot(
                targetMillis = 1_000L,
                windowSize = 100,
                sampleCount = 23,
                latestMillis = 420L,
                p50Millis = 300L,
                p95Millis = 880L,
                maxMillis = 1_210L,
                violationCount = 1,
            ),
        )

        val encoded = CaptureLatencyDiagnosticsCodec.encode(diagnostics)
        val restored = CaptureLatencyDiagnosticsCodec.decode(encoded)

        assertEquals(diagnostics, restored)
        assertTrue(restored.hasMeasurements)
        assertTrue(restored.hasRepresentativeP95)
        assertFalse(restored.targetMetInObservedWindow)
        assertFalse(encoded.keys.any { it.contains("text") || it.contains("image") || it.contains("offer") })
    }

    @Test
    fun `empty diagnostics never claim that the latency target was observed`() {
        val diagnostics = CaptureLatencyDiagnostics.empty(isSessionActive = true)

        assertFalse(diagnostics.hasMeasurements)
        assertFalse(diagnostics.targetMetInObservedWindow)
        assertFalse(diagnostics.hasRepresentativeP95)
        assertNull(diagnostics.snapshot.p95Millis)
        assertNull(diagnostics.snapshot.maxMillis)
    }

    @Test
    fun `corrupt partial measurement is discarded rather than creating a false p95`() {
        val restored = CaptureLatencyDiagnosticsCodec.decode(
            mapOf(
                CaptureLatencyDiagnosticsCodec.KEY_SESSION_ACTIVE to 1L,
                CaptureLatencyDiagnosticsCodec.KEY_TARGET_MILLIS to 1_000L,
                CaptureLatencyDiagnosticsCodec.KEY_WINDOW_SIZE to 100L,
                CaptureLatencyDiagnosticsCodec.KEY_SAMPLE_COUNT to 50L,
                CaptureLatencyDiagnosticsCodec.KEY_P95_MILLIS to 800L,
            ),
        )

        assertEquals(0, restored.snapshot.sampleCount)
        assertNull(restored.snapshot.p95Millis)
        assertFalse(restored.targetMetInObservedWindow)
    }

    @Test
    fun `decoder bounds invalid counts and keeps valid aggregate values`() {
        val restored = CaptureLatencyDiagnosticsCodec.decode(
            mapOf(
                CaptureLatencyDiagnosticsCodec.KEY_SESSION_ACTIVE to 1L,
                CaptureLatencyDiagnosticsCodec.KEY_UPDATED_AT_EPOCH_MILLIS to 50L,
                CaptureLatencyDiagnosticsCodec.KEY_TARGET_MILLIS to 1_000L,
                CaptureLatencyDiagnosticsCodec.KEY_WINDOW_SIZE to 3L,
                CaptureLatencyDiagnosticsCodec.KEY_SAMPLE_COUNT to 9L,
                CaptureLatencyDiagnosticsCodec.KEY_LATEST_MILLIS to 100L,
                CaptureLatencyDiagnosticsCodec.KEY_P50_MILLIS to 100L,
                CaptureLatencyDiagnosticsCodec.KEY_P95_MILLIS to 1_200L,
                CaptureLatencyDiagnosticsCodec.KEY_MAX_MILLIS to 1_200L,
                CaptureLatencyDiagnosticsCodec.KEY_VIOLATION_COUNT to 9L,
            ),
        )

        assertEquals(3, restored.snapshot.sampleCount)
        assertEquals(3, restored.snapshot.violationCount)
        assertEquals(1_200L, restored.snapshot.p95Millis)
        assertTrue(restored.hasMeasurements)
    }
}
