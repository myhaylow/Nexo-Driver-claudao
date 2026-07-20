package br.com.nexo.driver.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineMapPackageStoreTest {
    @Test
    fun `stores the package reference without copying its content`() {
        val store = InMemoryOfflineMapPackageStore()
        val mapPackage = OfflineMapPackage(
            contentUri = "content://provider/maps/curitiba.pmtiles",
            displayName = "curitiba.pmtiles",
            sizeBytes = 42_000_000L,
            importedAtEpochMs = 1_753_000_000_000L,
        )

        assertEquals(mapPackage, store.save(mapPackage))
        assertEquals(mapPackage, store.load())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a non content uri`() {
        InMemoryOfflineMapPackageStore().save(
            OfflineMapPackage(
                contentUri = "file:///sdcard/curitiba.map",
                displayName = "curitiba.map",
                sizeBytes = null,
                importedAtEpochMs = 1L,
            ),
        )
    }

    @Test
    fun `clear removes the package reference`() {
        val store = InMemoryOfflineMapPackageStore(
            OfflineMapPackage(
                contentUri = "content://provider/maps/curitiba.map",
                displayName = "curitiba.map",
                sizeBytes = null,
                importedAtEpochMs = 1L,
            ),
        )

        store.clear()

        assertNull(store.load())
    }
}
