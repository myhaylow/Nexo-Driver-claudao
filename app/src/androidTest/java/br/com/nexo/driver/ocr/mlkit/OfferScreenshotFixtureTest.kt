package br.com.nexo.driver.ocr.mlkit

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.nexo.driver.evaluation.OfferEvaluator
import br.com.nexo.driver.ocr.OcrTextSnapshot
import br.com.nexo.driver.ocr.OfferOcrPipeline
import br.com.nexo.driver.offer.OfferKind
import br.com.nexo.driver.offer.OfferSource
import br.com.nexo.driver.overlay.OfferOverlayPresenter
import br.com.nexo.driver.overlay.OverlayStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end visual regressions built from real, user-approved Uber/99 screenshots.
 *
 * Each fixture executes the production on-device ML Kit recognizer and the real parser. No raw
 * OCR text or screenshot is persisted by the app at runtime; these images are test-only assets.
 */
@RunWith(AndroidJUnit4::class)
class OfferScreenshotFixtureTest {

    @Test
    fun recognizesApprovedUberAnd99Screenshots() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        MlKitBitmapOcrEngine().use { engine ->
            FIXTURES.forEachIndexed { index, expected ->
                val bitmap = assets.open("offer-fixtures/${expected.fileName}").use(BitmapFactory::decodeStream)
                assertNotNull("Could not decode ${expected.fileName}", bitmap)
                try {
                    val startedAt = System.nanoTime()
                    val blocks = engine.recognize(requireNotNull(bitmap))
                    val diagnostic = blocks.joinToString(" | ") { it.text.replace('\n', ' ') }
                    Log.i("OfferFixtureOcr", "${expected.fileName}: $diagnostic")
                    val output = OfferOcrPipeline().process(
                        OcrTextSnapshot(
                            blocks = blocks,
                            capturedAtEpochMs = 1_000L + index,
                        ),
                    )
                    assertNotNull("${expected.fileName} was not parsed. OCR: $diagnostic", output.offer)
                    val offer = requireNotNull(output.offer)

                    assertEquals(expected.fileName, expected.source, offer.source)
                    assertEquals(expected.fileName, expected.kind, offer.kind)
                    assertEquals(expected.fileName, expected.payoutCents, offer.payout.value?.cents)
                    assertEquals(expected.fileName, expected.pickupSeconds, offer.pickup.duration.value?.seconds)
                    assertEquals(expected.fileName, expected.pickupMeters, offer.pickup.distance.value?.meters)
                    assertEquals(expected.fileName, expected.tripSeconds, offer.trip.duration.value?.seconds)
                    assertEquals(expected.fileName, expected.tripMeters, offer.trip.distance.value?.meters)
                    assertEquals(expected.fileName, expected.ratingHundredths, offer.passenger.rating.value)
                    assertEquals(expected.fileName, expected.tripCount, offer.passenger.tripCount.value)

                    val evaluator = OfferEvaluator()
                    val derived = evaluator.derive(offer)
                    assertEquals(expected.fileName, expected.totalSeconds, derived.totalDuration.value?.seconds)
                    assertEquals(expected.fileName, expected.totalMeters, derived.totalDistance.value?.meters)
                    assertEquals(expected.fileName, expected.ratePerKmCents, derived.ratePerKm.value)
                    assertEquals(expected.fileName, expected.ratePerHourCents, derived.ratePerHour.value)

                    val overlay = OfferOverlayPresenter(evaluator).present(
                        offer = offer,
                        result = evaluator.evaluate(offer, emptyList()),
                    )
                    assertEquals(expected.fileName, OverlayStatus.ANALYZE, overlay.status)
                    assertEquals(expected.fileName, expected.payoutLabel, overlay.payout.replace('\u00a0', ' '))
                    assertEquals(expected.fileName, expected.totalDurationLabel, overlay.totalDuration)
                    assertEquals(expected.fileName, expected.totalDistanceLabel, overlay.totalDistance.value)
                    assertEquals(expected.fileName, expected.ratePerKmLabel, overlay.ratePerKm.value.replace('\u00a0', ' '))
                    assertEquals(expected.fileName, expected.ratePerHourLabel, overlay.ratePerHour.value.replace('\u00a0', ' '))
                    val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
                    Log.i("OfferFixtureLatency", "${expected.fileName}: ${elapsedMillis}ms")
                    assertTrue(
                        "${expected.fileName} exceeded the one-second ceiling: ${elapsedMillis}ms",
                        elapsedMillis <= 1_000,
                    )
                } finally {
                    bitmap?.recycle()
                }
            }
        }
    }

    private data class ExpectedOffer(
        val fileName: String,
        val source: OfferSource,
        val kind: OfferKind,
        val payoutCents: Long,
        val pickupSeconds: Long,
        val pickupMeters: Long,
        val tripSeconds: Long,
        val tripMeters: Long,
        val ratingHundredths: Long,
        val tripCount: Long?,
        val totalSeconds: Long,
        val totalMeters: Long,
        val ratePerKmCents: Long,
        val ratePerHourCents: Long,
        val payoutLabel: String,
        val totalDurationLabel: String,
        val totalDistanceLabel: String,
        val ratePerKmLabel: String,
        val ratePerHourLabel: String,
    )

    private companion object {
        val FIXTURES = listOf(
            ExpectedOffer("uber_dark_1358.jpg", OfferSource.UBER, OfferKind.UBER_STANDARD, 1_358, 180, 1_200, 1_140, 9_300, 489, 245, 1_320, 10_500, 129, 3_703, "R$ 13,58", "22 min", "10,5 km", "R$ 1,29", "R$ 37,03"),
            ExpectedOffer("uber_light_1407.jpg", OfferSource.UBER, OfferKind.UBER_STANDARD, 1_407, 300, 2_100, 840, 7_000, 472, 1_235, 1_140, 9_100, 154, 4_443, "R$ 14,07", "19 min", "9,1 km", "R$ 1,54", "R$ 44,43"),
            ExpectedOffer("ninety_nine_dark_850.jpg", OfferSource.NINETY_NINE, OfferKind.NINETY_NINE_STANDARD, 850, 300, 1_600, 360, 4_100, 496, 112, 660, 5_700, 149, 4_636, "R$ 8,50", "11 min", "5,7 km", "R$ 1,49", "R$ 46,36"),
            ExpectedOffer("ninety_nine_dark_990.jpg", OfferSource.NINETY_NINE, OfferKind.NINETY_NINE_STANDARD, 990, 360, 1_900, 780, 4_900, 487, 999, 1_140, 6_800, 145, 3_126, "R$ 9,90", "19 min", "6,8 km", "R$ 1,45", "R$ 31,26"),
            ExpectedOffer("ninety_nine_negocia_2092.jpg", OfferSource.NINETY_NINE, OfferKind.NINETY_NINE_NEGOCIA, 2_092, 540, 3_700, 2_100, 18_300, 500, 47, 2_640, 22_000, 95, 2_852, "R$ 20,92", "44 min", "22,0 km", "R$ 0,95", "R$ 28,52"),
        )
    }
}
