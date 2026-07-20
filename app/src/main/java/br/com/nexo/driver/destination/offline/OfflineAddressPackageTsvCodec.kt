package br.com.nexo.driver.destination.offline

import br.com.nexo.driver.destination.GeoCoordinate
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Thrown when an offline address package is not a valid Driver Inteligente TSV document. */
class OfflineAddressPackageFormatException(message: String) : IllegalArgumentException(message)

/**
 * Compact, deterministic UTF-8 codec for offline address packages.
 *
 * The complete on-disk schema is deliberately small and documented here so a package can be
 * produced without Android, a map SDK, or a network connection:
 *
 * ```text
 * DRIVER_INTELIGENTE_OFFLINE_ADDRESS\t1\n
 * META\t<package name>\t<semantic version>\t<city>\n
 * PLACE\t<id>\t<label>\t<latitude>\t<longitude>[\t<alias> ...]\n
 * ```
 *
 * `\n` is LF (not CRLF), fields are literal UTF-8 text, and fields may not contain tabs,
 * newlines, or ASCII control characters. Aliases are additional `PLACE` columns, rather than a
 * comma-separated sub-format, so a comma in an address is represented without escaping. The
 * first line is an exact magic header and the second line is the one and only metadata row.
 * Every subsequent non-empty row is a place. A final LF is optional when reading and always
 * emitted when writing.
 *
 * Decoding intentionally rejects malformed UTF-8, unknown row types, invalid numbers, duplicate
 * aliases, and every validation error from [OfflineAddressPackageValidator]. It does not silently
 * repair an import: callers either receive a complete, usable package or an exception.
 */
object OfflineAddressPackageTsvCodec {
    const val MAGIC = "DRIVER_INTELIGENTE_OFFLINE_ADDRESS"
    const val FORMAT_VERSION = "1"

    private const val META_ROW = "META"
    private const val PLACE_ROW = "PLACE"
    private const val MAX_PACKAGE_BYTES = 8 * 1024 * 1024
    private const val MAX_PLACE_ROWS = 100_000
    private val strictDecimal = Regex("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?")

    /** Serializes a valid package as canonical UTF-8 TSV. */
    fun encode(addressPackage: OfflineAddressPackage): ByteArray {
        validateForEncoding(addressPackage)
        return buildString {
            append(MAGIC).append('\t').append(FORMAT_VERSION).append('\n')
            append(META_ROW).append('\t')
            append(addressPackage.metadata.name).append('\t')
            append(addressPackage.metadata.version).append('\t')
            append(addressPackage.metadata.city).append('\n')
            addressPackage.places.forEach { place ->
                append(PLACE_ROW).append('\t')
                append(place.id).append('\t')
                append(place.label).append('\t')
                append(place.coordinate.latitude.toCanonicalDecimal()).append('\t')
                append(place.coordinate.longitude.toCanonicalDecimal())
                place.aliases.sorted().forEach { alias -> append('\t').append(alias) }
                append('\n')
            }
        }.toByteArray(StandardCharsets.UTF_8)
    }

    /** Decodes strictly from UTF-8 bytes. Invalid byte sequences are rejected rather than replaced. */
    fun decode(bytes: ByteArray): OfflineAddressPackage {
        if (bytes.size > MAX_PACKAGE_BYTES) fail("Pacote excede o limite de ${MAX_PACKAGE_BYTES} bytes")
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            fail("Pacote nao esta codificado em UTF-8 valido")
        }
        return decode(text)
    }

    /** Decodes strictly from TSV text. Prefer [decode] with bytes for imported files. */
    fun decode(text: String): OfflineAddressPackage {
        if (text.isEmpty()) fail("Pacote vazio")
        if (text.startsWith('\uFEFF')) fail("Pacote nao pode conter BOM UTF-8")
        if ('\r' in text) fail("Use LF, nao CRLF, como quebra de linha")
        if (text.any { it.code in 0..31 && it != '\n' && it != '\t' || it.code == 127 }) {
            fail("Pacote contem caractere de controle invalido")
        }

        val payload = if (text.endsWith('\n')) text.dropLast(1) else text
        if (payload.endsWith('\n')) fail("Pacote contem linha vazia")
        val lines = payload.split('\n')
        if (lines.size < 2) fail("Cabecalho ou metadados ausentes")
        if (lines.any { it.isEmpty() }) fail("Pacote contem linha vazia")

        val header = lines[0].split('\t')
        if (header != listOf(MAGIC, FORMAT_VERSION)) fail("Cabecalho ou versao de formato invalido")

        val metadataFields = lines[1].split('\t')
        if (metadataFields.size != 4 || metadataFields.firstOrNull() != META_ROW) {
            fail("Linha META deve conter exatamente nome, versao e cidade")
        }
        metadataFields.drop(1).forEachIndexed { index, field ->
            if (field.isBlank()) fail("Campo META ${index + 1} esta vazio")
            validateFieldCharacters(field, "campo META ${index + 1}")
        }

        if (lines.size - 2 > MAX_PLACE_ROWS) fail("Pacote excede o limite de $MAX_PLACE_ROWS locais")
        val places = lines.drop(2).mapIndexed { index, line -> parsePlace(line, index + 3) }
        val addressPackage = OfflineAddressPackage(
            metadata = OfflineAddressPackageMetadata(
                name = metadataFields[1],
                version = metadataFields[2],
                city = metadataFields[3],
            ),
            places = places,
        )
        val validation = addressPackage.validate()
        if (!validation.isValid) {
            fail("Pacote falhou na validacao: ${validation.errors.joinToString { it.code.name }}")
        }
        return addressPackage
    }

    private fun parsePlace(line: String, lineNumber: Int): OfflineAddressPlace {
        val fields = line.split('\t')
        if (fields.size < 5 || fields.firstOrNull() != PLACE_ROW) {
            fail("Linha $lineNumber deve ser PLACE com id, rotulo, latitude e longitude")
        }
        val id = fields[1]
        val label = fields[2]
        if (id.isBlank()) fail("ID vazio na linha $lineNumber")
        if (label.isBlank()) fail("Rotulo vazio na linha $lineNumber")
        validateFieldCharacters(id, "id na linha $lineNumber")
        validateFieldCharacters(label, "rotulo na linha $lineNumber")

        val latitude = parseCoordinate(fields[3], "latitude", lineNumber)
        val longitude = parseCoordinate(fields[4], "longitude", lineNumber)
        val coordinate = GeoCoordinate(latitude, longitude)
        if (!coordinate.isValid) fail("Coordenada fora dos limites na linha $lineNumber")

        val aliases = fields.drop(5)
        if (aliases.any { it.isBlank() }) fail("Alias vazio na linha $lineNumber")
        aliases.forEach { validateFieldCharacters(it, "alias na linha $lineNumber") }
        if (aliases.toSet().size != aliases.size) fail("Alias duplicado na linha $lineNumber")
        return OfflineAddressPlace(id = id, label = label, coordinate = coordinate, aliases = aliases.toSet())
    }

    private fun parseCoordinate(raw: String, name: String, lineNumber: Int): Double {
        if (!strictDecimal.matches(raw)) fail("$name invalida na linha $lineNumber")
        return raw.toDoubleOrNull()?.takeIf(Double::isFinite)
            ?: fail("$name invalida na linha $lineNumber")
    }

    private fun validateForEncoding(addressPackage: OfflineAddressPackage) {
        val validation = addressPackage.validate()
        if (!validation.isValid) {
            fail("Nao e possivel serializar pacote invalido: ${validation.errors.joinToString { it.code.name }}")
        }
        listOf(
            addressPackage.metadata.name,
            addressPackage.metadata.version,
            addressPackage.metadata.city,
        ).forEachIndexed { index, field -> validateFieldCharacters(field, "campo META ${index + 1}") }
        addressPackage.places.forEach { place ->
            validateFieldCharacters(place.id, "id ${place.id}")
            validateFieldCharacters(place.label, "rotulo ${place.id}")
            place.aliases.forEach { validateFieldCharacters(it, "alias ${place.id}") }
        }
    }

    private fun validateFieldCharacters(value: String, description: String) {
        if (value.any { it == '\t' || it == '\n' || it == '\r' || it.code in 0..31 || it.code == 127 }) {
            fail("$description contem tabulacao, quebra de linha ou caractere de controle")
        }
        var index = 0
        while (index < value.length) {
            val current = value[index]
            if (current.isHighSurrogate()) {
                if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) {
                    fail("$description contem surrogate UTF-16 sem par")
                }
                index += 2
            } else {
                if (current.isLowSurrogate()) fail("$description contem surrogate UTF-16 sem par")
                index++
            }
        }
    }

    private fun Double.toCanonicalDecimal(): String {
        check(isFinite()) { "Coordenada nao finita" }
        return toString()
    }

    private fun fail(message: String): Nothing = throw OfflineAddressPackageFormatException(message)
}
