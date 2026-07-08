package io.github.alirezajavan.pulsekit.core

import io.github.alirezajavan.pulsekit.testing.FakeDataSource
import io.github.alirezajavan.pulsekit.testing.inMemoryPulseKitDatabase
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises [PulseKit]'s per-source lifecycle — selective start/stop, the observable
 * [PulseKit.activeSourceIds], failed-start handling, and [CollectionMode.Periodic] scheduling —
 * against a real (Robolectric-backed) database, driving collection on `runTest`'s virtual-time
 * [TestScope.backgroundScope] so periodic delays are deterministic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PulseKitTest {
    private fun TestScope.newPulseKit(vararg sources: Pair<DataSource, CollectionMode>): PulseKit {
        val builder = PulseKit.builder(inMemoryPulseKitDatabase())
            .collectionScope(backgroundScope)
        for ((source, mode) in sources) builder.addDataSource(source, mode)
        return builder.build()
    }

    @Test
    fun startSourcesStartsOnlyTheRequestedSourceAndReportsItActive() = runTest {
        val motion = FakeDataSource("motion")
        val location = FakeDataSource("location")
        val pulseKit = newPulseKit(
            motion to CollectionMode.Continuous,
            location to CollectionMode.Continuous,
        )

        pulseKit.startSources(setOf("motion"))
        runCurrent()

        assertEquals(setOf("motion"), pulseKit.activeSourceIds.value)
        assertEquals(1, motion.getStartCount())
        assertEquals(0, location.getStartCount())

        pulseKit.stop()
    }

    @Test
    fun aSecondSourceCanBeAddedToAnAlreadyRunningCollection() = runTest {
        val motion = FakeDataSource("motion")
        val location = FakeDataSource("location")
        val pulseKit = newPulseKit(
            motion to CollectionMode.Continuous,
            location to CollectionMode.Continuous,
        )

        pulseKit.startSources(setOf("motion"))
        runCurrent()
        pulseKit.startSources(setOf("location"))
        runCurrent()

        assertEquals(setOf("motion", "location"), pulseKit.activeSourceIds.value)

        pulseKit.stop()
    }

    @Test
    fun stopSourcesStopsAndReleasesOnlyThatSourceLeavingOthersRunning() = runTest {
        val motion = FakeDataSource("motion")
        val location = FakeDataSource("location")
        val pulseKit = newPulseKit(
            motion to CollectionMode.Continuous,
            location to CollectionMode.Continuous,
        )

        pulseKit.startSources(setOf("motion", "location"))
        runCurrent()
        pulseKit.stopSources(setOf("motion"))

        assertEquals(setOf("location"), pulseKit.activeSourceIds.value)
        assertEquals(1, motion.getStopCount())
        assertEquals(0, location.getStopCount())

        pulseKit.stop()
    }

    @Test
    fun aSourceWhoseStartFailsDoesNotStayMarkedActive() = runTest {
        val denied = FakeDataSource("location").apply { startResult = false }
        val pulseKit = newPulseKit(denied to CollectionMode.Continuous)

        pulseKit.startSources(setOf("location"))
        runCurrent()

        assertEquals(1, denied.getStartCount())
        assertEquals(0, denied.getStopCount(), "a source that never started must not be stopped")
        assertTrue(
            pulseKit.activeSourceIds.value.isEmpty(),
            "a failed start must leave the active set again so UI reflects reality",
        )

        pulseKit.stop()
    }

    @Test
    fun collectedEventsFromAStartedSourceArePersisted() = runTest {
        val motion = FakeDataSource("motion")
        val pulseKit = newPulseKit(motion to CollectionMode.Continuous)

        pulseKit.startSources(setOf("motion"))
        runCurrent() // let start() run and the collector subscribe before emitting

        motion.emit(SensorPayload.StepCount(steps = 7L))
        advanceTimeBy(1500.milliseconds) // past the default ingestion flush interval
        runCurrent()

        val persisted = pulseKit.syncSource.claimPendingBatch(limit = 10)
        assertEquals(1, persisted.size)
        assertEquals("motion", persisted.single().sensorType)

        pulseKit.stop()
    }

    @Test
    fun recordEventPersistsEvenWhenNoDataSourceHasEverBeenStarted() = runTest {
        val pulseKit = newPulseKit()

        pulseKit.recordEvent(SensorPayload.StepCount(steps = 1L), type = "manual_ping")
        advanceTimeBy(1500.milliseconds) // past the default ingestion flush interval
        runCurrent()

        val persisted = pulseKit.syncSource.claimPendingBatch(limit = 10)
        assertEquals(
            1,
            persisted.size,
            "recordEvent must not silently strand in the ingestion channel",
        )
        assertEquals("manual_ping", persisted.single().sensorType)

        pulseKit.stop()
    }

    @Test
    fun periodicSourceStartsCollectsForItsWindowThenStopsAndRepeatsNextInterval() = runTest {
        val bluetooth = FakeDataSource("bluetooth")
        val pulseKit = newPulseKit(
            bluetooth to CollectionMode.Periodic(intervalMillis = 5_000L, windowMillis = 1_000L),
        )

        pulseKit.startSources(setOf("bluetooth"))
        runCurrent()
        assertEquals(1, bluetooth.getStartCount(), "first window starts immediately")
        assertEquals(0, bluetooth.getStopCount())

        advanceTimeBy(1_100.milliseconds) // past the first window
        runCurrent()
        assertEquals(1, bluetooth.getStopCount(), "source is stopped at the end of its window")

        advanceTimeBy(5_000.milliseconds) // into the next interval
        runCurrent()
        assertEquals(2, bluetooth.getStartCount(), "the next interval starts a fresh window")

        pulseKit.stop()
    }

    @Test
    fun startSourcesIgnoresUnknownIdsWithoutAffectingKnownOnes() = runTest {
        val motion = FakeDataSource("motion")
        val pulseKit = newPulseKit(motion to CollectionMode.Continuous)

        pulseKit.startSources(setOf("motion", "does_not_exist"))
        runCurrent()

        assertEquals(setOf("motion"), pulseKit.activeSourceIds.value)

        pulseKit.stop()
    }

    @Test
    fun aSourceThatIsNotSupportedIsNeverStartedAndNeverMarkedActive() = runTest {
        val unsupported = FakeDataSource("motion", isSupported = false)
        val pulseKit = newPulseKit(unsupported to CollectionMode.Continuous)

        pulseKit.startSources(setOf("motion"))
        runCurrent()

        assertEquals(0, unsupported.getStartCount())
        assertTrue(pulseKit.activeSourceIds.value.isEmpty())

        pulseKit.stop()
    }
}
