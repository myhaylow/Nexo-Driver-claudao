package br.com.nexo.driver.destination

import br.com.nexo.driver.offer.Confidence
import br.com.nexo.driver.offer.FieldSource
import br.com.nexo.driver.offer.GeoText
import br.com.nexo.driver.offer.NormalizedOffer
import java.util.concurrent.Executor

/** Optional asynchronous enrichment for a textual drop-off; the caller decides when to re-run analysis. */
class OfferDestinationGeocoder(
    private val resolver: GeocoderDestinationResolver,
    private val callbackExecutor: Executor? = null,
) {
    fun resolveAsync(offer: NormalizedOffer, callback: (NormalizedOffer) -> Unit) {
        val existing = offer.trip.location.value?.coordinate
        if (existing?.isValid == true) {
            callback(offer)
            return
        }
        val text = offer.trip.location.value?.address?.trim()
        if (!GeocoderDestinationResolver.isUseful(text.orEmpty())) {
            callback(offer)
            return
        }
        resolver.resolveAsync(text.orEmpty()) { result ->
            val coordinate = result.coordinate
            val enriched = if (coordinate == null) offer else offer.copy(
                trip = offer.trip.copy(
                    location = Confidence(
                        value = GeoText(result.standardizedAddress ?: text, offer.trip.location.value?.locality, coordinate),
                        score = 1f,
                        source = FieldSource.DERIVED,
                    ),
                ),
            )
            callbackExecutor?.execute { callback(enriched) } ?: callback(enriched)
        }
    }
}
