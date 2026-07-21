package br.com.nexo.driver.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTelemetryTest {
    @Test
    fun `computes burn rate speed and cost from real session numbers`() {
        // 30 km em 1h, carro de 10 km/L a R$ 6,00: 3 L usados, 3 L/h, 30 km/h, R$ 18,00.
        val telemetry = SessionTelemetry.compute(
            sessionKm = 30.0,
            sessionStartEpochMs = 0L,
            nowEpochMs = 3_600_000L,
            pricePerLiterCents = 600L,
            kilometersPerLiter = 10.0,
        )

        assertEquals(3.0, telemetry.litersUsed!!, 0.001)
        assertEquals(3.0, telemetry.burnRateLitersPerHour!!, 0.001)
        assertEquals(30.0, telemetry.averageSpeedKmh!!, 0.001)
        assertEquals(18.0, telemetry.fuelCostReais!!, 0.001)
        assertEquals(3_600_000L, telemetry.elapsedMs)
    }

    @Test
    fun `rates stay null before one minute of session to avoid noise`() {
        val telemetry = SessionTelemetry.compute(
            sessionKm = 0.5,
            sessionStartEpochMs = 0L,
            nowEpochMs = 30_000L,
            pricePerLiterCents = 600L,
            kilometersPerLiter = 10.0,
        )

        assertNull(telemetry.burnRateLitersPerHour)
        assertNull(telemetry.averageSpeedKmh)
        // Litros e custo já são reais mesmo cedo: derivam só do km.
        assertEquals(0.05, telemetry.litersUsed!!, 0.001)
    }

    @Test
    fun `everything except elapsed is null without kilometres`() {
        val telemetry = SessionTelemetry.compute(
            sessionKm = 0.0,
            sessionStartEpochMs = 0L,
            nowEpochMs = 3_600_000L,
            pricePerLiterCents = 600L,
            kilometersPerLiter = 10.0,
        )

        assertNull(telemetry.litersUsed)
        assertNull(telemetry.burnRateLitersPerHour)
        assertNull(telemetry.averageSpeedKmh)
        assertNull(telemetry.fuelCostReais)
        assertEquals(3_600_000L, telemetry.elapsedMs)
    }

    @Test
    fun `no session clock means no elapsed or rates`() {
        val telemetry = SessionTelemetry.compute(
            sessionKm = 12.0,
            sessionStartEpochMs = null,
            nowEpochMs = 3_600_000L,
            pricePerLiterCents = 600L,
            kilometersPerLiter = 10.0,
        )

        assertNull(telemetry.elapsedMs)
        assertNull(telemetry.burnRateLitersPerHour)
        // O gasto de combustível continua derivável do km sozinho.
        assertEquals(1.2, telemetry.litersUsed!!, 0.001)
    }

    @Test
    fun `session clock is idempotent across service rebinds`() {
        SessionTelemetryRepository.sessionEnded()
        SessionTelemetryRepository.sessionStarted(nowEpochMs = 1_000L)
        SessionTelemetryRepository.sessionStarted(nowEpochMs = 9_000L)

        assertEquals(1_000L, SessionTelemetryRepository.currentSessionStart())
        SessionTelemetryRepository.sessionEnded()
        assertNull(SessionTelemetryRepository.currentSessionStart())
    }
}
