package br.com.nexo.driver.overlay

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayVisualRenderingTest {
    @Test
    fun rendersApprovedCardContentAndAcceptColorOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val startedAt = System.nanoTime()
        ActivityScenario.launch(OverlayVisualTestActivity::class.java).use {
            instrumentation.waitForIdleSync()

            val texts = instrumentation.uiAutomation.rootInActiveWindow.collectVisibleText()
            val renderedAtMillis = (System.nanoTime() - startedAt) / 1_000_000
            Log.i("OfferOverlayRenderLatency", "activity-to-readable-card: ${renderedAtMillis}ms")
            assertTrue(
                "Overlay was not readable within one second: ${renderedAtMillis}ms",
                renderedAtMillis <= 1_000,
            )
            for (expected in listOf(
                "ACEITAR CORRIDA",
                "R$ 1,29",
                "R$ 37,03",
                "4,89",
                "R$ 10,20",
                "10,5 km",
                "22 min",
            )) {
                assertTrue("Overlay is missing '$expected'. Visible: $texts", texts.any { expected in it })
            }

            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                assertTrue("Accept green was not rendered in the overlay", screenshot.countPixelsNear(ACCEPT_GREEN) > 150)
            } finally {
                screenshot.recycle()
            }
        }
    }

    private fun android.view.accessibility.AccessibilityNodeInfo?.collectVisibleText(): List<String> {
        val root = this ?: return emptyList()
        val result = mutableListOf<String>()
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo) {
            node.text?.toString()?.takeIf(String::isNotBlank)?.let(result::add)
            repeat(node.childCount) { index -> node.getChild(index)?.let(::visit) }
        }
        visit(root)
        return result
    }

    private fun Bitmap.countPixelsNear(expected: Int): Int {
        var count = 0
        for (y in 0 until height step 2) {
            for (x in 0 until width step 2) {
                val actual = getPixel(x, y)
                if (
                    kotlin.math.abs(Color.red(actual) - Color.red(expected)) <= 8 &&
                    kotlin.math.abs(Color.green(actual) - Color.green(expected)) <= 8 &&
                    kotlin.math.abs(Color.blue(actual) - Color.blue(expected)) <= 8
                ) count++
            }
        }
        return count
    }

    private companion object {
        const val ACCEPT_GREEN = 0xFF00FF00.toInt()
    }
}
