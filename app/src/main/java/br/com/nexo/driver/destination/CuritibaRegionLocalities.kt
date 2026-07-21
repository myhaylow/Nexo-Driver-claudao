package br.com.nexo.driver.destination

/**
 * Curitiba e Região Metropolitana: nomes de municípios (e UF/país) que os apps de corrida anexam
 * ao fim dos endereços ("..., São José dos Pinhais - PR, Brasil"). Esses sufixos nunca identificam
 * uma casa sozinhos e atrapalham o casamento exato de endereços; os matchers os removem do FINAL
 * do texto normalizado antes de comparar.
 *
 * As frases estão na forma normalizada usada pelos matchers: minúsculas, sem acentos, tokens
 * separados por espaço único.
 */
object CuritibaRegionLocalities {

    /** Municípios da RMC + capital + UF/país, em tokens normalizados. */
    val NORMALIZED_PHRASES: List<List<String>> = listOf(
        // Frases mais longas primeiro, para o sufixo maior ganhar.
        "campina grande do sul",
        "sao jose dos pinhais",
        "almirante tamandare",
        "rio branco do sul",
        "fazenda rio grande",
        "bocaiuva do sul",
        "tijucas do sul",
        "agudos do sul",
        "doutor ulysses",
        "quatro barras",
        "campo magro",
        "campo largo",
        "balsa nova",
        "cerro azul",
        "mandirituba",
        "itaperucu",
        "piraquara",
        "araucaria",
        "contenda",
        "curitiba",
        "colombo",
        "pinhais",
        "adrianopolis",
        "quitandinha",
        "tunas do parana",
        "lapa",
        "pien",
        "parana",
        "brasil",
        "brazil",
        "pr",
    ).map { phrase -> phrase.split(' ') }
        .sortedByDescending { it.size }

    /**
     * Remove repetidamente do FINAL da lista de tokens qualquer frase de localidade conhecida.
     * Só sufixos são removidos: "Rua Curitiba, 100" mantém "curitiba" no meio do endereço.
     */
    fun stripTrailingLocalities(tokens: List<String>): List<String> {
        var result = tokens
        var changed = true
        while (changed && result.isNotEmpty()) {
            changed = false
            for (phrase in NORMALIZED_PHRASES) {
                if (result.size >= phrase.size && result.takeLast(phrase.size) == phrase) {
                    result = result.dropLast(phrase.size)
                    changed = true
                    break
                }
            }
        }
        return result
    }

    // Caixa geográfica aproximada da RMC, para o geocoder priorizar resultados locais.
    const val BOUNDS_SOUTH_LATITUDE = -26.40
    const val BOUNDS_NORTH_LATITUDE = -24.65
    const val BOUNDS_WEST_LONGITUDE = -50.15
    const val BOUNDS_EAST_LONGITUDE = -48.75
}
