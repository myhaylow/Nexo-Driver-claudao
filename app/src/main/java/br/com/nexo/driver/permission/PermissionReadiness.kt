package br.com.nexo.driver.permission

/**
 * Pure permission contracts used by onboarding and the capture service.
 *
 * A MediaProjection approval is intentionally tied to a [CaptureSessionId]. Android
 * treats that approval as short lived, so a previous session must never make a new
 * session appear ready.
 */
enum class PermissionGrant {
    NOT_REQUESTED,
    GRANTED,
    DENIED,
}

@JvmInline
value class CaptureSessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "A capture session id cannot be blank." }
    }
}

sealed interface MediaProjectionConsent {
    data object NotRequested : MediaProjectionConsent
    data object Denied : MediaProjectionConsent
    data class Granted(val sessionId: CaptureSessionId) : MediaProjectionConsent
}

data class PermissionState(
    val overlay: PermissionGrant = PermissionGrant.NOT_REQUESTED,
    val notifications: PermissionGrant = PermissionGrant.NOT_REQUESTED,
    val mediaProjection: MediaProjectionConsent = MediaProjectionConsent.NotRequested,
)

/** A pure reducer; Android permission APIs remain at the app boundary. */
class PermissionStateReducer {
    fun setOverlay(state: PermissionState, grant: PermissionGrant): PermissionState =
        state.copy(overlay = grant)

    fun setNotifications(state: PermissionState, grant: PermissionGrant): PermissionState =
        state.copy(notifications = grant)

    fun grantMediaProjection(state: PermissionState, sessionId: CaptureSessionId): PermissionState =
        state.copy(mediaProjection = MediaProjectionConsent.Granted(sessionId))

    fun denyMediaProjection(state: PermissionState): PermissionState =
        state.copy(mediaProjection = MediaProjectionConsent.Denied)

    /** Call whenever a capture session ends, is revoked, or the screen is locked. */
    fun clearMediaProjection(state: PermissionState): PermissionState =
        state.copy(mediaProjection = MediaProjectionConsent.NotRequested)
}

enum class ReadinessStatus {
    READY,
    READY_WITH_LIMITATIONS,
    NOT_READY,
}

enum class ReadinessBlocker {
    OVERLAY_PERMISSION,
    ACCESSIBILITY_SERVICE,
    MEDIA_PROJECTION_SESSION_CONSENT,
}

enum class ReadinessNotice {
    NOTIFICATION_PERMISSION,
}

data class PermissionReadiness(
    val status: ReadinessStatus,
    val blockers: Set<ReadinessBlocker>,
    val notices: Set<ReadinessNotice>,
    val canShowOverlay: Boolean,
    val canAnalyzeOffers: Boolean,
)

/**
 * Determines whether the app has at least one safe local reader path available.
 *
 * Overlay is mandatory for visual feedback. The read path can be either the primary
 * AccessibilityService or a user-approved MediaProjection fallback for this exact session.
 * Notifications are a non-blocking limitation.
 */
class PermissionReadinessEvaluator {
    fun evaluate(
        state: PermissionState,
        sessionId: CaptureSessionId?,
        accessibilityServiceEnabled: Boolean = false,
    ): PermissionReadiness {
        val overlayGranted = state.overlay == PermissionGrant.GRANTED
        val projectionGrantedForSession = sessionId != null &&
            (state.mediaProjection as? MediaProjectionConsent.Granted)?.sessionId == sessionId
        val blockers = buildSet {
            if (!overlayGranted) add(ReadinessBlocker.OVERLAY_PERMISSION)
            if (!accessibilityServiceEnabled && !projectionGrantedForSession) {
                add(ReadinessBlocker.ACCESSIBILITY_SERVICE)
            }
        }
        val notices = buildSet {
            if (state.notifications != PermissionGrant.GRANTED) add(ReadinessNotice.NOTIFICATION_PERMISSION)
        }
        val canAnalyzeOffers = blockers.isEmpty()
        return PermissionReadiness(
            status = when {
                blockers.isNotEmpty() -> ReadinessStatus.NOT_READY
                notices.isNotEmpty() -> ReadinessStatus.READY_WITH_LIMITATIONS
                else -> ReadinessStatus.READY
            },
            blockers = blockers,
            notices = notices,
            canShowOverlay = overlayGranted,
            canAnalyzeOffers = canAnalyzeOffers,
        )
    }
}
