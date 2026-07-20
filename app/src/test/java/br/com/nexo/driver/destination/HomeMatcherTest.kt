package br.com.nexo.driver.destination

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMatcherTest {
    private val home = HomeDestination(
        coordinate = GeoCoordinate(-25.4284, -49.2733),
        arrivalRadiusMeters = 200.0,
        resolutionStatus = DestinationResolutionStatus.RESOLVED,
    )

    @Test
    fun `matches a dropoff inside the configured radius`() {
        val result = HomeMatcher().match(
            home,
            OfferDestination(
                coordinate = GeoCoordinate(-25.4285, -49.2733),
                resolutionStatus = DestinationResolutionStatus.RESOLVED,
            ),
        )

        assertEquals(HomeMatchStatus.ENDS_NEAR_HOME, result.status)
        assertTrue(result.endsNearHome == true)
        assertTrue(requireNotNull(result.distanceToHomeMeters) < 200.0)
    }

    @Test
    fun `marks a valid remote dropoff as outside home radius`() {
        val result = HomeMatcher().match(
            home,
            OfferDestination(
                coordinate = GeoCoordinate(-25.4384, -49.2733),
                resolutionStatus = DestinationResolutionStatus.RESOLVED,
            ),
        )

        assertEquals(HomeMatchStatus.ENDS_AWAY_FROM_HOME, result.status)
        assertFalse(result.endsNearHome == true)
        assertTrue(requireNotNull(result.distanceToHomeMeters) > 200.0)
    }

    @Test
    fun `trusted coordinates outside radius override matching text`() {
        val address = "Rua das Flores, 123, Curitiba"
        val result = HomeMatcher().match(
            home.copy(originalAddress = address),
            OfferDestination(
                coordinate = GeoCoordinate(-25.4384, -49.2733),
                originalAddress = address,
                resolutionStatus = DestinationResolutionStatus.RESOLVED,
            ),
        )

        assertEquals(HomeMatchStatus.ENDS_AWAY_FROM_HOME, result.status)
        assertEquals(HomeMatchMethod.GEO_DISTANCE, result.method)
    }

    @Test
    fun `does not infer a result when home or dropoff is unavailable`() {
        assertEquals(
            HomeMatchStatus.UNKNOWN,
            HomeMatcher().match(null, OfferDestination(coordinate = home.coordinate, resolutionStatus = DestinationResolutionStatus.RESOLVED)).status,
        )
        val result = HomeMatcher().match(home, null)
        assertEquals(HomeMatchStatus.UNKNOWN, result.status)
        assertNull(result.endsNearHome)
    }

    @Test
    fun `never trusts a coordinate attached to failed or unavailable resolution`() {
        val unresolvedOffer = OfferDestination(
            coordinate = home.coordinate,
            originalAddress = "Rua diferente, 99",
            resolutionStatus = DestinationResolutionStatus.FAILED,
        )
        val unavailableHome = home.copy(
            resolutionStatus = DestinationResolutionStatus.UNAVAILABLE,
            originalAddress = "Casa indisponível",
        )

        assertEquals(HomeMatchStatus.UNKNOWN, HomeMatcher().match(home, unresolvedOffer).status)
        assertEquals(HomeMatchStatus.UNKNOWN, HomeMatcher().match(unavailableHome, OfferDestination(
            coordinate = home.coordinate,
            originalAddress = "Rua diferente, 99",
            resolutionStatus = DestinationResolutionStatus.RESOLVED,
        )).status)
    }

    @Test
    fun `uses exact normalized address only as fallback when geocodes are unavailable`() {
        val textualHome = HomeDestination(
            coordinate = null,
            label = "Casa",
            originalAddress = "Rua São João, 10, Curitiba",
            resolutionStatus = DestinationResolutionStatus.UNAVAILABLE,
            enabled = true,
        )
        val textualOffer = OfferDestination(
            originalAddress = "RUA SAO JOAO 10",
            resolutionStatus = DestinationResolutionStatus.FAILED,
        )

        val result = HomeMatcher().match(textualHome, textualOffer)

        assertEquals(HomeMatchStatus.ENDS_NEAR_HOME, result.status)
        assertEquals(HomeMatchMethod.EXACT_ADDRESS_TEXT, result.method)
        assertEquals(HomeMatchReason.EXACT_ADDRESS_MATCH, result.reason)
    }

    @Test
    fun `matches textual addresses with two relevant tokens or seventy percent overlap`() {
        val textualHome = HomeDestination(
            originalAddress = "Rua das Flores, 123, Curitiba",
            resolutionStatus = DestinationResolutionStatus.UNAVAILABLE,
            enabled = true,
        )
        val abbreviatedOffer = OfferDestination(
            originalAddress = "Flores 123",
            resolutionStatus = DestinationResolutionStatus.FAILED,
        )

        assertEquals(HomeMatchStatus.ENDS_NEAR_HOME, HomeMatcher().match(textualHome, abbreviatedOffer).status)
    }

    @Test
    fun `does not treat city and state alone as a home address`() {
        val cityOnlyHome = HomeDestination(
            originalAddress = "Curitiba, PR",
            resolutionStatus = DestinationResolutionStatus.UNAVAILABLE,
            enabled = true,
        )
        val cityOnlyOffer = OfferDestination(
            originalAddress = "Curitiba Paraná",
            resolutionStatus = DestinationResolutionStatus.FAILED,
        )

        assertEquals(HomeMatchStatus.UNKNOWN, HomeMatcher().match(cityOnlyHome, cityOnlyOffer).status)
    }

    @Test
    fun `does not treat a multi word city as a home address`() {
        val cityOnlyHome = HomeDestination(
            originalAddress = "São José dos Pinhais",
            resolutionStatus = DestinationResolutionStatus.UNAVAILABLE,
            enabled = true,
        )
        val cityOnlyOffer = OfferDestination(
            originalAddress = "Sao Jose dos Pinhais",
            resolutionStatus = DestinationResolutionStatus.FAILED,
        )

        assertEquals(HomeMatchStatus.UNKNOWN, HomeMatcher().match(cityOnlyHome, cityOnlyOffer).status)
    }
}
