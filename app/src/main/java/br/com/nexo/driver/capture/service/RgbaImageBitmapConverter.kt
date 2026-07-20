package br.com.nexo.driver.capture.service

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.Image
import java.nio.ByteBuffer

/**
 * Converts an RGBA_8888 [ImageReader][android.media.ImageReader] frame without assuming that
 * rows are tightly packed.  The conversion reads channel bytes explicitly instead of relying on
 * device-specific native bitmap byte order.
 *
 * The source [Image] is always closed, including malformed frames and conversion failures.
 */
class RgbaImageBitmapConverter {
    fun convertAndClose(image: Image): Bitmap = try {
        convert(image)
    } finally {
        image.close()
    }

    fun convert(image: Image): Bitmap {
        check(image.format == PixelFormat.RGBA_8888) {
            "Expected RGBA_8888 image format, got ${image.format}."
        }
        check(image.planes.size == 1) { "Expected a single RGBA plane, got ${image.planes.size}." }
        val plane = image.planes[0]
        check(plane.pixelStride == RGBA_PIXEL_STRIDE_BYTES) {
            "Expected RGBA pixel stride of 4, got ${plane.pixelStride}."
        }
        check(plane.rowStride >= image.width * RGBA_PIXEL_STRIDE_BYTES) {
            "RGBA row stride is smaller than the frame width."
        }

        val pixels = IntArray(image.width * image.height)
        val buffer = plane.buffer.duplicate()
        val firstByteOffset = buffer.position()
        var outputIndex = 0
        for (y in 0 until image.height) {
            val rowOffset = firstByteOffset + y * plane.rowStride
            for (x in 0 until image.width) {
                val offset = rowOffset + x * plane.pixelStride
                check(offset + 3 < buffer.limit()) { "RGBA buffer ended before the expected pixel." }
                val red = buffer.unsignedByteAt(offset)
                val green = buffer.unsignedByteAt(offset + 1)
                val blue = buffer.unsignedByteAt(offset + 2)
                val alpha = buffer.unsignedByteAt(offset + 3)
                pixels[outputIndex++] = Color.argb(alpha, red, green, blue)
            }
        }
        return Bitmap.createBitmap(pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
    }

    private fun ByteBuffer.unsignedByteAt(index: Int): Int = get(index).toInt() and 0xFF

    private companion object {
        const val RGBA_PIXEL_STRIDE_BYTES = 4
    }
}
