package br.com.nexo.driver.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureRuntimeStateTest {
    @Test
    fun `active state only belongs to the process that owns media projection`() {
        val state = CaptureRuntimeState(isActive = true, ownerProcessId = 42)

        assertTrue(state.isActiveFor(42))
        assertFalse(state.isActiveFor(99))
    }

    @Test
    fun `inactive state is never restored as active`() {
        val state = CaptureRuntimeState(isActive = false, ownerProcessId = 42)

        assertFalse(state.isActiveFor(42))
    }
}
