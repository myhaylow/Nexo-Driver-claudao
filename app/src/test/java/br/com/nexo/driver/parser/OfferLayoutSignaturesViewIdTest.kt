package br.com.nexo.driver.parser

import br.com.nexo.driver.offer.OfferSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferLayoutSignaturesViewIdTest {
    @Test
    fun `Uber upfront-offer resource id resolves to Uber`() {
        assertTrue(OfferLayoutSignatures.hasUberViewIdAnchor("com.ubercab.driver:id/ub__upfront_offer_pickup_label"))
        assertEquals(
            OfferSource.UBER,
            OfferLayoutSignatures.sourceForViewId("com.ubercab.driver:id/driver_offers_job_board_recycler_view"),
        )
    }

    @Test
    fun `99 broadorder resource id resolves to 99`() {
        assertTrue(OfferLayoutSignatures.hasNinetyNineViewIdAnchor("com.app99.driver:id/eta_value_pickup"))
        assertEquals(
            OfferSource.NINETY_NINE,
            OfferLayoutSignatures.sourceForViewId("com.app99.driver:id/broadorder_container"),
        )
    }

    @Test
    fun `the app package prefix is ignored - only the entry name matters`() {
        // A repackaged/cloned build keeps the entry name; only the prefix differs.
        assertTrue(OfferLayoutSignatures.hasUberViewIdAnchor("com.example.mirror:id/cards_tray_v2"))
    }

    @Test
    fun `unrelated or blank ids resolve to nothing`() {
        assertFalse(OfferLayoutSignatures.hasUberViewIdAnchor("com.ubercab.driver:id/map_zoom_button"))
        assertFalse(OfferLayoutSignatures.hasNinetyNineViewIdAnchor(null))
        assertFalse(OfferLayoutSignatures.hasUberViewIdAnchor(""))
        assertNull(OfferLayoutSignatures.sourceForViewId("android:id/content"))
    }

    @Test
    fun `99 wins when both providers appear, matching text inference`() {
        // sourceForViewId inspects one id at a time; this documents the per-id 99-first order.
        assertEquals(
            OfferSource.NINETY_NINE,
            OfferLayoutSignatures.sourceForViewId("com.app99.driver:id/trippicker_detail"),
        )
    }
}
