package br.com.nexo.driver.analysis

import java.util.concurrent.atomic.AtomicLong

/** Allows one visible offer to receive late data without extending its original lifetime. */
class ActiveOfferUpdateGate(private val lifetimeMs: Long = DEFAULT_LIFETIME_MS) {
    private val generation = AtomicLong()
    @Volatile private var deadlineMs = 0L

    init {
        require(lifetimeMs > 0L)
    }

    fun open(nowElapsedMs: Long): Long {
        deadlineMs = nowElapsedMs + lifetimeMs
        return generation.incrementAndGet()
    }

    fun accepts(token: Long, nowElapsedMs: Long): Boolean =
        token == generation.get() && nowElapsedMs < deadlineMs

    companion object {
        const val DEFAULT_LIFETIME_MS = 8_000L
    }
}
