package br.com.nexo.driver.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayAutoDismissControllerTest {
    @Test
    fun `schedules auto-dismiss at the default display interval`() {
        val scheduler = FakeScheduler()
        var dismissals = 0
        val controller = controller(scheduler) { dismissals++ }

        controller.restart()

        assertEquals(OverlayAutoDismissController.DEFAULT_VISIBLE_DURATION_MS, scheduler.lastDelayMs)
        scheduler.runPending()
        assertEquals(1, dismissals)
    }

    @Test
    fun `honours a driver-configured display interval on the next card`() {
        val scheduler = FakeScheduler()
        val controller = controller(scheduler) {}

        controller.timeoutMs = 16_000L
        controller.restart()

        assertEquals(16_000L, scheduler.lastDelayMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a display interval beyond the supported maximum`() {
        controller(FakeScheduler()) {}.timeoutMs =
            OverlayAutoDismissController.MAX_VISIBLE_DURATION_MS + 1
    }

    @Test
    fun `a new show restarts the full display interval and invalidates the old callback`() {
        val scheduler = FakeScheduler()
        var dismissals = 0
        val controller = controller(scheduler) { dismissals++ }

        controller.restart()
        val first = scheduler.lastScheduledAction
        controller.restart()
        val second = scheduler.lastScheduledAction

        first.invoke()
        assertEquals(0, dismissals)
        assertTrue(scheduler.cancelCalled)
        assertEquals(OverlayAutoDismissController.DEFAULT_VISIBLE_DURATION_MS, scheduler.lastDelayMs)
        second.invoke()
        assertEquals(1, dismissals)
    }

    @Test
    fun `cancel prevents every pending timeout from dismissing later`() {
        val scheduler = FakeScheduler()
        var dismissals = 0
        val controller = controller(scheduler) { dismissals++ }

        controller.restart()
        val pending = scheduler.lastScheduledAction
        controller.cancel()
        pending.invoke()

        assertEquals(0, dismissals)
        assertTrue(scheduler.cancelCalled)
        assertFalse(scheduler.hasPending)
    }

    private fun controller(scheduler: FakeScheduler, onTimeout: () -> Unit) =
        OverlayAutoDismissController(
            postDelayed = scheduler::postDelayed,
            cancelPending = scheduler::cancel,
            onTimeout = onTimeout,
        )

    private class FakeScheduler {
        var lastDelayMs: Long? = null
        lateinit var lastScheduledAction: () -> Unit
        var cancelCalled = false
        var hasPending = false

        fun postDelayed(delayMs: Long, action: () -> Unit) {
            lastDelayMs = delayMs
            lastScheduledAction = action
            hasPending = true
        }

        fun cancel() {
            cancelCalled = true
            hasPending = false
        }

        fun runPending() {
            check(hasPending)
            hasPending = false
            lastScheduledAction.invoke()
        }
    }
}
