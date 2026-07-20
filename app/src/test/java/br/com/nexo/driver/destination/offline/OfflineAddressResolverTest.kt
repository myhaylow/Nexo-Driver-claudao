package br.com.nexo.driver.destination.offline

import br.com.nexo.driver.destination.GeoCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAddressResolverTest {
    private val packageUnderTest = OfflineAddressPackage(
        metadata = OfflineAddressPackageMetadata(
            name = "Curitiba básico",
            version = "1.0.0",
            city = "Curitiba",
        ),
        places = listOf(
            OfflineAddressPlace(
                id = "home",
                label = "Rua São José, 120",
                coordinate = GeoCoordinate(-25.4284, -49.2733),
                aliases = setOf("R. Sao Jose 120", "Casa"),
            ),
            OfflineAddressPlace(
                id = "terminal",
                label = "Avenida República Argentina, 900",
                coordinate = GeoCoordinate(-25.4711, -49.2922),
            ),
        ),
    )

    @Test
    fun `normalizes accents punctuation whitespace and common street abbreviations`() {
        assertEquals("rua sao jose 120", OfflineAddressNormalizer.normalize(" R.  São-José, nº 120 "))

        val resolver = requireNotNull(OfflineAddressResolver.create(packageUnderTest))
        val resolved = requireNotNull(resolver.resolve("  r. sao jose, 120  "))

        assertEquals("home", resolved.place.id)
        assertEquals("rua sao jose 120", resolved.normalizedQuery)
    }

    @Test
    fun `only accepts explicit aliases and never performs fuzzy matching`() {
        val resolver = requireNotNull(OfflineAddressResolver.create(packageUnderTest))

        assertEquals("home", resolver.resolve("Casa")?.place?.id)
        assertNull(resolver.resolve("Rua Sao Jose"))
        assertNull(resolver.resolve("Rua Sao Joze 120"))
        assertNull(resolver.resolve(null))
    }

    @Test
    fun `returns null when a normalized address belongs to more than one place`() {
        val ambiguous = packageUnderTest.copy(
            places = packageUnderTest.places + OfflineAddressPlace(
                id = "other-home",
                label = "Rua São José, 120",
                coordinate = GeoCoordinate(-25.4300, -49.2700),
            ),
        )

        val validation = ambiguous.validate()
        assertTrue(validation.isValid)
        assertTrue(validation.warnings.any { it.code == OfflinePackageIssueCode.AMBIGUOUS_ADDRESS_KEY })
        assertNull(requireNotNull(OfflineAddressResolver.create(ambiguous)).resolve("Rua Sao Jose 120"))
    }

    @Test
    fun `rejects malformed packages before resolver creation`() {
        val invalid = OfflineAddressPackage(
            metadata = OfflineAddressPackageMetadata("", "version one", ""),
            places = listOf(
                OfflineAddressPlace("", "", GeoCoordinate(91.0, 0.0), aliases = setOf("")),
                OfflineAddressPlace("DUP", "Rua A", GeoCoordinate(0.0, 0.0)),
                OfflineAddressPlace("dup", "Rua B", GeoCoordinate(0.0, 0.0)),
            ),
        )

        val validation = invalid.validate()
        assertFalse(validation.isValid)
        assertTrue(validation.errors.any { it.code == OfflinePackageIssueCode.BLANK_PACKAGE_NAME })
        assertTrue(validation.errors.any { it.code == OfflinePackageIssueCode.INVALID_COORDINATE })
        assertTrue(validation.errors.any { it.code == OfflinePackageIssueCode.DUPLICATE_PLACE_ID })
        assertNull(OfflineAddressResolver.create(invalid))
    }

    @Test
    fun `exposes package metadata without mutable resolver state`() {
        val resolver = requireNotNull(OfflineAddressResolver.create(packageUnderTest))
        assertEquals("Curitiba", resolver.packageMetadata().city)
        assertNotNull(resolver.resolve("Avenida Republica Argentina 900"))
    }
}
