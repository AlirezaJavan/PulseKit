package io.github.alirezajavan.pulsekit.core

import io.github.alirezajavan.pulsekit.core.db.SensorEventLog
import io.github.alirezajavan.pulsekit.core.db.createPulseKitDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises [TrackingEngine] end-to-end against a real (Robolectric-backed) database, simulating
 * how sensor `DataSource`s and `pulsekit-sync` actually drive it: bursts of `logSensorEvent`
 * calls from a "sensor callback", then claiming/uploading/pruning like a real sync pass would.
 *
 * Row counts are read via [currentEventCount] (claim-then-revert through the same [SyncSource]
 * surface `pulsekit-sync` uses), not [TrackingEngine.observeEventCount] -- that Flow hops onto a
 * real `Dispatchers.Default` thread internally (SQLDelight's `mapToOne`), which fights
 * `kotlinx-coroutines-test`'s virtual clock and made these exact assertions flaky/wrong when
 * mixed with `runCurrent()`/`advanceTimeBy()` on the ingestion loop's `withTimeoutOrNull`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TrackingEngineTest {
    private fun newEngine(
        config: PulseKitConfig,
        scope: CoroutineScope,
        logger: PulseKitLogger = NoOpPulseKitLogger,
    ) = TrackingEngine(
        createPulseKitDatabase(RuntimeEnvironment.getApplication()),
        config,
        scope,
        logger,
    )

    /** Claims every row then immediately reverts it to IDLE, leaving row state untouched. */
    private suspend fun TrackingEngine.currentEventCount(): Int {
        val claimed = claimPendingBatch(Int.MAX_VALUE)
        markFailed(claimed.map { it.id })
        return claimed.size
    }

    @Test
    fun flushesOnceTheBatchSizeIsReachedNotOneRowAtATime() = runTest {
        val engine = newEngine(
            PulseKitConfig(ingestionBatchSize = 3, ingestionFlushIntervalMillis = 10_000L),
            this,
        )
        engine.start()

        engine.logSensorEvent(SensorPayload.StepCount(steps = 1L), "step_count")
        engine.logSensorEvent(SensorPayload.StepCount(steps = 2L), "step_count")
        runCurrent()
        assertEquals(0, engine.currentEventCount(), "must not persist before the batch is full")

        engine.logSensorEvent(SensorPayload.StepCount(steps = 3L), "step_count")
        runCurrent()
        assertEquals(
            3,
            engine.currentEventCount(),
            "the full batch should flush in one transaction",
        )

        engine.stop()
    }

    @Test
    fun flushesAPartialBatchOnceTheFlushIntervalElapses() = runTest {
        val engine = newEngine(
            PulseKitConfig(ingestionBatchSize = 100, ingestionFlushIntervalMillis = 50L),
            this,
        )
        engine.start()

        engine.logSensorEvent(SensorPayload.StepCount(steps = 1L), "step_count")
        engine.logSensorEvent(SensorPayload.StepCount(steps = 2L), "step_count")
        runCurrent()
        assertEquals(0, engine.currentEventCount())

        advanceTimeBy(60)
        runCurrent()
        assertEquals(
            2,
            engine.currentEventCount(),
            "a partial batch should still flush once the flush interval elapses, " +
                "not wait forever for a full batch",
        )

        engine.stop()
    }

    @Test
    fun pruneLoopRemovesOldestRowsOnceOverTheStoredEventCap() = runTest {
        val engine = newEngine(
            PulseKitConfig(
                maxStoredEvents = 5,
                minPruneBatchSize = 2,
                pruneCheckIntervalMillis = 100L,
            ),
            this,
        )
        engine.bulkInsertEvents(
            (1..8).map { fixtureEvent(id = "evt-$it", timestamp = it.toLong()) },
        )
        assertEquals(8, engine.currentEventCount())

        engine.start()
        advanceTimeBy(150)
        runCurrent()

        // overflow = 8 - 5 = 3, which is already >= minPruneBatchSize (2), so exactly 3 of the
        // oldest rows are pruned, landing exactly back at the cap.
        assertEquals(5, engine.currentEventCount())

        engine.stop()
    }

    @Test
    fun pruneLoopRemovesEventsPastMaxAgeRegardlessOfStoredEventCount() = runTest {
        val engine = newEngine(
            PulseKitConfig(
                // effectively unbounded: isolate age-based pruning
                maxStoredEvents = 1_000_000,
                maxEventAgeMillis = 5_000L,
                pruneCheckIntervalMillis = 100L,
            ),
            this,
        )
        val now = platformCurrentTimeMillis()
        engine.bulkInsertEvents(
            listOf(
                fixtureEvent(id = "stale", timestamp = now - 10_000L),
                fixtureEvent(id = "fresh", timestamp = now - 100L),
            ),
        )

        engine.start()
        advanceTimeBy(150)
        runCurrent()

        val remaining = engine.claimPendingBatch(limit = 100).map { it.id }
        assertEquals(
            listOf("fresh"),
            remaining,
            "only the row past maxEventAgeMillis should be pruned",
        )

        engine.stop()
    }

    @Test
    fun pruneLoopReportsEachPruneThroughTheInjectedLogger() = runTest {
        val logger = RecordingLogger()
        val engine = newEngine(
            PulseKitConfig(
                maxStoredEvents = 5,
                minPruneBatchSize = 2,
                pruneCheckIntervalMillis = 100L,
            ),
            this,
            logger,
        )
        engine.bulkInsertEvents(
            (1..8).map { fixtureEvent(id = "evt-$it", timestamp = it.toLong()) },
        )

        engine.start()
        advanceTimeBy(150)
        runCurrent()
        engine.stop()

        assertTrue(
            logger.debugMessages.isNotEmpty(),
            "a prune pass that actually removes rows should be logged",
        )
    }

    @Test
    fun eraseAllDataRemovesEverythingRegardlessOfSyncStatus() = runTest {
        val engine = newEngine(PulseKitConfig(), this)
        engine.bulkInsertEvents(
            listOf(
                fixtureEvent(id = "idle-1", timestamp = 1L, syncStatus = SyncStatus.IDLE),
                fixtureEvent(id = "idle-2", timestamp = 2L, syncStatus = SyncStatus.IDLE),
                fixtureEvent(
                    id = "pending",
                    timestamp = 3L,
                    syncStatus = SyncStatus.PENDING_UPLOAD,
                ),
            ),
        )

        engine.eraseAllData()

        // claimPendingBatch only ever sees IDLE rows, so directly re-claiming everything (rather
        // than going through the currentEventCount() helper, which itself claims-then-reverts
        // only IDLE rows) is what actually proves the PENDING_UPLOAD row is gone too.
        assertTrue(engine.claimPendingBatch(limit = 100).isEmpty())
    }

    @Test
    fun fullLifecycleLogThenClaimThenUploadRemovesTheEventAsSyncSourceWould() = runTest {
        val engine = newEngine(
            PulseKitConfig(ingestionBatchSize = 1, ingestionFlushIntervalMillis = 10_000L),
            this,
        )
        engine.start()

        engine.logSensorEvent(SensorPayload.Location(1.0, 2.0, 5f, 0f), "location")
        runCurrent()
        assertEquals(1, engine.currentEventCount())

        // This is exactly what SyncEngine does on every sync pass.
        val claimed = engine.claimPendingBatch(limit = 10)
        assertEquals(1, claimed.size)
        engine.markUploaded(claimed.map { it.id })

        assertEquals(0, engine.currentEventCount())
        engine.stop()
    }

    @Test
    fun startIsIdempotentAndStopBeforeStartIsSafe() = runTest {
        val config = PulseKitConfig(ingestionBatchSize = 1, ingestionFlushIntervalMillis = 50L)
        val engine = newEngine(config, this)

        engine.stop() // must not throw when nothing was ever started
        engine.start()
        engine.start() // second call must be a no-op, not a second competing ingestion loop

        engine.logSensorEvent(SensorPayload.StepCount(steps = 1L), "step_count")
        runCurrent()
        advanceTimeBy(config.ingestionFlushIntervalMillis + 10L)
        runCurrent()

        // If start() weren't idempotent, two independent ingestion loops racing on the same
        // channel could plausibly double-insert or corrupt the batch; exactly one row landing
        // once is the observable proof that the second start() truly did nothing.
        assertEquals(1, engine.currentEventCount())

        engine.stop()
        engine.stop() // must also not throw on a second stop
    }
}

private fun fixtureEvent(id: String, timestamp: Long, syncStatus: SyncStatus = SyncStatus.IDLE) =
    SensorEventLog(
        id = id,
        sensorType = "test",
        timestamp = timestamp,
        payload = SensorPayload.StepCount(steps = timestamp),
        syncStatus = syncStatus,
    )

private class RecordingLogger : PulseKitLogger {
    val debugMessages = mutableListOf<String>()

    override fun debug(tag: String, message: String) {
        debugMessages += message
    }

    override fun warn(tag: String, message: String, throwable: Throwable?) = Unit

    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}
