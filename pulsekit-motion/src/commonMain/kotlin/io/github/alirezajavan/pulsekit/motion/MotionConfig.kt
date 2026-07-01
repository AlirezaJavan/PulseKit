package io.github.alirezajavan.pulsekit.motion

/**
 * Tunables for [MotionDataSource]. Raw accelerometer/gyroscope samples are buffered locally and
 * emitted as one [io.github.alirezajavan.pulsekit.core.SensorPayload.MotionChunk] every
 * [chunkSize] samples, so the core engine persists one row per chunk instead of one row per
 * sample.
 */
data class MotionConfig(
    /** Number of raw samples batched into a single MotionChunk emission. */
    val chunkSize: Int = 50,
    /** Requested sensor sampling interval, in microseconds (e.g. ~20_000 = 50Hz). */
    val samplingPeriodMicros: Int = 20_000,
)
