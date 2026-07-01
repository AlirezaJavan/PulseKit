package io.github.alirezajavan.pulsekit.core

import io.github.alirezajavan.pulsekit.core.db.createPulseKitDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Simulates what actually happens on a device under heavy load: several sensor `DataSource`s
 * (motion, location, BLE, step count) all emitting concurrently, at a rate the persistence layer
 * can't instantly keep up with. Deliberately uses real threads (`Dispatchers.Default`, not
 * `kotlinx-coroutines-test` virtual time) for the producers, so genuine race conditions between
 * concurrent senders and the single-consumer ingestion loop have a chance to actually manifest,
 * instead of being hidden by cooperative single-threaded test scheduling.
 *
 * The engine itself (and every direct DB read the test does to verify state) is pinned to one
 * dedicated thread via [dbDispatcher]: Robolectric's SQLite shim (`sqlite4java`) requires all
 * operations against one connection to stay on the exact same thread it was opened on -- a real
 * production `AndroidSqliteDriver` is more permissive, but this constraint doesn't weaken what's
 * under test here, since [TrackingEngine] only ever runs its own ingestion/prune loops on a
 * single coroutine anyway. What's being stress-tested is concurrent *producers* hammering
 * [TrackingEngine.logSensorEvent] (a non-suspending `Channel.trySend`, which is genuinely
 * thread-safe for concurrent senders) from real separate threads, not concurrent DB writers.
 */
@RunWith(RobolectricTestRunner::class)
class TrackingEngineStressTest {
    private val dbExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val dbDispatcher: CoroutineDispatcher = dbExecutor.asCoroutineDispatcher()
    private var engineScope: CoroutineScope? = null

    private fun newEngine(config: PulseKitConfig): TrackingEngine {
        val database = createPulseKitDatabase(RuntimeEnvironment.getApplication())
        val scope = CoroutineScope(SupervisorJob() + dbDispatcher)
        engineScope = scope
        return TrackingEngine(database, config, scope)
    }

    // Deterministic teardown: engine.stop() only *cancels* the ingestion/prune loops (correct for
    // production, where the app-owned scope outlives one stop() call). Without joining them here,
    // a still-finishing loop could touch the Robolectric SQLite connection after it's closed at
    // test teardown -- surfacing as an "Illegal connection pointer" in whichever test runs next.
    @After
    fun tearDown() {
        runBlocking { engineScope?.coroutineContext?.job?.cancelAndJoin() }
        dbExecutor.shutdown()
    }

    /** Claims every row, verifies no duplicate ids, then reverts to IDLE so the count is unaffected. */
    private suspend fun TrackingEngine.claimAndVerifyUnique(): List<String> =
        withContext(dbDispatcher) {
            val claimed = claimPendingBatch(Int.MAX_VALUE)
            val ids = claimed.map { it.id }
            assertEquals(ids.size, ids.toSet().size, "no event id should ever be persisted twice")
            markFailed(ids)
            ids
        }

    @Test
    fun manyConcurrentHighFrequencyProducersLoseNoEventsAndDoNotCrash() = runBlocking {
        val engine = newEngine(
            PulseKitConfig(
                ingestionBatchSize = 200,
                ingestionFlushIntervalMillis = 50L,
                // effectively unbounded: this test targets data loss, not pruning
                maxStoredEvents = 1_000_000,
            ),
        )
        engine.start()

        val sensorTypes = listOf("motion", "location", "bluetooth", "step_count")
        val producerCount = 8
        val eventsPerProducer = 1_000
        val expectedTotal = producerCount * eventsPerProducer
        val producerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // Simulates 8 independent sensor callback threads (2 of each type) all hammering the
        // engine concurrently, exactly like MotionDataSource/LocationDataSource/etc. would from
        // their own OS-driven callback threads on a real device.
        val producers = (0 until producerCount).map { producerIndex ->
            producerScope.async {
                val sensorType = sensorTypes[producerIndex % sensorTypes.size]
                repeat(eventsPerProducer) { i ->
                    engine.logSensorEvent(SensorPayload.StepCount(steps = i.toLong()), sensorType)
                }
            }
        }
        producers.awaitAll()

        // Ingestion is an async batched consumer; poll until the store catches up instead of
        // guessing a fixed delay is "long enough" (there's no synchronous flush-now API, by
        // design -- producers must never suspend/block).
        val ids = withTimeout(15_000) {
            var claimed: List<String>
            do {
                delay(50)
                claimed = engine.claimAndVerifyUnique()
            } while (claimed.size < expectedTotal)
            claimed
        }

        assertEquals(
            expectedTotal,
            ids.size,
            "every logged event must eventually be persisted -- none lost",
        )

        engine.stop()
        producerScope.cancel()
    }

    @Test
    fun loggingUnderHighVolumeNeverBlocksTheCallerEvenIfPersistenceLagsBehind() = runBlocking {
        // A deliberately slow flush cadence (long interval, tiny batch) so the ingestion loop
        // falls far behind the producer -- this is exactly the "DB slower than the sensor" edge
        // case flagged in REFACTOR_PLAN.md's Phase 7 backpressure note.
        val engine =
            newEngine(PulseKitConfig(ingestionBatchSize = 5, ingestionFlushIntervalMillis = 5_000L))
        engine.start()

        val burstSize = 20_000
        val elapsedMillis = measureTimeMillis {
            repeat(burstSize) { i ->
                engine.logSensorEvent(SensorPayload.MotionChunk(emptyList()), "motion")
            }
        }

        // logSensorEvent is a non-suspending trySend into an unlimited channel; even 20,000 calls
        // back-to-back, with persistence deliberately lagging far behind, must return in
        // milliseconds, not scale with how backed-up the consumer is. A regression that made this
        // suspend or block on the store would blow this budget by orders of magnitude.
        assertTrue(
            elapsedMillis < 2_000,
            "logging $burstSize events took ${elapsedMillis}ms -- producers must never be " +
                "slowed down by a lagging consumer",
        )

        engine.stop()
    }

    @Test
    fun pruneLoopKeepsStorageBoundedUnderSustainedConcurrentLoad() = runBlocking {
        val cap = 500
        val engine = newEngine(
            PulseKitConfig(
                ingestionBatchSize = 50,
                ingestionFlushIntervalMillis = 20L,
                maxStoredEvents = cap,
                minPruneBatchSize = 50,
                pruneCheckIntervalMillis = 30L,
            ),
        )
        engine.start()
        val producerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // Sustained load well past the cap, from multiple concurrent producers, while the prune
        // loop is running concurrently on the same engine -- this is the realistic "days of
        // continuous collection on a device that's offline" scenario the cap exists for.
        val producers = (0 until 4).map {
            producerScope.async {
                repeat(1_000) { i ->
                    engine.logSensorEvent(SensorPayload.StepCount(steps = i.toLong()), "step_count")
                }
            }
        }
        producers.awaitAll()

        // Give the ingestion + prune loops time to settle without a fixed guess: poll until the
        // count stabilizes at or under the cap, or fail if it never does.
        val finalCount = withTimeout(15_000) {
            var count = Int.MAX_VALUE
            var stableIterations = 0
            while (stableIterations < 3) {
                delay(100)
                val next = engine.claimAndVerifyUnique().size
                if (next == count) stableIterations++ else stableIterations = 0
                count = next
            }
            count
        }

        assertTrue(
            finalCount <= cap,
            "storage must stay bounded at the configured cap ($cap) under sustained load, " +
                "was $finalCount",
        )

        engine.stop()
        producerScope.cancel()
    }
}
