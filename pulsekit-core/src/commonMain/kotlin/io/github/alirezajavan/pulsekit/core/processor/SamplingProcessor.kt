package io.github.alirezajavan.pulsekit.core.processor

import io.github.alirezajavan.pulsekit.core.db.SensorEventLog
import kotlin.jvm.JvmOverloads

/**
 * Drops N-1 out of every N events of a specific [sensorType].
 *
 * Useful for reducing the volume of chatty sources (like accelerometer) when high fidelity
 * isn't required for every window.
 */
class SamplingProcessor
    @JvmOverloads
    constructor(
    private val sensorType: String,
    private val keepEveryNth: Int,
) : EventProcessor {
    private var count = 0

    init {
        require(keepEveryNth > 0) { "keepEveryNth must be > 0" }
    }

    override fun process(event: SensorEventLog): SensorEventLog? {
        if (event.sensorType != sensorType) return event

        count++
        return if (count % keepEveryNth == 0) {
            count = 0
            event
        } else {
            null
        }
    }
}
