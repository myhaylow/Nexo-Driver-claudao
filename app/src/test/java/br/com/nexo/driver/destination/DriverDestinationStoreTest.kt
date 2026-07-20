package br.com.nexo.driver.destination

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverDestinationStoreTest {
    @Test
    fun `codec round trips normalized destination`() {
        val destination = DriverDestination(
            coordinate = GeoCoordinate(-25.4284, -49.2733),
            label = "  Casa  ",
            arrivalRadiusMeters = 250.0,
        )

        val payload = DestinationPayloadCodec.encode(destination)
        assertTrue(payload.startsWith("driver-destination-v3\t"))
        assertEquals(
            destination.copy(label = "Casa"),
            DestinationPayloadCodec.decode(payload),
        )
    }

    @Test
    fun `codec migrates v1 and v2 payloads to the home destination model`() {
        val v1 = "driver-destination-v1\t-25.4284\t-49.2733\t150.0\tQ2FzYQ"
        val v2 = "driver-destination-v2\t-25.4284\t-49.2733\t150.0\tQ2FzYQ\t\t\tRESOLVED"

        val expected = HomeDestination(
            coordinate = GeoCoordinate(-25.4284, -49.2733),
            label = "Casa",
            arrivalRadiusMeters = 200.0,
            originalAddress = "Casa",
        )
        assertEquals(expected, DestinationPayloadCodec.decode(v1))
        assertEquals(expected, DestinationPayloadCodec.decode(v2))
    }

    @Test
    fun `legacy radius below current safety minimum is clamped during migration`() {
        val legacy = "driver-destination-v1\t-25.4284\t-49.2733\t150.0\tQ2FzYQ"

        assertEquals(200.0, DestinationPayloadCodec.decode(legacy)?.arrivalRadiusMeters)
    }

    @Test
    fun `v3 keeps an enabled textual home when geocoding is unavailable`() {
        val unresolved = HomeDestination(
            coordinate = null,
            label = "Casa",
            originalAddress = "Rua das Flores, 123",
            resolutionStatus = DestinationResolutionStatus.UNAVAILABLE,
            enabled = true,
        )

        assertEquals(unresolved, DestinationPayloadCodec.decode(DestinationPayloadCodec.encode(unresolved)))
    }

    @Test
    fun `codec treats malformed or invalid data as absent`() {
        assertNull(DestinationPayloadCodec.decode("driver-destination-v1\t-91\t-49\t150\t"))
        assertNull(DestinationPayloadCodec.decode("driver-destination-v1\t-25\t-49\tNaN\t"))
        assertNull(DestinationPayloadCodec.decode("wrong-schema\t-25\t-49\t150\t"))
    }

    @Test
    fun `in memory store replaces and clears destination`() {
        val store = InMemoryDriverDestinationStore()
        val first = DriverDestination(GeoCoordinate(-25.42, -49.27), "Casa", 200.0)
        val replacement = DriverDestination(GeoCoordinate(-25.43, -49.28), "Trabalho", 300.0)

        assertEquals(first, store.save(first))
        assertEquals(replacement, store.save(replacement))
        assertEquals(replacement, store.load())

        store.clear()
        assertNull(store.load())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `store refuses invalid coordinate`() {
        InMemoryDriverDestinationStore().save(
            DriverDestination(GeoCoordinate(latitude = 91.0, longitude = -49.0)),
        )
    }
}
