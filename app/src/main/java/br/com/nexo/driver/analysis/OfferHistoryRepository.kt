package br.com.nexo.driver.analysis

import br.com.nexo.driver.overlay.OverlayStatus

/**
 * Uma oferta avaliada nesta sessão, já reduzida aos números que o histórico da Início mostra.
 *
 * Mantém a mesma postura de privacidade dos contadores de sessão: valores numéricos e o veredito,
 * nunca endereço, texto de OCR ou imagem. Vive só em memória e morre com o processo.
 */
data class OfferHistoryEntry(
    val provider: String,
    val payoutCents: Long?,
    val totalDistanceMeters: Long?,
    val totalDurationSeconds: Long?,
    val ratePerKmCents: Long?,
    val status: OverlayStatus,
    val atEpochMs: Long,
)

fun interface OfferHistorySubscription : AutoCloseable {
    override fun close()
}

/**
 * Histórico em memória das ofertas avaliadas na sessão, mais recente primeiro.
 *
 * Deduplica com a mesma janela usada pelos contadores de sessão para que a mesma oferta
 * re-renderizada pelo app de corrida (o card pisca, um frame novo chega) não vire duas linhas.
 */
object OfferHistoryRepository {
    private const val MAX_ENTRIES = 50
    private const val DEDUP_WINDOW_MS = 30_000L

    private val lock = Any()
    private val observers = linkedSetOf<(List<OfferHistoryEntry>) -> Unit>()
    private val recent = linkedMapOf<String, Long>()
    private val entries = ArrayDeque<OfferHistoryEntry>(MAX_ENTRIES)

    fun current(): List<OfferHistoryEntry> = synchronized(lock) { entries.toList() }

    fun record(entry: OfferHistoryEntry) {
        val key = listOf(
            entry.provider,
            entry.payoutCents,
            entry.totalDistanceMeters,
            entry.totalDurationSeconds,
        ).joinToString("|")
        synchronized(lock) {
            recent.entries.removeAll { entry.atEpochMs - it.value > DEDUP_WINDOW_MS }
            if (recent.put(key, entry.atEpochMs) != null) return
            if (entries.size >= MAX_ENTRIES) entries.removeLast()
            entries.addFirst(entry)
        }
        publish()
    }

    fun subscribe(observer: (List<OfferHistoryEntry>) -> Unit): OfferHistorySubscription {
        val initial = synchronized(lock) {
            observers += observer
            entries.toList()
        }
        observer(initial)
        return OfferHistorySubscription { synchronized(lock) { observers -= observer } }
    }

    /** Limpa o histórico (testes e reinício de sessão). */
    fun clear() {
        synchronized(lock) {
            entries.clear()
            recent.clear()
        }
        publish()
    }

    private fun publish() {
        val listeners: List<(List<OfferHistoryEntry>) -> Unit>
        val snapshot: List<OfferHistoryEntry>
        synchronized(lock) {
            snapshot = entries.toList()
            listeners = observers.toList()
        }
        listeners.forEach { it(snapshot) }
    }
}
