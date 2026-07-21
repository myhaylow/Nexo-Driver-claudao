package br.com.nexo.driver.overlay.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPreferencesTest {
    @Test
    fun `default grid matches the mockup card layout`() {
        // Visual do mockup: a nota fica no rodapé, então a grade não repete a avaliação.
        assertEquals(
            listOf(
                OverlayMetricField.RATE_PER_KM,
                OverlayMetricField.RATE_PER_HOUR,
                OverlayMetricField.NET_PROFIT_PERCENT,
                OverlayMetricField.NET_PROFIT,
            ),
            OverlayPreferences.DEFAULT.fields,
        )
        assertEquals(4, OverlayPreferences.DEFAULT.fields.distinct().size)
    }

    @Test
    fun `three field grids are allowed for the mockup field count toggle`() {
        val threeFields = OverlayPreferences(
            listOf(
                OverlayMetricField.RATE_PER_KM,
                OverlayMetricField.RATE_PER_HOUR,
                OverlayMetricField.NET_PROFIT,
            ),
        )
        assertEquals(3, threeFields.fields.size)
        assertEquals(threeFields, OverlayPreferencesCodec.decode(OverlayPreferencesCodec.encode(threeFields)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `configuration rejects duplicate fields`() {
        OverlayPreferences(
            listOf(
                OverlayMetricField.RATE_PER_KM,
                OverlayMetricField.RATE_PER_KM,
                OverlayMetricField.PASSENGER_RATING,
                OverlayMetricField.PICKUP,
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `replacing a slot with an already visible field is rejected`() {
        // RATE_PER_HOUR is already shown in another default slot, so reusing it must fail.
        OverlayPreferences.DEFAULT.withField(OverlaySlot.TOP_START, OverlayMetricField.RATE_PER_HOUR)
    }

    @Test
    fun `codec round trips a valid alternative grid`() {
        val configuration = OverlayPreferences(
            listOf(
                OverlayMetricField.TOTAL_DISTANCE,
                OverlayMetricField.TOTAL_DURATION,
                OverlayMetricField.PASSENGER_RATING,
                OverlayMetricField.RATE_PER_KM,
            ),
        )

        assertEquals(configuration, OverlayPreferencesCodec.decode(OverlayPreferencesCodec.encode(configuration)))
    }

    @Test
    fun `codec uses defaults for corrupt and duplicate payloads`() {
        assertEquals(OverlayPreferences.DEFAULT, OverlayPreferencesCodec.decode("invalid"))
        assertEquals(
            OverlayPreferences.DEFAULT,
            OverlayPreferencesCodec.decode(
                "overlay-preferences-v1:RATE_PER_KM,RATE_PER_KM,PICKUP,PASSENGER_RATING",
            ),
        )
    }

    @Test
    fun `in memory store saves whole validated configurations`() {
        val store = InMemoryOverlayPreferenceStore()
        val replacement = OverlayPreferences(
            listOf(
                OverlayMetricField.TOTAL_DURATION,
                OverlayMetricField.TOTAL_DISTANCE,
                OverlayMetricField.PASSENGER_RATING,
                OverlayMetricField.PICKUP,
            ),
        )

        assertEquals(replacement, store.save(replacement))
        assertEquals(replacement, store.load())
        assertTrue(store.load().fields.all { it != OverlayMetricField.RATE_PER_HOUR })
    }
}
