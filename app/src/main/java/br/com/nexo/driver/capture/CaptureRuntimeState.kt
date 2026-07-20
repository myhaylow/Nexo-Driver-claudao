package br.com.nexo.driver.capture

import android.content.Context

/**
 * Small, frame-free state handoff between the foreground reader and the Compose activity.
 *
 * A MediaProjection is valid only for the process that received the system consent.  Persisting
 * that process id prevents a stale `active` value from making the switch look enabled after the
 * process was killed and later recreated.
 */
data class CaptureRuntimeState(
    val isActive: Boolean = false,
    val ownerProcessId: Int = NO_PROCESS,
) {
    fun isActiveFor(processId: Int): Boolean = isActive && ownerProcessId == processId

    companion object {
        const val NO_PROCESS = -1
    }
}

/** Private persistence used only to restore the in-process reader state when the Activity opens. */
class CaptureRuntimeStateStore private constructor(
    private val context: Context,
) {
    fun load(): CaptureRuntimeState = CaptureRuntimeState(
        isActive = preferences.getBoolean(KEY_ACTIVE, false),
        ownerProcessId = preferences.getInt(KEY_OWNER_PROCESS_ID, CaptureRuntimeState.NO_PROCESS),
    )

    fun save(state: CaptureRuntimeState) {
        // commit makes a just-started foreground service visible immediately when Activity
        // recreation happens in the same process. It never contains offer data or OCR text.
        preferences.edit()
            .putBoolean(KEY_ACTIVE, state.isActive)
            .putInt(KEY_OWNER_PROCESS_ID, state.ownerProcessId)
            .commit()
    }

    private val preferences
        get() = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    companion object {
        private const val PREFERENCES = "capture_runtime_state"
        private const val KEY_ACTIVE = "active"
        private const val KEY_OWNER_PROCESS_ID = "owner_process_id"

        fun create(context: Context): CaptureRuntimeStateStore =
            CaptureRuntimeStateStore(context.applicationContext)
    }
}
