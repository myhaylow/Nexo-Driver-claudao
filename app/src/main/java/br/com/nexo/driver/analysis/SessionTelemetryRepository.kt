package br.com.nexo.driver.analysis

/**
 * Números reais da telemetria de sessão, todos deriváveis de km rodado + tempo + combustível.
 * Campos são null enquanto ainda não há dado suficiente (ex.: sessão parada ou km zero), para a UI
 * mostrar um estado de espera em vez de um zero enganoso.
 */
data class SessionTelemetry(
    val elapsedMs: Long?,
    val litersUsed: Double?,
    val burnRateLitersPerHour: Double?,
    val averageSpeedKmh: Double?,
    val fuelCostReais: Double?,
) {
    companion object {
        /** Abaixo disso o L/h e a velocidade média ainda são ruído, não medida. */
        const val MIN_ELAPSED_MS_FOR_RATES = 60_000L

        fun compute(
            sessionKm: Double,
            sessionStartEpochMs: Long?,
            nowEpochMs: Long,
            pricePerLiterCents: Long,
            kilometersPerLiter: Double,
        ): SessionTelemetry {
            val safeKmPerLiter = kilometersPerLiter.coerceAtLeast(0.1)
            val liters = if (sessionKm > 0) sessionKm / safeKmPerLiter else null
            val cost = liters?.let { it * (pricePerLiterCents / 100.0) }
            val elapsed = sessionStartEpochMs?.let { (nowEpochMs - it).coerceAtLeast(0L) }
            val hours = elapsed?.takeIf { it >= MIN_ELAPSED_MS_FOR_RATES }?.let { it / 3_600_000.0 }
            return SessionTelemetry(
                elapsedMs = elapsed,
                litersUsed = liters,
                burnRateLitersPerHour = if (liters != null && hours != null) liters / hours else null,
                averageSpeedKmh = if (sessionKm > 0 && hours != null) sessionKm / hours else null,
                fuelCostReais = cost,
            )
        }
    }
}

/**
 * Marca o início/fim da sessão de telemetria — o serviço de localização liga o relógio quando o
 * rastreio começa. Idempotente: starts repetidos (rebinds do serviço) não reiniciam a contagem.
 */
object SessionTelemetryRepository {
    private val lock = Any()
    private var sessionStartEpochMs: Long? = null

    fun sessionStarted(nowEpochMs: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            if (sessionStartEpochMs == null) sessionStartEpochMs = nowEpochMs
        }
    }

    fun sessionEnded() {
        synchronized(lock) { sessionStartEpochMs = null }
    }

    fun currentSessionStart(): Long? = synchronized(lock) { sessionStartEpochMs }
}
