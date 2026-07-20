package br.com.nexo.driver.destination

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationDirectionEvaluatorTest {
    private val curitibaCenter = GeoCoordinate(-25.4284, -49.2733)
    private val homeNorth = GeoCoordinate(-25.3784, -49.2733)
    private val evaluator = DestinationDirectionEvaluator()

    @Test
    fun `marks an endpoint with meaningful progress as towards destination`() {
        val result = evaluator.evaluate(
            DirectionEvaluationInput(
                currentPosition = curitibaCenter,
                pickupPosition = null,
                dropoffPosition = GeoCoordinate(-25.3934, -49.2733),
                destination = DriverDestination(homeNorth),
            ),
        )

        assertEquals(DestinationDirectionStatus.TOWARDS_DESTINATION, result.status)
        assertEquals(DirectionOrigin.CURRENT_POSITION, result.origin)
        assertTrue(result.distanceReductionMeters!! > 1_000.0)
        assertTrue(result.distanceReductionRatio!! > 0.10)
        assertTrue(result.isTowardsDestination)
    }

    @Test
    fun `prefers pickup point when it is available`() {
        val result = evaluator.evaluate(
            DirectionEvaluationInput(
                currentPosition = GeoCoordinate(-25.4100, -49.2733),
                pickupPosition = curitibaCenter,
                dropoffPosition = GeoCoordinate(-25.3934, -49.2733),
                destination = DriverDestination(homeNorth),
            ),
        )

        assertEquals(DirectionOrigin.PICKUP, result.origin)
        assertEquals(DestinationDirectionStatus.TOWARDS_DESTINATION, result.status)
    }

    @Test
    fun `does not infer direction for lateral movement inside the dead band`() {
        val result = evaluator.evaluate(
            DirectionEvaluationInput(
                currentPosition = curitibaCenter,
                pickupPosition = null,
                // About 110 m north: under both the default absolute reduction and ratio guards.
                dropoffPosition = GeoCoordinate(-25.4274, -49.2733),
                destination = DriverDestination(homeNorth),
            ),
        )

        assertEquals(DestinationDirectionStatus.NEUTRAL, result.status)
        assertFalse(result.isTowardsDestination)
    }

    @Test
    fun `marks a destination that becomes farther away as away`() {
        val result = evaluator.evaluate(
            DirectionEvaluationInput(
                currentPosition = curitibaCenter,
                pickupPosition = null,
                dropoffPosition = GeoCoordinate(-25.4684, -49.2733),
                destination = DriverDestination(homeNorth),
            ),
        )

        assertEquals(DestinationDirectionStatus.AWAY_FROM_DESTINATION, result.status)
        assertTrue(result.distanceReductionMeters!! < 0.0)
    }

    @Test
    fun `accepts endpoint inside configured arrival radius`() {
        val result = evaluator.evaluate(
            DirectionEvaluationInput(
                currentPosition = GeoCoordinate(-25.3790, -49.2733),
                pickupPosition = null,
                dropoffPosition = GeoCoordinate(-25.3780, -49.2733),
                destination = DriverDestination(homeNorth, arrivalRadiusMeters = 100.0),
            ),
        )

        assertEquals(DestinationDirectionStatus.TOWARDS_DESTINATION, result.status)
    }

    @Test
    fun `returns unknown for missing or invalid coordinates`() {
        val missingDropoff = evaluator.evaluate(
            DirectionEvaluationInput(curitibaCenter, null, null, DriverDestination(homeNorth)),
        )
        val invalidDestination = evaluator.evaluate(
            DirectionEvaluationInput(
                curitibaCenter,
                null,
                curitibaCenter,
                DriverDestination(GeoCoordinate(91.0, -49.2733)),
            ),
        )

        assertEquals(DestinationDirectionStatus.UNKNOWN, missingDropoff.status)
        assertEquals(DestinationDirectionStatus.UNKNOWN, invalidDestination.status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid tolerance configuration`() {
        DestinationDirectionConfig(minimumReductionRatio = 1.01)
    }
}
