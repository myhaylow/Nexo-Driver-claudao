package br.com.nexo.driver.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationContractsTest {
    private val now = 1_000_000L
    private val validator = LocationFixValidator(LocationFixPolicy(maxAgeMs = 60_000L, maxFutureSkewMs = 2_000L, maxAccuracyMeters = 60.0))

    @Test
    fun `validator rejects invalid stale future and inaccurate fixes`() {
        assertEquals(LocationFixRejection.INVALID, validator.rejectionFor(fix(latitude = 91.0), now))
        assertEquals(LocationFixRejection.STALE, validator.rejectionFor(fix(capturedAt = now - 60_001), now))
        assertEquals(LocationFixRejection.FUTURE, validator.rejectionFor(fix(capturedAt = now + 2_001), now))
        assertEquals(LocationFixRejection.INACCURATE, validator.rejectionFor(fix(accuracy = 60.1), now))
    }

    @Test
    fun `last known selector accepts a fix more than ten seconds newer before accuracy`() {
        val selector = LastKnownLocationSelector(validator)
        val selected = selector.select(
            listOf(
                fix(accuracy = 5.0, capturedAt = now - 20_000),
                fix(accuracy = 50.0, capturedAt = now - 9_000, provider = "network"),
                fix(accuracy = 1.0, capturedAt = now - 90_000),
            ),
            now,
        )

        assertEquals("network", selected?.provider)
        assertEquals(now - 9_000, selected?.capturedAtEpochMs)
    }

    @Test
    fun `last known selector returns null when no candidate is safe`() {
        assertNull(LastKnownLocationSelector(validator).select(listOf(fix(accuracy = 500.0)), now))
    }

    @Test
    fun `accumulator exposes the most recent usable live fix and expires it without persisting`() {
        val accumulator = LocationFixAccumulator(validator)
        val accurate = fix(accuracy = 5.0, capturedAt = now - 1_000)
        val lessAccurateNewer = fix(accuracy = 20.0, capturedAt = now)

        assertEquals(CurrentLocationState.Available(accurate), accumulator.offer(accurate, now))
        assertEquals(CurrentLocationState.Available(lessAccurateNewer), accumulator.offer(lessAccurateNewer, now))
        assertEquals(CurrentLocationState.Acquiring, accumulator.current(now + 60_001))
    }

    @Test
    fun `accumulator exposes rejected live update without discarding known good fix`() {
        val accumulator = LocationFixAccumulator(validator)
        val good = fix(accuracy = 5.0)
        accumulator.offer(good, now)

        assertEquals(CurrentLocationState.Rejected(LocationFixRejection.INACCURATE), accumulator.offer(fix(accuracy = 70.0), now))
        assertEquals(CurrentLocationState.Available(good), accumulator.current(now))
    }

    private fun fix(
        latitude: Double = -25.4284,
        longitude: Double = -49.2733,
        accuracy: Double = 10.0,
        capturedAt: Long = now,
        provider: String = "gps",
        elapsedNanos: Long = capturedAt * 1_000_000L,
        lastKnown: Boolean = false,
    ) = LocationFix(GeoPoint(latitude, longitude), accuracy, capturedAt, provider, elapsedNanos, isLastKnown = lastKnown)
}
