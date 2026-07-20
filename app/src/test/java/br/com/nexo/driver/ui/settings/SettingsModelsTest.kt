package br.com.nexo.driver.ui.settings

import br.com.nexo.driver.overlay.preferences.OverlayMetricField
import br.com.nexo.driver.overlay.preferences.OverlayPreferences
import br.com.nexo.driver.overlay.preferences.OverlaySlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsModelsTest {
    @Test
    fun `overlay selector keeps the current field and hides fields used elsewhere`() {
        val choices = OverlayPreferences.DEFAULT.availableFieldsFor(OverlaySlot.TOP_START)

        assertEquals(OverlayMetricField.RATE_PER_KM, choices.first())
        // Fields already used by the default grid are hidden from other slots.
        assertFalse(OverlayMetricField.RATE_PER_HOUR in choices)
        assertFalse(OverlayMetricField.PASSENGER_RATING in choices)
        assertFalse(OverlayMetricField.NET_PROFIT in choices)
        assertEquals(
            setOf(
                OverlayMetricField.RATE_PER_KM,
                OverlayMetricField.RATE_PER_MINUTE,
                OverlayMetricField.PICKUP,
                OverlayMetricField.TOTAL_DURATION,
                OverlayMetricField.TOTAL_DISTANCE,
                OverlayMetricField.PAYOUT,
                OverlayMetricField.NET_PROFIT_PERCENT,
                OverlayMetricField.NET_PROFIT_PER_HOUR,
            ),
            choices.toSet(),
        )
    }
}
