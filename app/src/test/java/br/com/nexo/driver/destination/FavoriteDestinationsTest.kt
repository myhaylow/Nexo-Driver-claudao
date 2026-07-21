package br.com.nexo.driver.destination

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteDestinationsTest {

    private fun destination(label: String) = HomeDestination(
        coordinate = GeoCoordinate(-25.43, -49.27),
        label = label,
        arrivalRadiusMeters = 2_000.0,
        enabled = true,
        resolutionStatus = DestinationResolutionStatus.RESOLVED,
    )

    @Test
    fun `updating the active favorite replaces only that entry`() {
        val favorites = FavoriteDestinations(
            destinations = listOf(destination("Casa"), destination("Base")),
            activeIndex = 1,
        ).withUpdatedActive(destination("Base Nova"))

        assertEquals("Casa", favorites.destinations[0].label)
        assertEquals("Base Nova", favorites.destinations[1].label)
    }

    @Test
    fun `adding selects the new favorite and respects the cap`() {
        var favorites = FavoriteDestinations()
        repeat(7) { index -> favorites = favorites.withAdded(destination("D$index")) }

        assertEquals(FavoriteDestinations.MAX_FAVORITES, favorites.destinations.size)
        assertEquals(FavoriteDestinations.MAX_FAVORITES - 1, favorites.activeIndex)
    }

    @Test
    fun `removing the active favorite keeps a valid selection`() {
        val favorites = FavoriteDestinations(
            destinations = listOf(destination("Casa"), destination("Base")),
            activeIndex = 1,
        ).withRemovedActive()

        assertEquals(1, favorites.destinations.size)
        assertEquals(0, favorites.activeIndex)
        assertEquals("Casa", favorites.active?.label)
    }

    @Test
    fun `removing the last favorite leaves an empty list without crashing`() {
        val favorites = FavoriteDestinations(listOf(destination("Casa")), 0).withRemovedActive()

        assertEquals(0, favorites.destinations.size)
        assertNull(favorites.active)
    }

    @Test
    fun `updating with an empty list creates the first favorite`() {
        val favorites = FavoriteDestinations().withUpdatedActive(destination("Casa"))

        assertEquals(1, favorites.destinations.size)
        assertEquals("Casa", favorites.active?.label)
    }
}
