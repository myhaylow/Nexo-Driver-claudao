package br.com.nexo.driver.overlay

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.com.nexo.driver.ui.theme.DriverInteligenteTheme
import br.com.nexo.driver.ui.theme.DriverThemeMode
import br.com.nexo.driver.ui.theme.DriverVisualStyle
import br.com.nexo.driver.speech.OfferDecisionSpeaker
import br.com.nexo.driver.overlay.preferences.OverlayMetricField

/** Debug-only host used to inspect the real overlay composable without FLAG_SECURE hiding it. */
class OverlayVisualTestActivity : ComponentActivity() {
    private val speechHandler = Handler(Looper.getMainLooper())
    private var speaker: OfferDecisionSpeaker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Pinned to the neon identity so the rendering test's exact-colour check is
            // deterministic; the app-wide default is a different style, but this harness only needs
            // a fixed, known palette to assert against.
            DriverInteligenteTheme(mode = DriverThemeMode.LIGHT, visualStyle = DriverVisualStyle.CURRENT) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF3A403D))
                        .padding(top = 64.dp, start = 12.dp, end = 12.dp),
                ) {
                    OfferOverlayCard(acceptedUberFixtureModel())
                }
            }
        }
        if (intent.getBooleanExtra(EXTRA_SPEECH_TEST, false)) {
            speaker = OfferDecisionSpeaker(this)
            listOf(
                1_000L to OverlayStatus.ACCEPT,
                3_500L to OverlayStatus.ANALYZE,
                6_000L to OverlayStatus.REJECT,
            ).forEach { (delayMs, status) ->
                speechHandler.postDelayed(
                    { speaker?.speak(acceptedUberFixtureModel().copy(status = status)) },
                    delayMs,
                )
            }
        }
    }

    override fun onDestroy() {
        speechHandler.removeCallbacksAndMessages(null)
        speaker?.close()
        speaker = null
        super.onDestroy()
    }

    private companion object {
        const val EXTRA_SPEECH_TEST = "speech_test"
    }
}

private fun acceptedUberFixtureModel() = OfferOverlayUiModel(
    status = OverlayStatus.ACCEPT,
    totalDuration = "22 min",
    payout = "R$ 13,58",
    payoutStatus = OverlayStatus.ACCEPT,
    ratePerKm = OverlayMetricUi("R$ 1,29", OverlayStatus.ACCEPT),
    ratePerHour = OverlayMetricUi("R$ 37,03", OverlayStatus.ACCEPT),
    passengerRating = OverlayMetricUi("4,89", OverlayStatus.ACCEPT),
    pickup = OverlayMetricUi("3 min · 1,2 km", OverlayStatus.ACCEPT),
    totalDurationMetric = OverlayMetricUi("22 min", OverlayStatus.ACCEPT),
    totalDistance = OverlayMetricUi("10,5 km", OverlayStatus.ACCEPT),
    netProfit = OverlayMetricUi("R$ 10,20", OverlayStatus.ACCEPT),
    netProfitPercent = OverlayMetricUi("75%", OverlayStatus.ACCEPT),
    netProfitPerHour = OverlayMetricUi("R$ 27,82", OverlayStatus.ANALYZE),
    // The default grid, matching what the rendering test asserts is visible.
    gridFields = listOf(
        OverlayMetricField.RATE_PER_KM,
        OverlayMetricField.RATE_PER_HOUR,
        OverlayMetricField.PASSENGER_RATING,
        OverlayMetricField.NET_PROFIT,
    ),
)
