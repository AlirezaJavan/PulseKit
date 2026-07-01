package io.github.alirezajavan.pulsekit.core.db

import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises [SensorEventStore] against a real (Robolectric-backed) [android.database.sqlite]
 * database rather than a fake -- claim/mark/prune here are exactly the kind of SQL behavior
 * (atomicity, ordering, WHERE-clause correctness) that a hand-written in-memory fake would get
 * wrong in a different way than the real driver, silently hiding regressions.
 */
@RunWith(RobolectricTestRunner::class)
class SensorEventStoreTest {
    private fun newStore(): SensorEventStore =
        SensorEventStore(createPulseKitDatabase(RuntimeEnvironment.getApplication()))

    private fun event(id: String, timestamp: Long = 0L, syncStatus: SyncStatus = SyncStatus.IDLE) =
        SensorEventLog(
            id = id,
            sensorType = "motion",
            timestamp = timestamp,
            payload = SensorPayload.StepCount(steps = timestamp),
            syncStatus = syncStatus,
        )

    @Test
    fun insertEventsPersistsAllRowsInOneTransaction() = runTest {
        val store = newStore()
        val batch = (1..25).map { event(id = "evt-$it", timestamp = it.toLong()) }

        store.insertEvents(batch)

        assertEquals(25L, store.getEventCount())
    }

    @Test
    fun insertEventsWithEmptyListIsANoOp() = runTest {
        val store = newStore()

        store.insertEvents(emptyList())

        assertEquals(0L, store.getEventCount())
    }

    @Test
    fun claimPendingBatchOnlyReturnsIdleRowsAndFlipsThemToPendingUpload() = runTest {
        val store = newStore()
        store.insertEvents(
            listOf(
                event("idle-1", syncStatus = SyncStatus.IDLE),
                event("idle-2", syncStatus = SyncStatus.IDLE),
                event("already-pending", syncStatus = SyncStatus.PENDING_UPLOAD),
            ),
        )

        val claimed = store.claimPendingBatch(limit = 10L)

        assertEquals(setOf("idle-1", "idle-2"), claimed.map { it.id }.toSet())
        // Claiming again immediately must return nothing -- the rows are no longer IDLE, which is
        // exactly the optimistic-lock behavior SyncEngine's "never upload the same batch twice
        // concurrently" guarantee depends on.
        assertTrue(store.claimPendingBatch(limit = 10L).isEmpty())
    }

    @Test
    fun claimPendingBatchRespectsLimitAndOrdersByOldestFirst() = runTest {
        val store = newStore()
        store.insertEvents((1..5).map { event(id = "evt-$it", timestamp = (10 - it).toLong()) })
        // timestamps inserted: evt-1=9, evt-2=8, evt-3=7, evt-4=6, evt-5=5 -- oldest is evt-5.

        val claimed = store.claimPendingBatch(limit = 2L)

        assertEquals(listOf("evt-5", "evt-4"), claimed.map { it.id })
    }

    @Test
    fun markUploadedDeletesRowsAndMarkFailedRevertsThemToIdle() = runTest {
        val store = newStore()
        store.insertEvents(listOf(event("a"), event("b")))
        store.claimPendingBatch(limit = 10L)

        store.updateSyncStatus(listOf("a"), SyncStatus.PENDING_UPLOAD)
        store.deleteEvents(listOf("a"))
        store.updateSyncStatus(listOf("b"), SyncStatus.IDLE)

        assertEquals(1L, store.getEventCount())
        val requeued = store.claimPendingBatch(limit = 10L)
        assertEquals(listOf("b"), requeued.map { it.id })
    }

    @Test
    fun pruneOldEventsRemovesOnlyTheOldestRowsUpToLimit() = runTest {
        val store = newStore()
        store.insertEvents((1..10).map { event(id = "evt-$it", timestamp = it.toLong()) })

        store.pruneOldEvents(limit = 4L)

        assertEquals(6L, store.getEventCount())
        // The 4 oldest (lowest timestamp) rows should be gone; the newest 6 should remain.
        val remaining = store.claimPendingBatch(limit = 100L).map { it.id }.toSet()
        assertEquals((5..10).map { "evt-$it" }.toSet(), remaining)
    }

    @Test
    fun observeEventCountReflectsInsertsAndDeletesReactively() = runTest {
        val store = newStore()
        assertEquals(0L, store.observeEventCount().first())

        store.insertEvents(listOf(event("a"), event("b")))
        assertEquals(2L, store.observeEventCount().first())

        store.deleteEvents(listOf("a"))
        assertEquals(1L, store.observeEventCount().first())
    }

    @Test
    fun pruneEventsOlderThanRemovesOnlyRowsPastTheCutoffRegardlessOfCount() = runTest {
        val store = newStore()
        store.insertEvents((1..10).map { event(id = "evt-$it", timestamp = it.toLong()) })

        // Cutoff of 5: rows with timestamp < 5 (evt-1..evt-4) are stale, the rest are still fresh.
        store.pruneEventsOlderThan(cutoffTimestamp = 5L)

        assertEquals(6L, store.getEventCount())
        val remaining = store.claimPendingBatch(limit = 100L).map { it.id }.toSet()
        assertEquals((5..10).map { "evt-$it" }.toSet(), remaining)
    }

    @Test
    fun deleteAllEventsErasesEverythingRegardlessOfSyncStatus() = runTest {
        val store = newStore()
        store.insertEvents(
            listOf(
                event("idle", syncStatus = SyncStatus.IDLE),
                event("pending", syncStatus = SyncStatus.PENDING_UPLOAD),
            ),
        )

        store.deleteAllEvents()

        assertEquals(0L, store.getEventCount())
    }
}
