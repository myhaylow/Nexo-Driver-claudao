package br.com.nexo.driver.location

/** Public state excludes latitude/longitude; presentation-safe metadata stays only in memory. */
data class CurrentLocationServiceSnapshot(
    val status: CurrentLocationServiceStatus = CurrentLocationServiceStatus.IDLE,
    val sessionDistanceMeters: Double = 0.0,
    val provider: String? = null,
    val accuracyMeters: Double? = null,
    val fixEpochMs: Long? = null,
    val isLastKnown: Boolean = false,
)

enum class CurrentLocationServiceStatus {
    IDLE,
    ACQUIRING,
    ACTIVE,
    PERMISSION_MISSING,
    PROVIDER_UNAVAILABLE,
    FIX_REJECTED,
    MOVEMENT_REJECTED,
}

fun interface CurrentLocationStateSubscription : AutoCloseable {
    override fun close()
}

/**
 * Process-local observable state for a future UI. It deliberately never persists or exposes raw
 * coordinates; only readiness, non-sensitive fix metadata and session distance remain in memory.
 */
object CurrentLocationStateRepository {
    private val lock = Any()
    private val observers = linkedSetOf<(CurrentLocationServiceSnapshot) -> Unit>()
    private var snapshot = CurrentLocationServiceSnapshot()

    fun current(): CurrentLocationServiceSnapshot = synchronized(lock) { snapshot }

    fun update(state: CurrentLocationState) {
        val (next, listeners) = synchronized(lock) {
            val next = when (state) {
                CurrentLocationState.Idle -> CurrentLocationServiceSnapshot(CurrentLocationServiceStatus.IDLE)
                CurrentLocationState.Acquiring -> snapshot.copy(status = CurrentLocationServiceStatus.ACQUIRING)
                CurrentLocationState.PermissionMissing -> snapshot.copy(status = CurrentLocationServiceStatus.PERMISSION_MISSING)
                CurrentLocationState.ProviderUnavailable -> snapshot.copy(status = CurrentLocationServiceStatus.PROVIDER_UNAVAILABLE)
                is CurrentLocationState.Available -> CurrentLocationServiceSnapshot(
                    status = CurrentLocationServiceStatus.ACTIVE,
                    sessionDistanceMeters = state.sessionDistanceMeters,
                    provider = state.fix.provider,
                    accuracyMeters = state.fix.accuracyMeters,
                    fixEpochMs = state.fix.capturedAtEpochMs,
                    isLastKnown = state.fix.isLastKnown,
                )
                is CurrentLocationState.Rejected -> snapshot.copy(status = CurrentLocationServiceStatus.FIX_REJECTED)
                is CurrentLocationState.MovementRejected -> snapshot.copy(status = CurrentLocationServiceStatus.MOVEMENT_REJECTED)
            }
            snapshot = next
            next to observers.toList()
        }
        listeners.forEach { it(next) }
    }

    fun subscribe(observer: (CurrentLocationServiceSnapshot) -> Unit): CurrentLocationStateSubscription {
        val initial = synchronized(lock) {
            observers += observer
            snapshot
        }
        observer(initial)
        return CurrentLocationStateSubscription { synchronized(lock) { observers -= observer } }
    }
}
