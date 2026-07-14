package io.github.alirezajavan.pulsekit.core.processor

import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.db.SensorEventLog
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Coarse-grains [SensorPayload.Location] coordinates to a fixed number of decimal places.
 *
 * Useful for enhancing user privacy by ensuring exact house-level locations are never
 * stored. Pass null to [decimalPlacesProvider] to disable rounding.
 */
class LocationPrecisionProcessor(
    private val decimalPlacesProvider: () -> Int?,
) : EventProcessor {
    override fun process(event: SensorEventLog): SensorEventLog? {
        val payload = event.payload
        if (payload !is SensorPayload.Location) return event

        val decimalPlaces = decimalPlacesProvider() ?: return event
        val factor = 10.0.pow(decimalPlaces)

        val roundedPayload = payload.copy(
            latitude = (payload.latitude * factor).roundToLong() / factor,
            longitude = (payload.longitude * factor).roundToLong() / factor,
        )

        return event.copy(payload = roundedPayload)
    }
}
