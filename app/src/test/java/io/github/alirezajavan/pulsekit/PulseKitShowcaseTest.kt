package io.github.alirezajavan.pulsekit

import io.github.alirezajavan.pulsekit.core.CollectionMode
import io.github.alirezajavan.pulsekit.core.PulseKit
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.testing.FakeDataSource
import io.github.alirezajavan.pulsekit.testing.MutableTimeProvider
import io.github.alirezajavan.pulsekit.testing.inMemoryPulseKitDatabase
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/**
 * A showcase test demonstrating how to use the `:pulsekit-testing` infrastructure to test
 * PulseKit integrations deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PulseKitShowcaseTest {

    @Test
    fun `test pulsekit integration with fake data and virtual time`() = runTest {
        // 1. Setup testing infrastructure
        val timeProvider = MutableTimeProvider(initialMillis = 1000L)
        val database = inMemoryPulseKitDatabase()
        val fakeDataSource = FakeDataSource(id = "showcase-sensor")

        // 2. Build PulseKit with injected test components
        val pulseKit = PulseKit.builder(database)
            .addDataSource(fakeDataSource, CollectionMode.Continuous)
            .timeProvider(timeProvider)
            .collectionScope(backgroundScope) // Run ingestion loop on test scope
            .build()

        // 3. Start collection
        pulseKit.start()
        runCurrent()

        // 4. Simulate a sensor event at T=1000
        fakeDataSource.emit(SensorPayload.StepCount(steps = 100L))

        // 5. Advance time and simulate another event at T=5000
        timeProvider.advanceBy(4000L)
        fakeDataSource.emit(SensorPayload.StepCount(steps = 105L))

        // 6. Wait for ingestion flush (default is 1s)
        advanceTimeBy(1500.milliseconds)
        runCurrent()

        // 7. Assert on the results via the sync source
        val events = pulseKit.syncSource.claimPendingBatch(limit = 10)

        assertEquals(2, events.size)

        // Verify first event
        assertEquals(1000L, events[0].timestamp)
        assertEquals("showcase-sensor", events[0].sensorType)
        assertEquals(100L, (events[0].payload as SensorPayload.StepCount).steps)

        // Verify second event
        assertEquals(5000L, events[1].timestamp)
        assertEquals(105L, (events[1].payload as SensorPayload.StepCount).steps)

        pulseKit.stop()
    }
}
