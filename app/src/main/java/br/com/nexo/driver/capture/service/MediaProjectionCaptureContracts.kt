package br.com.nexo.driver.capture.service

import android.graphics.PixelFormat

/**
 * Parameters owned by the foreground MediaProjection service.  The service creates the
 * [android.media.ImageReader] from this object after the driver has granted screen-capture
 * consent; this module deliberately never stores the consent intent or a captured frame.
 */
data class MediaProjectionCaptureConfig(
    val widthPixels: Int,
    val heightPixels: Int,
    val densityDpi: Int,
    val minFrameIntervalMillis: Long = DEFAULT_MIN_FRAME_INTERVAL_MS,
    val maxPendingFrames: Int = DEFAULT_MAX_PENDING_FRAMES,
    val imageReaderMaxImages: Int = DEFAULT_IMAGE_READER_MAX_IMAGES,
    val pixelFormat: Int = PixelFormat.RGBA_8888,
) {
    init {
        require(widthPixels > 0) { "Capture width must be positive." }
        require(heightPixels > 0) { "Capture height must be positive." }
        require(densityDpi > 0) { "Capture density must be positive." }
        require(minFrameIntervalMillis >= 0L) { "Minimum frame interval cannot be negative." }
        require(maxPendingFrames > 0) { "At least one pending frame must be permitted." }
        require(imageReaderMaxImages >= 2) {
            "ImageReader must keep at least two images so acquireLatestImage can discard stale frames."
        }
        require(pixelFormat == PixelFormat.RGBA_8888) {
            "Only RGBA_8888 frames are supported by the local converter."
        }
    }
}

/**
 * Keeps the OCR surface bounded on high-resolution devices.  Offer cards use large text, so a
 * 1080-pixel minor edge preserves the smaller distance values that appear on 99 offer cards,
 * while still bounding allocations on 1440p+ displays. This affects only the transient
 * MediaProjection surface; it never writes an image to storage.
 */
fun boundedCaptureSize(
    sourceWidthPixels: Int,
    sourceHeightPixels: Int,
    maxMinorEdgePixels: Int = DEFAULT_CAPTURE_MAX_MINOR_EDGE_PIXELS,
): Pair<Int, Int> {
    require(sourceWidthPixels > 0 && sourceHeightPixels > 0) { "Capture dimensions must be positive." }
    require(maxMinorEdgePixels > 0) { "Capture minor edge bound must be positive." }
    val minorEdge = minOf(sourceWidthPixels, sourceHeightPixels)
    if (minorEdge <= maxMinorEdgePixels) return sourceWidthPixels to sourceHeightPixels
    val scale = maxMinorEdgePixels.toDouble() / minorEdge.toDouble()
    return (sourceWidthPixels * scale).toInt().coerceAtLeast(1) to
        (sourceHeightPixels * scale).toInt().coerceAtLeast(1)
}

/** Observable lifecycle of frame delivery, independent from Android's projection consent UI. */
sealed interface MediaProjectionCaptureState {
    data object Idle : MediaProjectionCaptureState
    data object Running : MediaProjectionCaptureState
    data class Stopped(val reason: CaptureStopReason) : MediaProjectionCaptureState
    data class Failed(val message: String) : MediaProjectionCaptureState
}

enum class CaptureStopReason {
    USER,
    PROJECTION_REVOKED,
    SCREEN_LOCKED,
    CAPTURE_RESIZED,
    SERVICE_DESTROYED,
}

/** Receives ownership of the bitmap for one accepted screen frame. */
fun interface CapturedBitmapConsumer {
    fun onFrame(frame: CapturedBitmapFrame)
}

data class CapturedBitmapFrame(
    val bitmap: android.graphics.Bitmap,
    /** Timestamp supplied by ImageReader (monotonic nanoseconds). */
    val capturedAtNanos: Long,
)

data class MediaProjectionFrameMetrics(
    val acquiredFrames: Long = 0,
    val deliveredFrames: Long = 0,
    val throttledFrames: Long = 0,
    val backpressuredFrames: Long = 0,
    val conversionFailures: Long = 0,
    val consumerFailures: Long = 0,
)

/**
 * Lower bound between accepted frames. This throttle is the first component of the one-second
 * frame-to-overlay budget: a card that appears right after a rejected frame waits this long
 * before its first frame is even accepted. Backpressure ([DEFAULT_MAX_PENDING_FRAMES] = 1)
 * already prevents OCR overload while a recognition is in flight, so the interval only needs to
 * bound idle-screen work, not protect the OCR stage.
 */
const val DEFAULT_MIN_FRAME_INTERVAL_MS = 100L
const val DEFAULT_MAX_PENDING_FRAMES = 1
const val DEFAULT_IMAGE_READER_MAX_IMAGES = 3
const val DEFAULT_CAPTURE_MAX_MINOR_EDGE_PIXELS = 1080
