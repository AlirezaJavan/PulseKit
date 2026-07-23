package io.github.alirezajavan.pulsekit.geofence

import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.db.SensorEventLog
import io.github.alirezajavan.pulsekit.core.processor.EventProcessor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A geofence transition event.
 */
data class GeofenceEvent(
    val regionId: String,
    val transition: Transition,
    val timestamp: Long,
) {
    enum class Transition {
        ENTER,
        EXIT,
    }
}

/**
 * An [EventProcessor] that monitors location events and triggers geofence transitions.
 *
 * @param regions The list of regions to monitor.
 * @param hysteresisMeters The additional distance a device must move beyond the radius to trigger an EXIT.
 * @param minAccuracyMeters Locations with accuracy worse than this value will be ignored.
 */
class GeofenceProcessor(
    val regions: List<GeofenceRegion>,
    private val hysteresisMeters: Double = 10.0,
    private val minAccuracyMeters: Float = 100f,
) : EventProcessor {
    private val _events = MutableSharedFlow<GeofenceEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    val events: SharedFlow<GeofenceEvent> = _events.asSharedFlow()

    private val _currentlyInside = MutableStateFlow<Set<String>>(emptySet())
    val currentlyInside: StateFlow<Set<String>> = _currentlyInside.asStateFlow()

    private val regionStates = regions.associate { it.id to RegionState() }.toMutableMap()

    override fun process(event: SensorEventLog): SensorEventLog? {
        val payload = event.payload
        if (payload is SensorPayload.Location) {
            if (payload.accuracy <= minAccuracyMeters) {
                evaluateRegions(payload, event.timestamp)
            }
        }
        return event
    }

    private fun evaluateRegions(location: SensorPayload.Location, timestamp: Long) {
        regions.forEach { region ->
            val distance = haversineMeters(
                location.latitude,
                location.longitude,
                region.latitude,
                region.longitude,
            )
            val state = regionStates[region.id] ?: return@forEach

            val isCurrentlyInside = state.isInside
            val radiusWithHysteresis = if (isCurrentlyInside) {
                region.radiusMeters + hysteresisMeters
            } else {
                region.radiusMeters
            }

            val isNowInside = distance <= radiusWithHysteresis

            if (isNowInside != isCurrentlyInside) {
                state.isInside = isNowInside
                val transition = if (isNowInside) {
                    _currentlyInside.update { it + region.id }
                    GeofenceEvent.Transition.ENTER
                } else {
                    _currentlyInside.update { it - region.id }
                    GeofenceEvent.Transition.EXIT
                }
                _events.tryEmit(GeofenceEvent(region.id, transition, timestamp))
            }
        }
    }

    private class RegionState {
        var isInside: Boolean = false
    }
}
