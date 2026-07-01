package io.github.alirezajavan.pulsekit.motion

import io.github.alirezajavan.pulsekit.core.MotionSample

/**
 * Accumulates raw motion samples and yields a completed, ordered chunk once [chunkSize] samples
 * have been collected. Pure logic extracted out of the platform actuals (which previously each
 * duplicated this exact accumulation loop) so there is exactly one implementation of the
 * chunking rule, and it is unit-testable without any Android/iOS sensor API.
 *
 * Not thread-safe on its own -- callers are responsible for serializing calls to [add]/[clear]
 * (e.g. via `@Synchronized` on Android, or a dedicated serial queue on iOS), since the safe
 * serialization strategy differs per platform.
 */
internal class MotionSampleBuffer(private val chunkSize: Int) {
    private val samples = ArrayList<MotionSample>(chunkSize)

    /** Adds [sample]; returns a completed chunk (in insertion order) once [chunkSize] is reached, else null. */
    fun add(sample: MotionSample): List<MotionSample>? {
        samples.add(sample)
        if (samples.size < chunkSize) return null
        val chunk = ArrayList(samples)
        samples.clear()
        return chunk
    }

    /** Discards any partially-accumulated samples without emitting a chunk. */
    fun clear() {
        samples.clear()
    }
}
