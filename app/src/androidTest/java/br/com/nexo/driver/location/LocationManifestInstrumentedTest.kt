package br.com.nexo.driver.location

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationManifestInstrumentedTest {
    @Suppress("DEPRECATION")
    @Test
    fun manifestKeepsLocationServiceExplicitAndPrivacyBounded() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES,
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()

        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in permissions)
        assertTrue(Manifest.permission.ACCESS_COARSE_LOCATION in permissions)
        assertTrue(Manifest.permission.FOREGROUND_SERVICE_LOCATION in permissions)
        assertFalse(Manifest.permission.ACCESS_BACKGROUND_LOCATION in permissions)
        assertFalse(Manifest.permission.INTERNET in permissions)

        val component = ComponentName(context, CurrentLocationService::class.java)
        val service = packageInfo.services.orEmpty().single { it.name == component.className }
        assertFalse(service.exported)
        assertTrue(service.flags and ServiceInfo.FLAG_STOP_WITH_TASK != 0)
        assertTrue(
            service.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION != 0,
        )
    }
}
