package br.com.nexo.driver.capture.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSessionGuardTest {
    @Test
    fun invalidationRejectsLateWorkFromStoppedSession() {
        val guard = CaptureSessionGuard()
        val session = guard.begin()

        assertTrue(guard.isActive(session))
        guard.invalidate()

        assertFalse(guard.isActive(session))
    }

    @Test
    fun newSessionRejectsResultsFromPreviousSession() {
        val guard = CaptureSessionGuard()
        val previous = guard.begin()
        val current = guard.begin()

        assertFalse(guard.isActive(previous))
        assertTrue(guard.isActive(current))
    }
}
