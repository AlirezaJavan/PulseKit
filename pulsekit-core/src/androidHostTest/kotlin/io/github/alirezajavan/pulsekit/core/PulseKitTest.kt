package io.github.alirezajavan.pulsekit.core

import io.github.alirezajavan.pulsekit.core.db.createPulseKitDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
    private class FakeDataSource(
        override val id: String,
        private val startSucceeds: Boolean = true,
        override val isSupported: Boolean = true,
    ) : DataSource {
        val sink = MutableSharedFlow<SensorPayload>(extraBufferCapacity = 64)
        var startCount = 0
            private set
        var stopCount = 0
            private set

        override suspend fun start(): Boolean {
            startCount++
            return startSucceeds
        }

        override suspend fun stop() {
            stopCount++
        }

        override fun events(): Flow<SensorPayload> = sink.asSharedFlow()
    }

    private fun TestScope.newPulseKit(vararg sources: Pair<DataSource, CollectionMode>): PulseKit {
        val builder = PulseKit.builder(createPulseKitDatabase(RuntimeEnvironment.getApplication()))
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
        assertEquals(1, motion.startCount)
        assertEquals(0, location.startCount)

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
        assertEquals(1, motion.stopCount)
        assertEquals(0, location.stopCount)

        pulseKit.stop()
    }

    @Test
    fun aSourceWhoseStartFailsDoesNotStayMarkedActive() = runTest {
        val denied = FakeDataSource("location", startSucceeds = false)
        val pulseKit = newPulseKit(denied to CollectionMode.Continuous)

        pulseKit.startSources(setOf("location"))
        runCurrent()

        assertEquals(1, denied.startCount)
        assertEquals(0, denied.stopCount, "a source that never started must not be stopped")
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

        motion.sink.emit(SensorPayload.StepCount(steps = 7L))
        advanceTimeBy(1_500) // past the default ingestion flush interval
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
        advanceTimeBy(1_500) // past the default ingestion flush interval
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
        assertEquals(1, bluetooth.startCount, "first window starts immediately")
        assertEquals(0, bluetooth.stopCount)

        advanceTimeBy(1_100) // past the first window
        runCurrent()
        assertEquals(1, bluetooth.stopCount, "source is stopped at the end of its window")

        advanceTimeBy(5_000) // into the next interval
        runCurrent()
        assertEquals(2, bluetooth.startCount, "the next interval starts a fresh window")

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

        assertEquals(0, unsupported.startCount)
        assertTrue(pulseKit.activeSourceIds.value.isEmpty())

        pulseKit.stop()
    }
}
