package br.com.nexo.driver.offer

import org.junit.Assert.assertThrows
import org.junit.Test

class OfferModelsTest {
    @Test
    fun `Money accepts zero and positive cents`() {
        Money(0)
        Money(1_000)
    }

    @Test
    fun `Money rejects negative cents`() {
        assertThrows(IllegalArgumentException::class.java) { Money(-1) }
    }

    @Test
    fun `Distance accepts zero and positive meters`() {
        Distance(0)
        Distance(500)
    }

    @Test
    fun `Distance rejects negative meters`() {
        assertThrows(IllegalArgumentException::class.java) { Distance(-1) }
    }

    @Test
    fun `Duration accepts zero and positive seconds`() {
        Duration(0)
        Duration(60)
    }

    @Test
    fun `Duration rejects negative seconds`() {
        assertThrows(IllegalArgumentException::class.java) { Duration(-1) }
    }
}
