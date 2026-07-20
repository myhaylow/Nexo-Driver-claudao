package br.com.nexo.driver.capture.service

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaProjectionCaptureConfigTest {
    @Test
    fun `keeps configured throttle and pending frame values`() {
        val config = MediaProjectionCaptureConfig(
            widthPixels = 1080,
            heightPixels = 2400,
            densityDpi = 420,
            minFrameIntervalMillis = 80,
            maxPendingFrames = 2,
        )

        assertEquals(80L, config.minFrameIntervalMillis)
        assertEquals(2, config.maxPendingFrames)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero pending frame capacity`() {
        MediaProjectionCaptureConfig(
            widthPixels = 1080,
            heightPixels = 2400,
            densityDpi = 420,
            maxPendingFrames = 0,
        )
    }

    @Test
    fun `bounds tall high resolution capture by minor edge`() {
        assertEquals(1080 to 2340, boundedCaptureSize(1080, 2340))
        assertEquals(1080 to 2340, boundedCaptureSize(1440, 3120))
    }

    @Test
    fun `does not upscale compact capture`() {
        assertEquals(540 to 960, boundedCaptureSize(540, 960))
    }
}
