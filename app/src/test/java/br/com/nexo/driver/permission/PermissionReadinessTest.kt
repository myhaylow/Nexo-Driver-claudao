package br.com.nexo.driver.permission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionReadinessTest {
    private val evaluator = PermissionReadinessEvaluator()
    private val reducer = PermissionStateReducer()
    private val session = CaptureSessionId("offer-session-1")

    @Test
    fun `requires overlay and a local reader path`() {
        val result = evaluator.evaluate(PermissionState(), session)

        assertEquals(ReadinessStatus.NOT_READY, result.status)
        assertEquals(
            setOf(
                ReadinessBlocker.OVERLAY_PERMISSION,
                ReadinessBlocker.ACCESSIBILITY_SERVICE,
            ),
            result.blockers,
        )
        assertFalse(result.canShowOverlay)
        assertFalse(result.canAnalyzeOffers)
    }

    @Test
    fun `is ready when accessibility overlay and notifications are granted`() {
        val state = PermissionState(
            overlay = PermissionGrant.GRANTED,
            notifications = PermissionGrant.GRANTED,
        )

        val result = evaluator.evaluate(state, session, accessibilityServiceEnabled = true)

        assertEquals(ReadinessStatus.READY, result.status)
        assertTrue(result.blockers.isEmpty())
        assertTrue(result.notices.isEmpty())
        assertTrue(result.canShowOverlay)
        assertTrue(result.canAnalyzeOffers)
    }

    @Test
    fun `accepts a fresh projection consent as fallback when accessibility is off`() {
        val state = PermissionState(
            overlay = PermissionGrant.GRANTED,
            notifications = PermissionGrant.GRANTED,
            mediaProjection = MediaProjectionConsent.Granted(session),
        )

        val result = evaluator.evaluate(state, session, accessibilityServiceEnabled = false)

        assertEquals(ReadinessStatus.READY, result.status)
        assertTrue(result.blockers.isEmpty())
        assertTrue(result.canAnalyzeOffers)
    }

    @Test
    fun `notifications missing is a non blocking limitation`() {
        val state = PermissionState(
            overlay = PermissionGrant.GRANTED,
        )

        val result = evaluator.evaluate(state, session, accessibilityServiceEnabled = true)

        assertEquals(ReadinessStatus.READY_WITH_LIMITATIONS, result.status)
        assertTrue(result.blockers.isEmpty())
        assertEquals(setOf(ReadinessNotice.NOTIFICATION_PERMISSION), result.notices)
        assertTrue(result.canAnalyzeOffers)
    }

    @Test
    fun `destination home does not require device location permission`() {
        val state = PermissionState(
            overlay = PermissionGrant.GRANTED,
            notifications = PermissionGrant.GRANTED,
        )

        val result = evaluator.evaluate(state, session, accessibilityServiceEnabled = true)

        assertEquals(ReadinessStatus.READY, result.status)
        assertTrue(result.canAnalyzeOffers)
    }

    @Test
    fun `clearing session consent makes capture unready`() {
        val granted = reducer.grantMediaProjection(PermissionState(overlay = PermissionGrant.GRANTED), session)
        val cleared = reducer.clearMediaProjection(granted)

        assertEquals(MediaProjectionConsent.NotRequested, cleared.mediaProjection)
        assertEquals(ReadinessStatus.NOT_READY, evaluator.evaluate(cleared, session).status)
    }
}
