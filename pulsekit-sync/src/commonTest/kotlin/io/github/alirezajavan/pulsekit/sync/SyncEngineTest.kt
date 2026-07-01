@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.alirezajavan.pulsekit.sync

import io.github.alirezajavan.pulsekit.core.PulseKitLogger
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.SyncSource
import io.github.alirezajavan.pulsekit.core.SyncStatus
import io.github.alirezajavan.pulsekit.core.db.SensorEventLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class SyncEngineTest {
    @Test
    fun retriesOnFailureThenUploadsAndDeletesOnSuccess() = runTest {
        val event = testEvent("1")
        val source = FakeSyncSource(listOf(event))
        val uploader = ScriptedUploader(failuresBeforeSuccess = 2)
        val engine = SyncEngine(
            source,
            uploader,
            SyncConfig(
                batchSize = 10,
                idlePollIntervalMillis = 1_000L,
                initialBackoffMillis = 100L,
                maxBackoffMillis = 100_000L,
            ),
        )

        engine.start(this)
        // attempt 1 @ t=0 (fails), attempt 2 @ t=100 (fails, backoff doubles to 200),
        // attempt 3 @ t=300 (succeeds).
        advanceTimeBy(310.milliseconds)
        engine.stop()

        assertEquals(3, uploader.attempts)
        assertTrue(
            source.pending.isEmpty(),
            "batch should have been removed from the source on success",
        )
        assertEquals(listOf(event.id), source.deletedIds)
    }

    @Test
    fun emptyQueuePollsAtIdleIntervalWithoutCallingUploader() = runTest {
        val source = FakeSyncSource(emptyList())
        val uploader = ScriptedUploader(failuresBeforeSuccess = 0)
        val engine = SyncEngine(
            source,
            uploader,
            SyncConfig(idlePollIntervalMillis = 500L),
        )

        engine.start(this)
        advanceTimeBy(1_600.milliseconds) // ~3 idle-poll cycles
        engine.stop()

        assertEquals(0, uploader.attempts, "uploader must never be called for an empty batch")
        assertTrue(source.claimTimestamps.size >= 3)
        // Each poll should be spaced by the configured idle interval, not busy-looped.
        source.claimTimestamps.zipWithNext().forEach { (a, b) -> assertEquals(500L, b - a) }
    }

    @Test
    fun backoffGrowsExponentiallyThenCapsAtMaxBackoff() = runTest {
        val source = FakeSyncSource(listOf(testEvent("1")))
        val uploader = AlwaysFailingUploader()
        val engine = SyncEngine(
            source,
            uploader,
            SyncConfig(initialBackoffMillis = 100L, maxBackoffMillis = 250L),
        )

        engine.start(this)
        // Failures land at t = 0, 100, 300, 550, 800 (deltas: 100, 200, 250, 250 -- capped
        // from the third failure onward instead of continuing to double unboundedly).
        advanceTimeBy(900.milliseconds)
        engine.stop()

        val deltas = source.failedAtTimestamps.zipWithNext { a, b -> b - a }
        assertEquals(listOf(100L, 200L, 250L, 250L), deltas.take(4))
    }

    @Test
    fun observeStateReflectsSuccessThenFailureTransitions() = runTest {
        val source = FakeSyncSource(listOf(testEvent("1"), testEvent("2")))
        val uploader = ScriptedUploader(failuresBeforeSuccess = 0)
        val engine = SyncEngine(source, uploader, SyncConfig(idlePollIntervalMillis = 1_000L))

        engine.start(this)
        advanceTimeBy(10.milliseconds)

        val afterSuccess = engine.observeState().value
        assertNotNull(
            afterSuccess.lastSuccessTimestampMillis,
            "a successful upload should stamp lastSuccessTimestampMillis",
        )
        assertNull(afterSuccess.lastError)
        assertEquals(0, afterSuccess.consecutiveFailures)

        // Second batch always fails from here on.
        source.pending += testEvent("3")
        uploader.alwaysFail = true
        advanceTimeBy(1_500.milliseconds)
        engine.stop()

        val afterFailure = engine.observeState().value
        assertNotNull(afterFailure.lastError, "a failed upload should record a non-null lastError")
        assertTrue(afterFailure.consecutiveFailures >= 1)
    }

    @Test
    fun loggerIsWarnedOnEveryFailedUploadAttempt() = runTest {
        val source = FakeSyncSource(listOf(testEvent("1")))
        val uploader = AlwaysFailingUploader()
        val logger = RecordingLogger()
        val engine = SyncEngine(
            source,
            uploader,
            SyncConfig(initialBackoffMillis = 50L, maxBackoffMillis = 50L),
            logger,
        )

        engine.start(this)
        advanceTimeBy(120.milliseconds) // attempts at t=0, 50, 100
        engine.stop()

        assertTrue(
            logger.warnings.isNotEmpty(),
            "every failed upload should be reported through the logger",
        )
        assertTrue(logger.warnings.all { it.tag == "SyncEngine" })
    }
}

private fun testEvent(id: String) = SensorEventLog(
    id = id,
    sensorType = "test",
    timestamp = 0L,
    payload = SensorPayload.StepCount(steps = 1L),
    syncStatus = SyncStatus.IDLE,
)

/**
 * In-memory [SyncSource] fake: no real database, just enough claim/mark bookkeeping to exercise
 * [SyncEngine]'s state machine deterministically under [kotlinx.coroutines.test]'s virtual time.
 */
private class FakeSyncSource(initial: List<SensorEventLog>) : SyncSource {
    val pending = initial.toMutableList()
    private val claimed = mutableListOf<SensorEventLog>()
    val deletedIds = mutableListOf<String>()
    val failedAtTimestamps = mutableListOf<Long>()
    val claimTimestamps = mutableListOf<Long>()

    override suspend fun claimPendingBatch(limit: Int): List<SensorEventLog> {
        claimTimestamps += currentVirtualTime()
        val batch = pending.take(limit)
        pending.removeAll(batch)
        claimed += batch
        return batch
    }

    override suspend fun markUploaded(ids: List<String>) {
        deletedIds += ids
        claimed.removeAll { it.id in ids }
    }

    override suspend fun markFailed(ids: List<String>) {
        failedAtTimestamps += currentVirtualTime()
        val reverted = claimed.filter { it.id in ids }
        claimed.removeAll { it.id in ids }
        pending += reverted
    }
}

/** ScriptedUploader fails a fixed number of times, then succeeds on every call after that. */
private class ScriptedUploader(private val failuresBeforeSuccess: Int) : SyncUploader {
    var attempts = 0
        private set

    /** Once set, every subsequent call fails regardless of [failuresBeforeSuccess]. */
    var alwaysFail = false

    override suspend fun upload(batch: List<SensorEventLog>): Boolean {
        attempts++
        if (alwaysFail) return false
        return attempts > failuresBeforeSuccess
    }
}

private class AlwaysFailingUploader : SyncUploader {
    var attempts = 0
        private set

    override suspend fun upload(batch: List<SensorEventLog>): Boolean {
        attempts++
        return false
    }
}

private class RecordingLogger : PulseKitLogger {
    data class Entry(val tag: String, val message: String)

    val warnings = mutableListOf<Entry>()

    override fun debug(tag: String, message: String) = Unit

    override fun warn(tag: String, message: String, throwable: Throwable?) {
        warnings += Entry(tag, message)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}

/** Reads the enclosing [kotlinx.coroutines.test.TestScope]'s virtual clock from within a suspend fn. */
private suspend fun currentVirtualTime(): Long =
    kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.test.TestCoroutineScheduler]
        ?.currentTime ?: 0L
