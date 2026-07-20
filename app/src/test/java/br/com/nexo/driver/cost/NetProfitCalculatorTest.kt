package br.com.nexo.driver.cost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetProfitCalculatorTest {
    @Test
    fun `subtracts estimated fuel cost from the gross payout`() {
        // R$ 4,00/L at 8 km/L -> R$ 0,50/km. 14.8 km -> R$ 7,40 fuel. 4830 - 740 = 4090 cents.
        val calculator = NetProfitCalculator(FuelSettings(pricePerLiterCents = 400, kilometersPerLiter = 8.0))

        assertEquals(4_090L, calculator.estimateCents(grossPayoutCents = 4_830, totalDistanceMeters = 14_800))
    }

    @Test
    fun `returns full profit estimate including percentage`() {
        val calculator = NetProfitCalculator(FuelSettings(pricePerLiterCents = 400, kilometersPerLiter = 8.0))

        val estimate = calculator.estimate(grossPayoutCents = 4_830, totalDistanceMeters = 14_800)

        assertEquals(740L, estimate?.fuelCostCents)
        assertEquals(4_090L, estimate?.netProfitCents)
        assertEquals(8_467L, estimate?.netProfitPercentScaled)
    }

    @Test
    fun `returns null when a required input is missing`() {
        val calculator = NetProfitCalculator(FuelSettings())

        assertNull(calculator.estimateCents(grossPayoutCents = null, totalDistanceMeters = 10_000))
        assertNull(calculator.estimateCents(grossPayoutCents = 2_000, totalDistanceMeters = null))
    }

    @Test
    fun `returns null when the estimate is disabled`() {
        val calculator = NetProfitCalculator(FuelSettings(enabled = false))

        assertNull(calculator.estimateCents(grossPayoutCents = 2_000, totalDistanceMeters = 10_000))
    }
}
