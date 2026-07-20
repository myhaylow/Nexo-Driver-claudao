package br.com.nexo.driver.capture

import android.app.Activity
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [OfferCaptureService] against the real [android.app.Service] lifecycle instead of
 * pure unit logic, which the existing `app/src/test` suite cannot reach (Service/Context are not
 * available on the JVM unit-test classpath).
 */
@RunWith(AndroidJUnit4::class)
class OfferCaptureServiceLifecycleTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun deniedProjectionConsentStopsTheServiceWithoutCrashingTheProcess() {
        // RESULT_CANCELED mirrors the user dismissing the system screen-capture consent dialog.
        // The service must satisfy the startForegroundService() contract (startForeground with a
        // permitted type) and then stop itself; skipping that kills the whole process with
        // ForegroundServiceDidNotStartInTimeException. Deliberately no early stopService() here:
        // stopping before onStartCommand has run reproduces a platform race instead of app logic.
        OfferCaptureService.start(context, Activity.RESULT_CANCELED, Intent())

        // Give the service time to run onStartCommand and self-stop, and give the OS time to
        // deliver a pending ForegroundServiceDidNotStartInTimeException (which would crash this
        // instrumentation process and fail the test).
        Thread.sleep(3_000)

        assertFalse(OfferCaptureService.isActive(context))
    }
}
