package br.com.nexo.driver.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationMovementAccumulatorTest {
    @Test
    fun `last known and first live fix do not add session distance`() {
        val accumulator = LocationMovementAccumulator()
        accumulator.offer(fix(latitude = -25.4284, elapsedNanos = 1L, lastKnown = true))
        val firstLive = accumulator.offer(fix(latitude = -25.4184, elapsedNanos = 2_000_000_000L))

        assertEquals(0.0, firstLive.sessionDistanceMeters, 0.001)
    }

    @Test
    fun `ignores accuracy based jitter and then adds plausible live distance`() {
        val accumulator = LocationMovementAccumulator()
        accumulator.offer(fix(elapsedNanos = 1_000_000_000L, accuracy = 10.0))
        val jitter = accumulator.offer(fix(latitude = -25.42842, elapsedNanos = 2_000_000_000L, accuracy = 10.0))
        val moved = accumulator.offer(fix(latitude = -25.42815, elapsedNanos = 12_000_000_000L, accuracy = 10.0))

        assertTrue(jitter.ignoredAsJitter)
        assertEquals(0.0, jitter.sessionDistanceMeters, 0.001)
        assertFalse(moved.ignoredAsJitter)
        assertTrue(moved.addedDistanceMeters > 20.0)
        assertEquals(moved.addedDistanceMeters, moved.sessionDistanceMeters, 0.001)
    }

    @Test
    fun `rejects reported and calculated speeds above one hundred seventy kmh`() {
        val reported = LocationMovementAccumulator()
        reported.offer(fix(elapsedNanos = 1_000_000_000L))
        val reportedResult = reported.offer(fix(elapsedNanos = 2_000_000_000L, speedMps = 48.0))
        assertEquals(LocationMovementRejection.REPORTED_SPEED_OUTLIER, reportedResult.rejection)

        val calculated = LocationMovementAccumulator()
        calculated.offer(fix(elapsedNanos = 1_000_000_000L))
        val calculatedResult = calculated.offer(fix(latitude = -25.4000, elapsedNanos = 2_000_000_000L))
        assertEquals(LocationMovementRejection.CALCULATED_SPEED_OUTLIER, calculatedResult.rejection)
    }

    @Test
    fun `rejects movement with non monotonic elapsed realtime`() {
        val accumulator = LocationMovementAccumulator()
        accumulator.offer(fix(elapsedNanos = 3_000_000_000L))
        val result = accumulator.offer(fix(latitude = -25.4270, elapsedNanos = 2_000_000_000L))

        assertEquals(LocationMovementRejection.NON_MONOTONIC_CLOCK, result.rejection)
    }

    private fun fix(
        latitude: Double = -25.4284,
        accuracy: Double = 10.0,
        elapsedNanos: Long,
        speedMps: Double? = null,
        lastKnown: Boolean = false,
    ) = LocationFix(
        point = GeoPoint(latitude, -49.2733),
        accuracyMeters = accuracy,
        capturedAtEpochMs = 1_000_000L,
        provider = "gps",
        elapsedRealtimeNanos = elapsedNanos,
        speedMps = speedMps,
        isLastKnown = lastKnown,
    )
}
