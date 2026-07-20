package br.com.nexo.driver.destination.offline

import br.com.nexo.driver.destination.GeoCoordinate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAddressPackageTsvCodecTest {
    private val packageUnderTest = OfflineAddressPackage(
        metadata = OfflineAddressPackageMetadata(
            name = "Curitiba basico",
            version = "1.2.3",
            city = "Curitiba",
        ),
        places = listOf(
            OfflineAddressPlace(
                id = "home",
                label = "Rua Sao Jose, 120",
                coordinate = GeoCoordinate(-25.4284, -49.2733),
                aliases = setOf("Casa", "R. Sao Jose 120"),
            ),
            OfflineAddressPlace(
                id = "terminal",
                label = "Avenida Republica Argentina, 900",
                coordinate = GeoCoordinate(-25.4711, -49.2922),
            ),
        ),
    )

    @Test
    fun `round trips valid package through canonical UTF-8 tsv`() {
        val encoded = OfflineAddressPackageTsvCodec.encode(packageUnderTest)
        val decoded = OfflineAddressPackageTsvCodec.decode(encoded)

        assertEquals(packageUnderTest, decoded)
        assertEquals(
            "DRIVER_INTELIGENTE_OFFLINE_ADDRESS\t1\n" +
                "META\tCuritiba basico\t1.2.3\tCuritiba\n" +
                "PLACE\thome\tRua Sao Jose, 120\t-25.4284\t-49.2733\tCasa\tR. Sao Jose 120\n" +
                "PLACE\tterminal\tAvenida Republica Argentina, 900\t-25.4711\t-49.2922\n",
            encoded.toString(Charsets.UTF_8),
        )
        assertArrayEquals(encoded, OfflineAddressPackageTsvCodec.encode(decoded))
    }

    @Test
    fun `accepts a final LF but rejects blank rows and CRLF`() {
        val validWithoutFinalLf = OfflineAddressPackageTsvCodec.encode(packageUnderTest)
            .toString(Charsets.UTF_8)
            .dropLast(1)
        assertEquals(packageUnderTest, OfflineAddressPackageTsvCodec.decode(validWithoutFinalLf))

        assertFormatError(validWithoutFinalLf + "\n\n")
        assertFormatError(validWithoutFinalLf.replace("\n", "\r\n"))
    }

    @Test
    fun `rejects malformed bytes headers rows and numeric fields`() {
        assertFormatError(byteArrayOf(0xC3.toByte(), 0x28))
        assertFormatError("WRONG\t1\nMETA\tName\t1.0.0\tCity\n")
        assertFormatError("${OfflineAddressPackageTsvCodec.MAGIC}\t2\nMETA\tName\t1.0.0\tCity\n")
        assertFormatError("${OfflineAddressPackageTsvCodec.MAGIC}\t1\nMETA\tName\t1.0.0\tCity\nUNKNOWN\tx\n")
        assertFormatError("${OfflineAddressPackageTsvCodec.MAGIC}\t1\nMETA\tName\t1.0.0\tCity\nPLACE\tid\tRua\t1e2\t-49.0\n")
        assertFormatError("${OfflineAddressPackageTsvCodec.MAGIC}\t1\nMETA\tName\t1.0.0\tCity\nPLACE\tid\tRua\t-25.0\t181.0\n")
    }

    @Test
    fun `rejects malformed package content instead of silently repairing it`() {
        val prefix = "${OfflineAddressPackageTsvCodec.MAGIC}\t1\nMETA\tName\t1.0.0\tCity\n"
        assertFormatError(prefix + "PLACE\tid\tRua\t-25.0\t-49.0\tCasa\tCasa\n")
        assertFormatError(prefix + "PLACE\tid\tRua\t-25.0\t-49.0\t\n")
        assertFormatError(prefix + "PLACE\tA\tRua A\t-25.0\t-49.0\nPLACE\ta\tRua B\t-25.1\t-49.1\n")
        assertFormatError("\uFEFF$prefix")
    }

    @Test
    fun `refuses to encode data that cannot be represented losslessly`() {
        val withTab = packageUnderTest.copy(
            places = packageUnderTest.places.map { place ->
                if (place.id == "home") place.copy(label = "Rua\tSao Jose") else place
            },
        )
        assertFormatError { OfflineAddressPackageTsvCodec.encode(withTab) }
    }

    private fun assertFormatError(input: String) = assertFormatError { OfflineAddressPackageTsvCodec.decode(input) }

    private fun assertFormatError(input: ByteArray) = assertFormatError { OfflineAddressPackageTsvCodec.decode(input) }

    private fun assertFormatError(block: () -> Unit) {
        val thrown = runCatching(block).exceptionOrNull()
        assertTrue("Expected OfflineAddressPackageFormatException but was $thrown", thrown is OfflineAddressPackageFormatException)
    }
}
