package br.com.nexo.driver.analysis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveOfferUpdateGateTest {
    @Test
    fun acceptsOnlyCurrentOfferInsideOriginalLifetime() {
        val gate = ActiveOfferUpdateGate(lifetimeMs = 8_000L)
        val first = gate.open(nowElapsedMs = 1_000L)
        assertTrue(gate.accepts(first, 8_999L))
        assertFalse(gate.accepts(first, 9_000L))

        val second = gate.open(nowElapsedMs = 10_000L)
        assertFalse(gate.accepts(first, 10_001L))
        assertTrue(gate.accepts(second, 10_001L))
    }
}
