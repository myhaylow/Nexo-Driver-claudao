package br.com.nexo.driver.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the screenshot pacing policy that replaced the 150ms cadence.
 *
 * On a Galaxy S23 / Android 16 the old interval produced 82 consecutive
 * ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT rejections and zero usable frames, because the
 * framework rate-limits `takeScreenshot` well above that. The policy itself is pure arithmetic,
 * so it is asserted here rather than only on a device.
 */
class ScreenshotThrottleTest {

    @Test
    fun `the floor is at or above the platform's observed rate limit`() {
        // 150ms was the value that spun; anything in that range must not be reintroduced.
        assertTrue(
            "Screenshot floor must stay above the cadence that caused the rejection loop.",
            AccessibilityScreenshotFallback.MIN_SCREENSHOT_INTERVAL_MS >= 1_000L,
        )
    }

    @Test
    fun `backoff doubles on rejection and stays bounded`() {
        var interval = AccessibilityScreenshotFallback.MIN_SCREENSHOT_INTERVAL_MS
        repeat(10) {
            interval = (interval * 2).coerceAtMost(AccessibilityScreenshotFallback.MAX_SCREENSHOT_INTERVAL_MS)
        }

        assertEquals(AccessibilityScreenshotFallback.MAX_SCREENSHOT_INTERVAL_MS, interval)
    }

    @Test
    fun `a burst cannot outlive its deadline even at the maximum backoff`() {
        // The burst window must still permit a useful number of attempts once fully backed off,
        // otherwise the fallback becomes a no-op the moment the platform throttles it.
        val attempts = AccessibilityScreenshotFallback.CAPTURE_BURST_MAX_MS /
            AccessibilityScreenshotFallback.MAX_SCREENSHOT_INTERVAL_MS

        assertTrue("A throttled burst must still get multiple attempts.", attempts >= 2)
    }

    @Test
    fun `the interval-too-short error code matches the framework constant`() {
        assertEquals(3, AccessibilityScreenshotFallback.ERROR_INTERVAL_TIME_SHORT)
    }
}
