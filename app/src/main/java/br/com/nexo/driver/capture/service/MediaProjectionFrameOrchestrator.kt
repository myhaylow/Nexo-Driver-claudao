package br.com.nexo.driver.capture.service

import android.media.ImageReader
import android.os.Handler
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Connects an ImageReader to a local frame consumer with latest-frame acquisition, throttling
 * and a one-frame (by default) work queue.  The supplied executor owns conversion and callback
 * work; callers must not perform OCR on the ImageReader callback thread.
 */
class MediaProjectionFrameOrchestrator(
    private val imageReader: ImageReader,
    config: MediaProjectionCaptureConfig,
    private val conversionExecutor: Executor,
    private val consumer: CapturedBitmapConsumer,
    private val converter: RgbaImageBitmapConverter = RgbaImageBitmapConverter(),
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private val pendingFrames = AtomicInteger(0)
    private val throttle = FrameThrottle(config.minFrameIntervalMillis * NANOS_PER_MILLISECOND)
    private val maxPendingFrames = config.maxPendingFrames
    private val lock = Any()

    @Volatile
    private var state: MediaProjectionCaptureState = MediaProjectionCaptureState.Idle

    private var metrics = MediaProjectionFrameMetrics()

    private val imageListener = ImageReader.OnImageAvailableListener(::onImageAvailable)

    /** Attach to the reader. The handler should belong to a lightweight service callback thread. */
    fun start(callbackHandler: Handler) {
        check(running.compareAndSet(false, true)) { "Frame orchestrator is already running." }
        synchronized(lock) { state = MediaProjectionCaptureState.Running }
        imageReader.setOnImageAvailableListener(imageListener, callbackHandler)
    }

    /** Stops new delivery but intentionally does not close the ImageReader, which the service owns. */
    fun stop(reason: CaptureStopReason) {
        if (!running.getAndSet(false)) return
        imageReader.setOnImageAvailableListener(null, null)
        throttle.reset()
        synchronized(lock) { state = MediaProjectionCaptureState.Stopped(reason) }
    }

    fun state(): MediaProjectionCaptureState = state

    fun metrics(): MediaProjectionFrameMetrics = synchronized(lock) { metrics }

    override fun close() = stop(CaptureStopReason.SERVICE_DESTROYED)

    private fun onImageAvailable(reader: ImageReader) {
        val image = try {
            reader.acquireLatestImage()
        } catch (_: IllegalStateException) {
            recordFailure("ImageReader could not acquire a frame.")
            return
        } ?: return

        if (!running.get()) {
            image.close()
            return
        }
        incrementMetrics { it.copy(acquiredFrames = it.acquiredFrames + 1) }
        val timestamp = image.timestamp.coerceAtLeast(0L)
        if (!throttle.tryAcquire(timestamp)) {
            incrementMetrics { it.copy(throttledFrames = it.throttledFrames + 1) }
            image.close()
            return
        }
        if (!reserveFrameSlot()) {
            incrementMetrics { it.copy(backpressuredFrames = it.backpressuredFrames + 1) }
            image.close()
            return
        }
        try {
            conversionExecutor.execute {
                try {
                    val bitmap = converter.convertAndClose(image)
                    if (running.get()) {
                        try {
                            consumer.onFrame(CapturedBitmapFrame(bitmap, timestamp))
                            incrementMetrics { it.copy(deliveredFrames = it.deliveredFrames + 1) }
                        } catch (_: Exception) {
                            incrementMetrics { it.copy(consumerFailures = it.consumerFailures + 1) }
                        }
                    } else {
                        // Conversion may finish after stop(). Since ownership was never handed to
                        // the consumer, the orchestrator must release the native bitmap memory.
                        bitmap.recycle()
                    }
                } catch (_: Exception) {
                    incrementMetrics { it.copy(conversionFailures = it.conversionFailures + 1) }
                } finally {
                    pendingFrames.decrementAndGet()
                }
            }
        } catch (_: RejectedExecutionException) {
            pendingFrames.decrementAndGet()
            image.close()
            recordFailure("Frame executor rejected a capture task.")
        }
    }

    private fun reserveFrameSlot(): Boolean {
        while (true) {
            val current = pendingFrames.get()
            if (current >= maxPendingFrames) return false
            if (pendingFrames.compareAndSet(current, current + 1)) return true
        }
    }

    private fun recordFailure(message: String) {
        synchronized(lock) { state = MediaProjectionCaptureState.Failed(message) }
    }

    private fun incrementMetrics(transform: (MediaProjectionFrameMetrics) -> MediaProjectionFrameMetrics) {
        synchronized(lock) { metrics = transform(metrics) }
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
