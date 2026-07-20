package br.com.nexo.driver.evaluation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the invariant that made it possible to delete roughly fifteen exhaustive `when(metric)`
 * expressions: every metric carries its own label, unit and placement. A new entry that forgets
 * one of these no longer breaks a screen at runtime -- it fails here.
 */
class MetricMetadataTest {

    @Test
    fun `every metric declares non-blank labels`() {
        Metric.entries.forEach { metric ->
            assertTrue("${metric.name} has a blank label", metric.label.isNotBlank())
            assertTrue("${metric.name} has a blank displayName", metric.displayName.isNotBlank())
        }
    }

    @Test
    fun `numeric metrics have a usable scale and flags do not pretend to`() {
        Metric.entries.forEach { metric ->
            if (metric.unit.isNumeric) {
                assertTrue("${metric.name} needs a positive scale", metric.unit.scale > 0L)
                assertTrue("${metric.name} needs a unit symbol", metric.unit.symbol.isNotBlank())
            } else {
                assertEquals("${metric.name} is a flag and needs no symbol", "", metric.unit.symbol)
            }
        }
    }

    @Test
    fun `scales match the units the evaluator actually stores`() {
        // These are the conversions the parser and evaluator commit to: money in cents, distance
        // in metres, duration in seconds, rating x100. A drift here silently rescales every
        // target a driver has already saved.
        assertEquals(100L, Metric.PAYOUT.unit.scale)
        assertEquals(1_000L, Metric.TOTAL_DISTANCE.unit.scale)
        assertEquals(60L, Metric.TOTAL_DURATION.unit.scale)
        assertEquals(100L, Metric.PASSENGER_RATING.unit.scale)
    }

    @Test
    fun `only engine-owned metrics are hidden from the filter picker`() {
        val hidden = Metric.entries.filterNot { it.isUserConfigurable }

        assertEquals(listOf(Metric.PICKUP_IS_BLOCKED), hidden)
    }

    @Test
    fun `the rating band is absolute because a percentage band is meaningless for it`() {
        assertNotNull(Metric.PASSENGER_RATING.defaultToleranceAbsolute)
        // Every other metric is happy with the percentage band.
        Metric.entries.filterNot { it == Metric.PASSENGER_RATING }.forEach { metric ->
            assertEquals(
                "${metric.name} unexpectedly declares an absolute band",
                null,
                metric.defaultToleranceAbsolute,
            )
        }
    }

    @Test
    fun `metrics are uniquely ordered within their group`() {
        // Duplicate order values inside a group make the filter list's sort unstable, so two rules
        // can swap places between recompositions for no visible reason.
        Metric.entries.groupBy { it.group }.forEach { (group, metrics) ->
            val orders = metrics.map { it.order }
            assertEquals("$group has duplicate display orders: $orders", orders.size, orders.toSet().size)
        }
    }
}
