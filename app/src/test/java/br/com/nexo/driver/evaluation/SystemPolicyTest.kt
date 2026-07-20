package br.com.nexo.driver.evaluation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The driver no longer sets weight, mode or tolerance; policy does. These pin that policy so a rule
 * built anywhere in the app carries consistent, system-decided values.
 */
class SystemPolicyTest {

    @Test
    fun `earnings and rating weigh more than trip totals`() {
        val earnings = listOf(
            Metric.PAYOUT, Metric.RATE_PER_KM, Metric.RATE_PER_HOUR, Metric.NET_PROFIT,
            Metric.PASSENGER_RATING,
        )
        val trip = listOf(Metric.TRIP_DISTANCE, Metric.TRIP_DURATION, Metric.TOTAL_DISTANCE, Metric.TOTAL_DURATION)

        earnings.forEach { e ->
            trip.forEach { t ->
                assertTrue("${e.name} should outweigh ${t.name}", e.systemWeight > t.systemWeight)
            }
        }
    }

    @Test
    fun `only a blocked pickup is eliminatory`() {
        Metric.entries.forEach { metric ->
            val expected = if (metric == Metric.PICKUP_IS_BLOCKED) EvaluationMode.ELIMINATORY else EvaluationMode.SCORE
            assertEquals(metric.name, expected, metric.systemMode)
        }
    }

    @Test
    fun `withSystemPolicy overwrites any hand-set values`() {
        val handTuned = FilterRule(
            metric = Metric.RATE_PER_KM,
            comparator = Comparator.AT_LEAST,
            target = 180L,
            tolerancePercent = 40,
            weight = 9,
            mode = EvaluationMode.ELIMINATORY,
        )

        val stamped = handTuned.withSystemPolicy()

        assertEquals(Metric.RATE_PER_KM.systemWeight, stamped.weight)
        assertEquals(EvaluationMode.SCORE, stamped.mode)
        assertEquals(SYSTEM_TOLERANCE_PERCENT, stamped.tolerancePercent)
        assertEquals(null, stamped.toleranceAbsolute)
        // The driver's actual choices -- metric, bound, value -- are untouched.
        assertEquals(180L, stamped.target)
        assertEquals(Comparator.AT_LEAST, stamped.comparator)
    }

    @Test
    fun `every weight is positive so the evaluator's require holds`() {
        Metric.entries.forEach { metric ->
            assertTrue("${metric.name} weight must be positive", metric.systemWeight > 0)
        }
    }
}
