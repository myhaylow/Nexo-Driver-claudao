package br.com.nexo.driver.parser

import br.com.nexo.driver.offer.OfferKind
import br.com.nexo.driver.offer.OfferSource
import br.com.nexo.driver.offer.FieldSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OfferTextParserTest {
    private val registry = OfferParserRegistry()

    @Test
    fun `parses Uber light offer fields`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    UberX
                    R$ 13,58
                    R$ 1,29/km est.
                    4,89 (245)
                    3 min (1,2 km)
                    Rua Arthur Manoel Iwersen, Curitiba
                    19 minutos (9,3 km)
                    Rua Adolfo Saviski, São José dos Pinhais
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertNotNull(offer)
        assertEquals(OfferSource.UBER, offer?.source)
        assertEquals(1_358L, offer?.payout?.value?.cents)
        assertEquals(1_200L, offer?.pickup?.distance?.value?.meters)
        assertEquals(1_140L, offer?.trip?.duration?.value?.seconds)
        assertEquals(489L, offer?.passenger?.rating?.value)
    }

    @Test
    fun `normalizes OCR payout without decimal separator as cents`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    UberX
                    R$ 1358
                    4,89 (245)
                    3 min (1,2 km)
                    19 min (9,3 km)
                """.trimIndent(),
                capturedAtEpochMs = 1L,
                layoutHint = "uber",
            ),
        )

        assertEquals(1_358L, offer?.payout?.value?.cents)
    }

    @Test
    fun `marks parsed fields as accessibility when raw text came from accessibility service`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    UberX
                    R$ 13,58
                    R$ 1,29/km est.
                    4,89 (245)
                    3 min (1,2 km)
                    Rua Arthur Manoel Iwersen, Curitiba
                    19 minutos (9,3 km)
                    Rua Adolfo Saviski, São José dos Pinhais
                """.trimIndent(),
                capturedAtEpochMs = 1L,
                layoutHint = "uber",
                fieldSource = FieldSource.ACCESSIBILITY,
            ),
        )

        assertEquals(FieldSource.ACCESSIBILITY, offer?.payout?.source)
        assertEquals(FieldSource.ACCESSIBILITY, offer?.pickup?.duration?.source)
        assertEquals(FieldSource.ACCESSIBILITY, offer?.passenger?.rating?.source)
    }

    @Test
    fun `uses Uber card payout instead of unrelated earnings chip behind the overlay`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    R$0,00
                    UberX
                    R$ 13,58
                    R$ 1,29/km est.
                    4,89 (245) Verificado
                    3 min (1,2 km)
                    Rua Arthur Manoel Iwersen, Curitiba
                    19 minutos (9,3 km)
                    Rua Adolfo Saviski, São José dos Pinhais
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(1_358L, offer?.payout?.value?.cents)
        assertEquals(129L, offer?.displayedRatePerKm?.value?.cents)
        assertEquals(489L, offer?.passenger?.rating?.value)
        assertEquals(true, offer?.metadata?.hasVerificationBadge)
    }

    @Test
    fun `never uses separated dynamic fare as Uber total payout`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    UberX
                    R$ 32,64
                    R$ 1,92/km est.
                    ★
                    4.91 (318)
                    R$ 5,25
                    Tarifa dinâmica incl.
                    2 min (0,7 km)
                    Retirada
                    27 minutos (14,6 km)
                    Destino
                """.trimIndent(),
                capturedAtEpochMs = 1L,
                layoutHint = "uber",
            ),
        )

        assertEquals(3_264L, offer?.payout?.value?.cents)
    }

    @Test
    fun `rebuilds Uber rating when star number and trips are separate OCR lines`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    UberX
                    R$ 32,64
                    R$ 1,92/km est.
                    ★
                    4.91
                    (318)
                    2 min (0,7 km)
                    Retirada
                    27 minutos (14,6 km)
                    Destino
                """.trimIndent(),
                capturedAtEpochMs = 1L,
                layoutHint = "uber",
            ),
        )

        assertEquals(491L, offer?.passenger?.rating?.value)
        assertEquals(318L, offer?.passenger?.tripCount?.value)
    }

    @Test
    fun `rejects Uber card when OCR misses total and only reads dynamic fare`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    UberX
                    R$ 1,92/km est.
                    ★ 4.91 (318)
                    R$ 5,25
                    ícone promocional
                    Tarifa dinâmica incl.
                    2 min (0,7 km)
                    Retirada
                    27 minutos (14,6 km)
                    Destino
                """.trimIndent(),
                capturedAtEpochMs = 1L,
                layoutHint = "uber",
            ),
        )

        assertNull(offer)
    }

    @Test
    fun `rejects wait compensation as total when main payout is absent`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    Pgto. no app
                    R$1,46/km
                    R$1,81 por espera incl.
                    4,87 · +999 corridas · Perfil Premium
                    6min (1,9km)
                    Rua Nicarágua, Bacacheri
                    13min (4,9km)
                    Praça Nossa Senhora de Salete
                """.trimIndent(),
                capturedAtEpochMs = 1L,
                layoutHint = "99",
            ),
        )

        assertNull(offer)
    }

    @Test
    fun `parses live Uber Comfort without displayed rate per kilometre`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    Arena da Baixada +R$ 6,50
                    Comfort Exclusivo
                    R$ 20,69
                    4,98 (328) Verificado
                    +R$ 5,25 incluído
                    5 min (1.9 km)
                    Av. Victor Ferreira do Amaral, Curitiba
                    14 minutos (4.9 km)
                    Shopping Curitiba, Centro
                    Aceitar
                """.trimIndent(),
                capturedAtEpochMs = 1L,
                layoutHint = "uber",
            ),
        )

        assertEquals(2_069L, offer?.payout?.value?.cents)
        assertEquals(498L, offer?.passenger?.rating?.value)
        assertEquals(2, listOf(offer?.pickup, offer?.trip).count { it?.duration?.value != null })
    }

    @Test
    fun `parses Uber Priority card without UberX label`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    Priority
                    R$ 23,11
                    R$ 1,76/km est.
                    5,00 (2)
                    +R$ 1,89 incluído para embarque prioritário
                    14 min (6.6 km)
                    Rua 3, Ipê, São José dos Pinhais
                    11 minutos (6.5 km)
                    Rua Antônio Emílio Cumin, Uberaba, Curitiba
                    Selecionar
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(OfferSource.UBER, offer?.source)
        assertEquals(2_311L, offer?.payout?.value?.cents)
        assertEquals(840L, offer?.pickup?.duration?.value?.seconds)
        assertEquals(6_500L, offer?.trip?.distance?.value?.meters)
        assertEquals(500L, offer?.passenger?.rating?.value)
    }

    @Test
    fun `uses Uber radar title as a card marker when service label is still visible`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    Radar de viagens
                    Fora do local de destino • Desativado
                    UberX
                    R$ 5,97
                    R$ 1,93/km est.
                    4,90 (149)
                    1 min (0.3 km)
                    Rua Dias da Rocha Filho, Alto da Rua Quinze, Curitiba
                    8 minutos (2.8 km)
                    R. Jacy Loureiro de Campos 2º e 3º Andares, S/N, Centro Cívico, Curitiba
                    Selecionar
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(OfferSource.UBER, offer?.source)
        assertEquals(597L, offer?.payout?.value?.cents)
        assertEquals(60L, offer?.pickup?.duration?.value?.seconds)
        assertEquals(2_800L, offer?.trip?.distance?.value?.meters)
    }

    @Test
    fun `parses route legs when OCR splits duration and distance lines`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    R$ 23,11
                    R$ 1,76/km est.
                    5,00 (2)
                    14 min
                    6.6 km
                    Rua 3, Ipê, São José dos Pinhais
                    11 minutos
                    6.5 km
                    Rua Antônio Emílio Cumin, Uberaba, Curitiba
                    Selecionar
                """.trimIndent(),
                capturedAtEpochMs = 1L,
                layoutHint = "uber",
            ),
        )

        assertEquals(2_311L, offer?.payout?.value?.cents)
        assertEquals(840L, offer?.pickup?.duration?.value?.seconds)
        assertEquals(6_600L, offer?.pickup?.distance?.value?.meters)
        assertEquals("Rua 3, Ipê, São José dos Pinhais", offer?.pickup?.location?.value?.address)
        assertEquals(660L, offer?.trip?.duration?.value?.seconds)
        assertEquals(6_500L, offer?.trip?.distance?.value?.meters)
    }

    @Test
    fun `reads rating when OCR merges it with included dynamic value`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    UberX Exclusivo
                    R$ 29,71
                    4,96 (163) +R$ 5,25 incluído
                    6 min (2.4 km)
                    Rua General Adalberto G. de Menezes, Curitiba
                    22 minutos (14.5 km)
                    São José dos Pinhais
                    Aceitar
                """.trimIndent(),
                capturedAtEpochMs = 1L,
                layoutHint = "uber",
            ),
        )

        assertEquals(2_971L, offer?.payout?.value?.cents)
        assertEquals(496L, offer?.passenger?.rating?.value)
    }

    @Test
    fun `parses 99 standard offer and bonus`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    Pgto. no app
                    R$8,50
                    R$1,50/km
                    R$2,71 Tarifa base dinâmica incl.
                    4,96 · 112 corridas · Perfil Essencial
                    5min (1,6km)
                    Rua Joaquim Nabuco, Centro
                    6min (4,1km)
                    Rua José Fernandes Alves, Uberaba
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(OfferSource.NINETY_NINE, offer?.source)
        assertEquals(OfferKind.NINETY_NINE_STANDARD, offer?.kind)
        assertEquals(850L, offer?.payout?.value?.cents)
        assertEquals(150L, offer?.displayedRatePerKm?.value?.cents)
        assertEquals(271L, offer?.bonus?.value?.cents)
        assertEquals(496L, offer?.passenger?.rating?.value)
        assertEquals(112L, offer?.passenger?.tripCount?.value)
        assertEquals(300L, offer?.pickup?.duration?.value?.seconds)
        assertEquals(4100L, offer?.trip?.distance?.value?.meters)
        assertEquals(true, offer?.metadata?.hasDynamicFare)
    }

    @Test
    fun `ignores implausible 99 payout above two hundred reais`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    Pgto. no app
                    R$ 250,00
                    4,87 · +999 corridas · Perfil Premium
                    3 min (1,2 km)
                    Rua José Fernandes Alves, Uberaba
                    19 min (9,3 km)
                    Rua Francisco Derosso, Xaxim
                """.trimIndent(),
                capturedAtEpochMs = 1L,
                layoutHint = "99",
            ),
        )

        assertNull(offer)
    }

    @Test
    fun `keeps 99 passenger rating separate from pickup distance digits`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    Pgto. no app
                    R$8,50
                    4,96 · 112 corridas · Perfil Essencial
                    5min (1,6km)
                    Rua Joaquim Nabuco, Centro
                    6min (4,1km)
                    Rua José Fernandes Alves, Uberaba
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(496L, offer?.passenger?.rating?.value)
        assertEquals(1_600L, offer?.pickup?.distance?.value?.meters)
    }

    @Test
    fun `does not use a route distance as rating when OCR reading order is irregular`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    Pgto. no app
                    R$8,50
                    5min (1,6km)
                    Rua Exemplo, Centro
                    6min (4,1km)
                    Rua Destino, Bairro
                    4,96 112 corridas Perfil Essencial
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(496L, offer?.passenger?.rating?.value)
    }

    @Test
    fun `treats Negocia as a separate layout using its initial value`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    Negocia Nova
                    R$20,92
                    5,00 · 47 corridas · Perfil Essencial
                    (9 min 3,7 km) Rua Genoveva Forlepa Kopka
                    (35 min 18,3 km) Rua São Francisco Pereira dos Santos
                    R$21,97 R$23,01 R$24,06
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(OfferKind.NINETY_NINE_NEGOCIA, offer?.kind)
        assertEquals(2_092L, offer?.payout?.value?.cents)
        assertEquals(3_700L, offer?.pickup?.distance?.value?.meters)
        assertEquals(2_100L, offer?.trip?.duration?.value?.seconds)
        assertEquals("Rua Genoveva Forlepa Kopka", offer?.pickup?.location?.value?.address)
        assertEquals("Rua São Francisco Pereira dos Santos", offer?.trip?.location?.value?.address)
        assertEquals(listOf(2_197L, 2_301L, 2_406L), offer?.metadata?.negotiationAlternatives?.map { it.cents })
        assertEquals(500L, offer?.passenger?.rating?.value)
        assertEquals(47L, offer?.passenger?.tripCount?.value)
    }

    @Test
    fun `prioritizes a 99 Negocia card over an unrelated Uber floating bubble`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    Uber →
                    Solicitações
                    Negocia Nova
                    R$20,92
                    5,00 · 47 corridas · Perfil Essencial
                    (9 min 3,7 km) Rua Genoveva Forlepa Kopka
                    (35 min 18,3 km) Rua São Francisco Pereira dos Santos
                    Aceitar por R$20,92
                    R$21,97 R$23,01 R$24,06
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(OfferKind.NINETY_NINE_NEGOCIA, offer?.kind)
        assertEquals(2_092L, offer?.payout?.value?.cents)
    }

    @Test
    fun `recognizes explicit multiple stops labels without a numeric count`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    UberX
                    R$ 16,40
                    4,92 (301)
                    Múltiplas paradas
                    4 min (1,1 km)
                    Rua das Flores, Curitiba
                    18 min (8,4 km)
                    Rua do Sol, Curitiba
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(2L, offer?.stopCount?.value)
    }

    @Test
    fun `normalizes an additional 99 stop as at least two total stops`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    Pgto. no app
                    R$12,20
                    4,96 · 112 corridas · Perfil Essencial
                    1 parada adicional
                    5min (1,6km)
                    Rua Joaquim Nabuco, Centro
                    12min (5,1km)
                    Rua José Fernandes Alves, Uberaba
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(2L, offer?.stopCount?.value)
    }

    @Test
    fun `does not mistake an ordinary bus stop reference for a multiple-stop offer`() {
        val offer = registry.parse(
            RawOfferText(
                text = """
                    UberX
                    R$ 13,58
                    4,89 (245)
                    3 min (1,2 km)
                    Próximo à parada de ônibus Rua Arthur Manoel Iwersen, Curitiba
                    19 minutos (9,3 km)
                    Rua Adolfo Saviski, São José dos Pinhais
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(null, offer?.stopCount?.value)
    }

    @Test
    fun `recognizes an explicit long trip label regardless of numeric thresholds`() {
        val parser = UberTextParser()
        val offer = parser.parse(
            RawOfferText(
                text = """
                    UberX
                    R$ 13,58
                    Long trip
                    3 min (1,2 km)
                    Rua Arthur Manoel Iwersen, Curitiba
                    19 minutos (9,3 km)
                    Rua Adolfo Saviski, São José dos Pinhais
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(true, offer?.longTripHint?.value)
    }

    @Test
    fun `can derive long trip from configurable total distance`() {
        val parser = UberTextParser(
            longTripDetection = LongTripDetectionConfig(
                minimumTotalDistanceMeters = 10_000L,
            ),
        )
        val offer = parser.parse(
            RawOfferText(
                text = """
                    UberX
                    R$ 13,58
                    3 min (1,2 km)
                    Rua Arthur Manoel Iwersen, Curitiba
                    19 minutos (9,3 km)
                    Rua Adolfo Saviski, São José dos Pinhais
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(true, offer?.longTripHint?.value)
    }

    @Test
    fun `can classify a known short trip as not long with configured trip thresholds`() {
        val parser = NinetyNineTextParser(
            longTripDetection = LongTripDetectionConfig(
                minimumTripDurationSeconds = 20 * 60L,
                minimumTripDistanceMeters = 10_000L,
            ),
        )
        val offer = parser.parse(
            RawOfferText(
                text = """
                    Pgto. no app
                    R$8,50
                    5min (1,6km)
                    Rua Joaquim Nabuco, Centro
                    6min (4,1km)
                    Rua José Fernandes Alves, Uberaba
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(false, offer?.longTripHint?.value)
    }

    @Test
    fun `reports the recognized source when a card marker is present but fields cannot be extracted`() {
        // "UberX" is a real card marker, but there is no "R$" payout line -- the layout drifted
        // (or OCR missed a block), so parsing must fail without silently pretending nothing
        // was on screen.
        val attempt = registry.parseAttempt(
            RawOfferText(
                text = """
                    UberX
                    4,89 (245)
                """.trimIndent(),
                capturedAtEpochMs = 1L,
            ),
        )

        assertEquals(null, attempt.offer)
        assertEquals(OfferSource.UBER, attempt.unrecognizedLayoutSource)
    }

    @Test
    fun `parses Uber split route legs with descriptive accessibility text`() {
        val offer = UberTextParser().parse(
            RawOfferText(
                text = """
                    Solicitação de corrida
                    R$ 23,11
                    R$ 1,76/km est.
                    5,00 (2)
                    14 min até o local de partida
                    6.6 km
                    11 minutos até o destino
                    6.5 km
                    Selecionar
                """.trimIndent(),
                capturedAtEpochMs = 1L,
                layoutHint = "uber",
            ),
        )

        assertEquals(2_311L, offer?.payout?.value?.cents)
        assertEquals(14 * 60L, offer?.pickup?.duration?.value?.seconds)
        assertEquals(6_600L, offer?.pickup?.distance?.value?.meters)
        assertEquals(11 * 60L, offer?.trip?.duration?.value?.seconds)
        assertEquals(6_500L, offer?.trip?.distance?.value?.meters)
    }

    @Test
    fun `reports no unrecognized layout when no card marker is visible at all`() {
        val attempt = registry.parseAttempt(
            RawOfferText(text = "Texto sem card de oferta", capturedAtEpochMs = 1L),
        )

        assertEquals(null, attempt.offer)
        assertEquals(null, attempt.unrecognizedLayoutSource)
    }
}
