package br.com.nexo.driver.capture.service

import java.util.concurrent.atomic.AtomicLong

/**
 * Rejects asynchronous work that belongs to an earlier MediaProjection session.
 *
 * OCR can still be running while the user stops sharing the screen. Invalidating the active
 * generation before releasing Android resources prevents that late result from recreating the
 * overlay after capture has ended.
 */
internal class CaptureSessionGuard {
    private val nextGeneration = AtomicLong(0L)
    private val activeGeneration = AtomicLong(NO_SESSION)

    fun begin(): Long {
        val generation = nextGeneration.incrementAndGet()
        activeGeneration.set(generation)
        return generation
    }

    fun invalidate() {
        activeGeneration.set(NO_SESSION)
    }

    fun isActive(generation: Long): Boolean =
        generation != NO_SESSION && activeGeneration.get() == generation

    fun currentOrNull(): Long? = activeGeneration.get().takeUnless { it == NO_SESSION }

    private companion object {
        const val NO_SESSION = 0L
    }
}
