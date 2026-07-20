package br.com.nexo.driver.destination

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

/** Internal, versioned single-record codec; it keeps this small configuration dependency-free. */
internal object DestinationPayloadCodec {
    private const val SCHEMA = "driver-destination-v3"
    private const val V2_SCHEMA = "driver-destination-v2"
    private const val V1_SCHEMA = "driver-destination-v1"

    fun encode(destination: HomeDestination): String {
        val validated = requireNotNull(destination.validatedOrNull()) { "Invalid destination." }
        return listOf(
            SCHEMA,
            if (validated.enabled) "1" else "0",
            validated.coordinate?.latitude?.toString().orEmpty(),
            validated.coordinate?.longitude?.toString().orEmpty(),
            validated.arrivalRadiusMeters.toString(),
            validated.label?.let(::encodeText).orEmpty(),
            validated.originalAddress?.let(::encodeText).orEmpty(),
            validated.standardizedAddress?.let(::encodeText).orEmpty(),
            validated.preparedAtEpochMs?.toString().orEmpty(),
            validated.resolutionStatus.name,
        ).joinToString(separator = "\t")
    }

    fun decode(payload: String?): DriverDestination? {
        if (payload.isNullOrBlank()) return null
        return runCatching {
            val fields = payload.split('\t')
            when {
                fields.size == 5 && fields[0] == V1_SCHEMA -> legacyDestination(fields)
                fields.size == 8 && fields[0] == V2_SCHEMA -> legacyDestination(fields)
                fields.size == 10 && fields[0] == SCHEMA -> v3Destination(fields)
                else -> error("Unsupported destination schema.")
            }.validatedOrNull() ?: error("Invalid destination.")
        }.getOrNull()
    }

    private fun legacyDestination(fields: List<String>): HomeDestination {
        val label = fields[4].takeIf { it.isNotEmpty() }?.let(::decodeText)
        return HomeDestination(
            coordinate = GeoCoordinate(fields[1].toDouble(), fields[2].toDouble()),
            arrivalRadiusMeters = fields[3].toDouble().coerceAtLeast(HomeDestination.MIN_HOME_RADIUS_METERS),
            label = label,
            standardizedAddress = fields.getOrNull(5)?.takeIf { it.isNotEmpty() }?.let(::decodeText),
            preparedAtEpochMs = fields.getOrNull(6)?.takeIf { it.isNotEmpty() }?.toLongOrNull(),
            resolutionStatus = fields.getOrNull(7)?.let { runCatching { DestinationResolutionStatus.valueOf(it) }.getOrNull() }
                ?: DestinationResolutionStatus.RESOLVED,
            enabled = true,
            originalAddress = label,
        )
    }

    private fun v3Destination(fields: List<String>): HomeDestination {
        val status = runCatching { DestinationResolutionStatus.valueOf(fields[9]) }.getOrNull()
            ?: DestinationResolutionStatus.UNAVAILABLE
        require(fields[2].isBlank() == fields[3].isBlank()) { "Incomplete coordinate." }
        val coordinate = fields[2].takeIf { it.isNotEmpty() }?.let { latitude ->
            val longitude = fields[3].takeIf { it.isNotEmpty() } ?: error("Incomplete coordinate.")
            GeoCoordinate(latitude.toDouble(), longitude.toDouble())
        }
        return HomeDestination(
            coordinate = coordinate,
            arrivalRadiusMeters = fields[4].toDouble(),
            label = fields[5].takeIf { it.isNotEmpty() }?.let(::decodeText),
            originalAddress = fields[6].takeIf { it.isNotEmpty() }?.let(::decodeText),
            standardizedAddress = fields[7].takeIf { it.isNotEmpty() }?.let(::decodeText),
            preparedAtEpochMs = fields[8].takeIf { it.isNotEmpty() }?.toLongOrNull(),
            resolutionStatus = status,
            enabled = fields[1] == "1",
        )
    }

    private fun encodeText(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(UTF_8))

    private fun decodeText(value: String): String = String(Base64.getUrlDecoder().decode(value), UTF_8)
}
