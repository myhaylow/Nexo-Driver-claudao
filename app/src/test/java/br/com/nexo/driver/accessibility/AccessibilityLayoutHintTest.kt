package br.com.nexo.driver.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityLayoutHintTest {
    @Test
    fun `recognizes Uber Driver package when card omits UberX label`() {
        assertEquals("uber", accessibilityLayoutHint("com.ubercab.driver"))
    }

    @Test
    fun `recognizes known 99 driver package families`() {
        assertEquals("99", accessibilityLayoutHint("com.taxis99.driver"))
        assertEquals("99", accessibilityLayoutHint("com.app99.driver"))
        assertEquals("99", accessibilityLayoutHint("com.didiglobal.driver"))
    }

    @Test
    fun `does not classify unrelated packages`() {
        assertNull(accessibilityLayoutHint("br.com.nexo.driver"))
    }
}
