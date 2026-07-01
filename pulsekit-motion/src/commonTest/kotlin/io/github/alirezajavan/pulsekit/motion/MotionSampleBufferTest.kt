package io.github.alirezajavan.pulsekit.motion

import io.github.alirezajavan.pulsekit.core.MotionSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MotionSampleBufferTest {
    private fun sample(timestamp: Long) =
        MotionSample(timestamp = timestamp, x = 0f, y = 0f, z = 9.8f)

    @Test
    fun emitsNothingUntilChunkSizeIsReached() {
        val buffer = MotionSampleBuffer(chunkSize = 50)

        // Simulates ~1 second of a 50Hz accelerometer feed falling one sample short of a full chunk.
        val chunks = (1..49).map { buffer.add(sample(it.toLong())) }

        assertTrue(
            chunks.all {
            it == null
        },
            "no chunk should be emitted before chunkSize samples have arrived",
        )
    }

    @Test
    fun emitsExactlyOneOrderedChunkPerChunkSizeSamples() {
        val buffer = MotionSampleBuffer(chunkSize = 50)

        // Simulates 2 seconds of a 50Hz feed (100 samples) -- exactly two chunk boundaries.
        val chunks = (1..100L).mapNotNull { buffer.add(sample(it)) }

        assertEquals(2, chunks.size)
        assertEquals(
            (1..50L).toList(),
            chunks[0].map {
            it.timestamp
        },
            "first chunk must preserve arrival order",
        )
        assertEquals(
            (51..100L).toList(),
            chunks[1].map {
            it.timestamp
        },
            "second chunk must not repeat or skip samples",
        )
    }

    @Test
    fun clearDiscardsAPartialChunkWithoutEmittingIt() {
        val buffer = MotionSampleBuffer(chunkSize = 10)

        repeat(7) { i -> assertNull(buffer.add(sample(i.toLong()))) }
        buffer.clear() // e.g. stop() called mid-stream

        // The next full chunk must contain only samples logged after clear(), not a mix of the
        // discarded partial accumulation plus new ones.
        val chunk = (100..109L).mapNotNull { buffer.add(sample(it)) }.single()
        assertEquals((100..109L).toList(), chunk.map { it.timestamp })
    }

    @Test
    fun chunkSizeOfOneEmitsEverySampleImmediately() {
        val buffer = MotionSampleBuffer(chunkSize = 1)

        val chunks = (1..5L).mapNotNull { buffer.add(sample(it)) }

        assertEquals(
            listOf(listOf(1L), listOf(2L), listOf(3L), listOf(4L), listOf(5L)),
            chunks.map {
            it.map { s -> s.timestamp }
        },
        )
    }

    @Test
    fun highVolumeStreamNeverLosesOrDuplicatesASample() {
        val chunkSize = 64
        val buffer = MotionSampleBuffer(chunkSize)
        val totalSamples = 10_000L

        // Simulates a sustained high-frequency burst (e.g. a burst of BLE-triggered wakeups
        // replaying buffered high-rate accelerometer data) far larger than any single chunk.
        val chunks = (1..totalSamples).mapNotNull { buffer.add(sample(it)) }

        val allTimestamps = chunks.flatten().map { it.timestamp }
        assertEquals((totalSamples / chunkSize).toInt(), chunks.size)
        assertEquals(
            allTimestamps,
            allTimestamps.distinct(),
            "no sample should ever appear in two chunks",
        )
        assertEquals((1..chunks.size * chunkSize.toLong()).toList(), allTimestamps)
    }
}
