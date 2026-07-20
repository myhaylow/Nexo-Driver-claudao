package br.com.nexo.driver.capture

/**
 * State contract for the user-consented MediaProjection session.
 * The Android service implementation is introduced only after this state model
 * is exercised by the onboarding and permission flows.
 */
sealed interface CaptureSessionState {
    data object Idle : CaptureSessionState
    data object AwaitingConsent : CaptureSessionState
    data object Active : CaptureSessionState
    data class Stopped(val reason: StopReason) : CaptureSessionState
}

enum class StopReason { USER, SCREEN_LOCKED, PROJECTION_REVOKED, SERVICE_ERROR }

class CaptureSessionReducer {
    fun requestConsent(current: CaptureSessionState): CaptureSessionState = when (current) {
        CaptureSessionState.Idle, is CaptureSessionState.Stopped -> CaptureSessionState.AwaitingConsent
        CaptureSessionState.AwaitingConsent, CaptureSessionState.Active -> current
    }

    fun consentGranted(current: CaptureSessionState): CaptureSessionState =
        if (current == CaptureSessionState.AwaitingConsent) CaptureSessionState.Active else current

    fun consentDenied(current: CaptureSessionState): CaptureSessionState =
        if (current == CaptureSessionState.AwaitingConsent) CaptureSessionState.Stopped(StopReason.USER) else current

    fun stop(reason: StopReason): CaptureSessionState = CaptureSessionState.Stopped(reason)
}
