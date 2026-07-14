package io.github.alirezajavan.pulsekit.core.processor

import io.github.alirezajavan.pulsekit.core.db.SensorEventLog

/**
 * Intercepts events after they are captured but before they are persisted to storage.
 *
 * Processors can transform, enrich, or filter events (by returning null). They run in the
 * order they were registered.
 */
fun interface EventProcessor {
    /**
     * Processes the given event. Returning null drops the event from the pipeline.
     *
     * IMPORTANT: This method runs on the ingestion loop. It should be synchronous and fast
     * to avoid stalling the persistence pipeline.
     */
    fun process(event: SensorEventLog): SensorEventLog?
}
