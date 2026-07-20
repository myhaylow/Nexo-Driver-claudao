package br.com.nexo.driver.capture.service

/**
 * Thread-safe monotonic frame gate.  It accepts the first timestamp and then one frame per
 * interval.  A timestamp going backwards is rejected rather than resetting the gate, which
 * prevents stale ImageReader frames from bypassing the configured limit.
 */
class FrameThrottle(minimumIntervalNanos: Long) {
    init {
        require(minimumIntervalNanos >= 0L) { "Minimum frame interval cannot be negative." }
    }

    private val intervalNanos = minimumIntervalNanos
    private var lastAcceptedNanos: Long? = null

    @Synchronized
    fun tryAcquire(timestampNanos: Long): Boolean {
        require(timestampNanos >= 0L) { "Frame timestamp cannot be negative." }
        val previous = lastAcceptedNanos
        if (previous != null && timestampNanos - previous < intervalNanos) return false
        lastAcceptedNanos = timestampNanos
        return true
    }

    @Synchronized
    fun reset() {
        lastAcceptedNanos = null
    }
}
