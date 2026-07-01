package io.github.alirezajavan.pulsekit.core.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import io.github.alirezajavan.pulsekit.core.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/** Thin, testable wrapper around the generated SQLDelight queries for [SensorEventLog] rows. */
internal class SensorEventStore(private val database: PulseKitDatabase) {
    private val queries get() = database.sensorEventLogQueries

    /** Reactive row count, not the rows themselves — safe to observe even with hundreds of thousands of rows. */
    fun observeEventCount(): Flow<Long> =
        queries.eventCount().asFlow().mapToOne(Dispatchers.Default)

    fun getEventCount(): Long = queries.eventCount().executeAsOne()

    suspend fun insertEvents(events: List<SensorEventLog>) {
        if (events.isEmpty()) return
        queries.transaction {
            events.forEach { event ->
                queries.insertEvent(
                    event.id,
                    event.sensorType,
                    event.timestamp,
                    event.payload,
                    event.syncStatus,
                )
            }
        }
    }

    /** Atomically selects up to [limit] [SyncStatus.IDLE] rows and flips them to [SyncStatus.PENDING_UPLOAD]. */
    suspend fun claimPendingBatch(limit: Long): List<SensorEventLog> {
        var batch: List<SensorEventLog> = emptyList()
        queries.transaction {
            batch = queries.eventsByStatus(SyncStatus.IDLE, limit).executeAsList()
            if (batch.isNotEmpty()) {
                updateSyncStatusChunked(SyncStatus.PENDING_UPLOAD, batch.map { it.id })
            }
        }
        return batch
    }

    suspend fun updateSyncStatus(ids: List<String>, status: SyncStatus) {
        if (ids.isEmpty()) return
        queries.transaction { updateSyncStatusChunked(status, ids) }
    }

    suspend fun deleteEvents(ids: List<String>) {
        if (ids.isEmpty()) return
        queries.transaction {
            ids.chunked(SQL_IN_CLAUSE_CHUNK_SIZE).forEach { chunk -> queries.deleteEvents(chunk) }
        }
    }

    suspend fun pruneOldEvents(limit: Long) {
        queries.pruneOldEvents(limit)
    }

    /** Removes every row older than [cutoffTimestamp] (age-based retention, alongside the count-based cap). */
    suspend fun pruneEventsOlderThan(cutoffTimestamp: Long) {
        queries.pruneEventsOlderThan(cutoffTimestamp)
    }

    /** Unconditionally deletes every stored row -- for a right-to-erasure request, not routine pruning. */
    suspend fun deleteAllEvents() {
        queries.deleteAllEvents()
    }

    // SQLite caps how many bound parameters a single statement may have
    // (SQLITE_MAX_VARIABLE_NUMBER -- 999 on older SQLite builds still found on some Android
    // devices, higher on newer ones). A `WHERE id IN (...)` built from an oversized id list --
    // e.g. a caller-configured SyncConfig.batchSize larger than that, or claiming/reverting a
    // large backlog after being offline for a while -- would otherwise throw a runtime
    // SQLiteException instead of just running as multiple statements.
    private fun updateSyncStatusChunked(status: SyncStatus, ids: List<String>) {
        ids.chunked(SQL_IN_CLAUSE_CHUNK_SIZE).forEach { chunk ->
            queries.updateSyncStatus(status, chunk)
        }
    }

    private companion object {
        const val SQL_IN_CLAUSE_CHUNK_SIZE = 900
    }
}
