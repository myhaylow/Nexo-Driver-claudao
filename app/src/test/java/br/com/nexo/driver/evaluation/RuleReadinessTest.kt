package br.com.nexo.driver.evaluation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the state observed live on a Galaxy S23: the "termina perto de casa" rule was enabled
 * with a home destination configured, but no offline address package had been imported, so it
 * never resolved on a single offer while quietly costing 12.5% of rule coverage.
 */
class RuleReadinessTest {

    private val ready = RulePrerequisites(
        hasHomeDestination = true,
        hasOfflineAddressPackage = true,
        isBlocklistEnabled = true,
    )

    private fun rule(metric: Metric, enabled: Boolean = true) =
        FilterRule(metric, Comparator.IS_TRUE, enabled = enabled)

    @Test
    fun `ends near home is blocked without the offline package`() {
        val blocker = rule(Metric.ENDS_NEAR_HOME).blocker(ready.copy(hasOfflineAddressPackage = false))

        assertEquals(RuleBlocker.MISSING_OFFLINE_ADDRESS_PACKAGE, blocker)
    }

    @Test
    fun `a missing destination is reported before a missing package`() {
        // Telling someone to import an address package when they have not chosen a destination
        // points at the second step, not the one that is actually blocking them.
        val blocker = rule(Metric.ENDS_NEAR_HOME).blocker(
            ready.copy(hasHomeDestination = false, hasOfflineAddressPackage = false),
        )

        assertEquals(RuleBlocker.MISSING_HOME_DESTINATION, blocker)
    }

    @Test
    fun `a fully configured rule reports nothing`() {
        assertNull(rule(Metric.ENDS_NEAR_HOME).blocker(ready))
        assertNull(rule(Metric.IS_TOWARD_DESTINATION).blocker(ready))
        assertNull(rule(Metric.PICKUP_IS_BLOCKED).blocker(ready))
    }

    @Test
    fun `a disabled rule is never flagged`() {
        // It already reads as inactive on screen; flagging it too would be noise.
        val blocker = rule(Metric.ENDS_NEAR_HOME, enabled = false)
            .blocker(ready.copy(hasOfflineAddressPackage = false))

        assertNull(blocker)
    }

    @Test
    fun `the blocklist rule is blocked while the toggle is off`() {
        assertEquals(
            RuleBlocker.BLOCKLIST_DISABLED,
            rule(Metric.PICKUP_IS_BLOCKED).blocker(ready.copy(isBlocklistEnabled = false)),
        )
    }

    @Test
    fun `metrics without prerequisites are never blocked`() {
        val unaffected = listOf(Metric.PAYOUT, Metric.RATE_PER_KM, Metric.PASSENGER_RATING)

        unaffected.forEach { metric ->
            assertNull(
                "$metric should not depend on prerequisites",
                FilterRule(metric, Comparator.AT_LEAST, target = 100L)
                    .blocker(RulePrerequisites(false, false, false)),
            )
        }
    }

    @Test
    fun `every blocker states the problem and offers a way out`() {
        RuleBlocker.entries.forEach { blocker ->
            assertEquals(
                "${blocker.name} summary must end as a sentence",
                true,
                blocker.summary.isNotBlank() && blocker.summary.trim().endsWith("."),
            )
            assertEquals(
                "${blocker.name} must offer a recovery action",
                true,
                !blocker.actionLabel.isNullOrBlank(),
            )
        }
    }
}
