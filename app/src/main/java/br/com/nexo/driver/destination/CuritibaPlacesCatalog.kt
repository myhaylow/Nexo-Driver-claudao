package br.com.nexo.driver.destination

import java.text.Normalizer

/**
 * Um lugar sugerível do catálogo embutido de Curitiba e Região Metropolitana.
 *
 * A coordenada é o centroide aproximado do bairro/município (precisão de bairro, ~1 km) — serve
 * para o raio de chegada padrão (2 km) e para a direção "sentido casa". Para precisão de rua o
 * motorista usa o GPS de 1 toque ou o resolvedor de endereço.
 */
data class CuritibaPlace(
    val name: String,
    val city: String,
    val coordinate: GeoCoordinate,
) {
    val displayName: String get() = if (city == name) name else "$name – $city"
}

/**
 * Catálogo offline embutido: os principais bairros de Curitiba + as sedes dos municípios da RMC.
 * Nenhuma rede é usada; a busca é por prefixo/contains em texto normalizado (sem acentos).
 */
object CuritibaPlacesCatalog {

    /** Lugares do catálogo até [maxDistanceMeters] de [center], mais próximos primeiro. */
    fun nearby(center: GeoCoordinate, maxDistanceMeters: Double, limit: Int = 8): List<CuritibaPlace> {
        if (!center.isValid) return emptyList()
        val metersPerDegreeLng = METERS_PER_DEGREE_LAT * kotlin.math.cos(Math.toRadians(center.latitude))
        return places
            .map { indexed ->
                val dLat = (indexed.place.coordinate.latitude - center.latitude) * METERS_PER_DEGREE_LAT
                val dLng = (indexed.place.coordinate.longitude - center.longitude) * metersPerDegreeLng
                indexed.place to kotlin.math.sqrt(dLat * dLat + dLng * dLng)
            }
            .filter { (_, distance) -> distance <= maxDistanceMeters }
            .sortedBy { (_, distance) -> distance }
            .take(limit)
            .map { (place, _) -> place }
    }

    private const val METERS_PER_DEGREE_LAT = 111_320.0

    fun search(query: String, limit: Int = 6): List<CuritibaPlace> {
        val normalized = normalize(query)
        if (normalized.length < 2) return emptyList()
        // Prefixo ganha de contains para a sugestão mais intuitiva aparecer primeiro.
        val prefix = places.filter { it.normalizedName.startsWith(normalized) }
        val contains = places.filter {
            !it.normalizedName.startsWith(normalized) && it.normalizedFull.contains(normalized)
        }
        return (prefix + contains).take(limit).map(IndexedPlace::place)
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private class IndexedPlace(val place: CuritibaPlace) {
        val normalizedName = normalize(place.name)
        val normalizedFull = normalize(place.displayName)
    }

    private fun bairro(name: String, lat: Double, lng: Double) =
        IndexedPlace(CuritibaPlace(name, "Curitiba", GeoCoordinate(lat, lng)))

    private fun cidade(name: String, lat: Double, lng: Double) =
        IndexedPlace(CuritibaPlace(name, name, GeoCoordinate(lat, lng)))

    private val places: List<IndexedPlace> = listOf(
        // ---- Bairros de Curitiba (centroides aproximados) ----
        bairro("Centro", -25.4284, -49.2733),
        bairro("Batel", -25.4430, -49.2860),
        bairro("Água Verde", -25.4570, -49.2830),
        bairro("Bigorrilho", -25.4360, -49.3010),
        bairro("Mercês", -25.4230, -49.2960),
        bairro("Bom Retiro", -25.4110, -49.2860),
        bairro("Ahú", -25.4050, -49.2680),
        bairro("Cabral", -25.4030, -49.2560),
        bairro("Juvevê", -25.4150, -49.2560),
        bairro("Alto da XV", -25.4270, -49.2530),
        bairro("Cristo Rei", -25.4330, -49.2470),
        bairro("Jardim Botânico", -25.4420, -49.2390),
        bairro("Rebouças", -25.4480, -49.2660),
        bairro("Prado Velho", -25.4550, -49.2560),
        bairro("Parolin", -25.4650, -49.2660),
        bairro("Guaíra", -25.4750, -49.2730),
        bairro("Portão", -25.4780, -49.2900),
        bairro("Vila Izabel", -25.4550, -49.2950),
        bairro("Novo Mundo", -25.4950, -49.2900),
        bairro("Capão Raso", -25.5100, -49.2950),
        bairro("Pinheirinho", -25.5250, -49.2900),
        bairro("Sítio Cercado", -25.5350, -49.2650),
        bairro("Boqueirão", -25.5100, -49.2400),
        bairro("Alto Boqueirão", -25.5300, -49.2350),
        bairro("Xaxim", -25.4950, -49.2550),
        bairro("Hauer", -25.4780, -49.2500),
        bairro("Fanny", -25.4700, -49.2450),
        bairro("Uberaba", -25.4700, -49.2200),
        bairro("Cajuru", -25.4450, -49.2200),
        bairro("Capão da Imbuia", -25.4350, -49.2230),
        bairro("Tarumã", -25.4230, -49.2200),
        bairro("Bairro Alto", -25.4150, -49.2100),
        bairro("Bacacheri", -25.4000, -49.2330),
        bairro("Boa Vista", -25.3880, -49.2430),
        bairro("Santa Cândida", -25.3700, -49.2280),
        bairro("Tingui", -25.3850, -49.2270),
        bairro("Barreirinha", -25.3720, -49.2500),
        bairro("Cachoeira", -25.3550, -49.2570),
        bairro("Abranches", -25.3750, -49.2700),
        bairro("Pilarzinho", -25.3950, -49.2820),
        bairro("São Lourenço", -25.3900, -49.2650),
        bairro("Vista Alegre", -25.4050, -49.3000),
        bairro("Santa Felicidade", -25.3920, -49.3300),
        bairro("Campo Comprido", -25.4400, -49.3400),
        bairro("Mossunguê", -25.4400, -49.3200),
        bairro("Ecoville", -25.4380, -49.3180),
        bairro("Campina do Siqueira", -25.4400, -49.3080),
        bairro("Seminário", -25.4450, -49.3000),
        bairro("Santa Quitéria", -25.4600, -49.3050),
        bairro("Fazendinha", -25.4750, -49.3100),
        bairro("Cidade Industrial", -25.4900, -49.3400),
        bairro("CIC", -25.4900, -49.3400),
        bairro("Tatuquara", -25.5550, -49.3100),
        bairro("Umbará", -25.5700, -49.2800),
        bairro("Ganchinho", -25.5600, -49.2450),
        bairro("Jardim das Américas", -25.4550, -49.2350),
        bairro("Guabirotuba", -25.4550, -49.2450),
        // ---- Sedes dos municípios da RMC ----
        cidade("São José dos Pinhais", -25.5340, -49.2060),
        cidade("Colombo", -25.2920, -49.2240),
        cidade("Pinhais", -25.4450, -49.1920),
        cidade("Piraquara", -25.4420, -49.0630),
        cidade("Quatro Barras", -25.3650, -49.0770),
        cidade("Campina Grande do Sul", -25.3050, -49.0550),
        cidade("Almirante Tamandaré", -25.3240, -49.3100),
        cidade("Rio Branco do Sul", -25.1900, -49.3140),
        cidade("Itaperuçu", -25.2200, -49.3450),
        cidade("Campo Magro", -25.3690, -49.4500),
        cidade("Campo Largo", -25.4590, -49.5280),
        cidade("Balsa Nova", -25.5800, -49.6300),
        cidade("Araucária", -25.5930, -49.4100),
        cidade("Contenda", -25.6810, -49.5350),
        cidade("Fazenda Rio Grande", -25.6620, -49.3070),
        cidade("Mandirituba", -25.7770, -49.3260),
        cidade("Quitandinha", -25.8720, -49.4970),
        cidade("Tijucas do Sul", -25.9310, -49.1950),
        cidade("Agudos do Sul", -25.9900, -49.3340),
        cidade("Piên", -26.0960, -49.4330),
        cidade("Lapa", -25.7700, -49.7160),
        cidade("Bocaiúva do Sul", -25.2060, -49.1140),
        cidade("Tunas do Paraná", -24.9720, -49.0860),
        cidade("Cerro Azul", -24.8250, -49.2610),
        cidade("Doutor Ulysses", -24.5680, -49.4210),
        cidade("Adrianópolis", -24.6600, -48.9910),
    )
}
