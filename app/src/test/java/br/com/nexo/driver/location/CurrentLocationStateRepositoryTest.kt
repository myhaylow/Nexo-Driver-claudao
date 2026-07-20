package br.com.nexo.driver.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CurrentLocationStateRepositoryTest {
    @Test
    fun `repository exposes display metadata but never coordinates to observers`() {
        val observed = mutableListOf<CurrentLocationServiceSnapshot>()
        val subscription = CurrentLocationStateRepository.subscribe(observed::add)
        try {
            CurrentLocationStateRepository.update(
                CurrentLocationState.Available(
                    LocationFix(
                        point = GeoPoint(-25.4284, -49.2733),
                        accuracyMeters = 5.0,
                        capturedAtEpochMs = 1L,
                        provider = "gps",
                        elapsedRealtimeNanos = 1L,
                    ),
                    sessionDistanceMeters = 123.4,
                ),
            )

            assertEquals(CurrentLocationServiceStatus.ACTIVE, observed.last().status)
            assertEquals(123.4, observed.last().sessionDistanceMeters, 0.001)
            assertEquals("gps", observed.last().provider)
            assertEquals(5.0, observed.last().accuracyMeters!!, 0.001)
            assertEquals(1L, observed.last().fixEpochMs)
            assertFalse(observed.last().isLastKnown)
            assertFalse(
                CurrentLocationServiceSnapshot::class.java.declaredFields.any {
                    it.name.contains("point", true) || it.name.contains("latitude", true) || it.name.contains("longitude", true)
                },
            )
        } finally {
            subscription.close()
            CurrentLocationStateRepository.update(CurrentLocationState.Idle)
        }
    }
}
