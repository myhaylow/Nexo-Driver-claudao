package br.com.nexo.driver.screenreader.domain

import java.math.BigDecimal

/**
 * Provider-neutral, ephemeral representation produced by the screen-reader stage.
 * It deliberately contains values only; no AccessibilityNodeInfo or image reference escapes
 * the capture callback.
 */
data class RideOffer(
    val source: RideSource,
    val windowId: Int,
    val category: String?,
    val price: BigDecimal?,
    val pickupDistanceKm: Double?,
    val pickupDurationMinutes: Int?,
    val tripDistanceKm: Double?,
    val tripDurationMinutes: Int?,
    val totalDistanceKm: Double?,
    val totalDurationMinutes: Int?,
    val passengerRating: Double?,
    val confidence: Float,
    val captureMethod: CaptureMethod,
    val capturedAt: Long,
)

enum class RideSource { UBER, NINETY_NINE }

enum class CaptureMethod { ACCESSIBILITY_TREE, ACCESSIBILITY_SCREENSHOT_OCR, HYBRID }
