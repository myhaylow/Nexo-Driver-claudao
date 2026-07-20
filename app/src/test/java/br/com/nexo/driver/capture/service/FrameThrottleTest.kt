package br.com.nexo.driver.capture.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameThrottleTest {
    @Test
    fun `accepts first frame and limits frames inside interval`() {
        val throttle = FrameThrottle(minimumIntervalNanos = 100L)

        assertTrue(throttle.tryAcquire(1_000L))
        assertFalse(throttle.tryAcquire(1_099L))
        assertTrue(throttle.tryAcquire(1_100L))
    }

    @Test
    fun `rejects timestamp that moves backwards`() {
        val throttle = FrameThrottle(minimumIntervalNanos = 0L)

        assertTrue(throttle.tryAcquire(500L))
        assertFalse(throttle.tryAcquire(499L))
    }

    @Test
    fun `reset permits the next frame regardless of prior timestamp`() {
        val throttle = FrameThrottle(minimumIntervalNanos = 100L)

        assertTrue(throttle.tryAcquire(500L))
        throttle.reset()

        assertTrue(throttle.tryAcquire(1L))
    }
}
