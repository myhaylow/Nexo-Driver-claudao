package br.com.nexo.driver.destination

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeocoderDestinationResolverTest {
    @Test fun `rejects generic accessibility destinations`() {
        assertFalse(GeocoderDestinationResolver.isUseful("destino"))
        assertFalse(GeocoderDestinationResolver.isUseful("detectado pelo Android"))
        assertFalse(GeocoderDestinationResolver.isUseful("teste de overlay"))
    }

    @Test fun `accepts a useful address`() {
        assertTrue(GeocoderDestinationResolver.isUseful("Rua XV de Novembro, Curitiba"))
    }
}
