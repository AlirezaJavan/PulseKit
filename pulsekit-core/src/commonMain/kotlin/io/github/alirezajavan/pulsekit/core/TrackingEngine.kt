package io.github.alirezajavan.pulsekit.core

import io.github.alirezajavan.pulsekit.core.db.PulseKitDatabase
import io.github.alirezajavan.pulsekit.core.db.SensorEventLog
import io.github.alirezajavan.pulsekit.core.db.SensorEventStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

import kotlin.time.Duration.Companion.milliseconds

/**
 * Engine responsible for turning a stream of [SensorPayload] events into durable, bounded
 * storage. All persistence happens off a buffered channel so bursts of high-frequency sensor
 * data (e.g. accelerometer at 50-200Hz) are coalesced into batched transactions rather than one
 * SQLite write per sample.
 */
internal class TrackingEngine(
    database: PulseKitDatabase,
    private val config: PulseKitConfig = PulseKitConfig(),
    private val scope: CoroutineScope,
    private val logger: PulseKitLogger = NoOpPulseKitLogger,
) : SyncSource {
    private val store = SensorEventStore(database)

    // Unlimited capacity: producers (DataSources) must never suspend/backpressure on a sensor
    // callback thread; the engine is the one place batching and backpressure are handled.
    private val ingestionChannel = Channel<SensorEventLog>(capacity = Channel.UNLIMITED)

    private var ingestionJob: Job? = null
    private var pruneJob: Job? = null

    fun start() {
        if (ingestionJob?.isActive == true) return
        ingestionJob = scope.launch { runIngestionLoop() }
        pruneJob = scope.launch { runPruneLoop() }
    }

    fun stop() {
        ingestionJob?.cancel()
        pruneJob?.cancel()
        ingestionJob = null
        pruneJob = null
    }

    /** Reactive count of all stored events, safe to render in UI without loading rows. */
    fun observeEventCount(): Flow<Long> = store.observeEventCount()

    /** Enqueues an event for batched persistence. Never suspends; safe to call from sensor callbacks. */
    fun logSensorEvent(payload: SensorPayload, sensorType: String) {
        val event = SensorEventLog(
            id = platformGenerateUuid(),
            sensorType = sensorType,
            timestamp = platformCurrentTimeMillis(),
            payload = payload,
            syncStatus = SyncStatus.IDLE,
        )
        ingestionChannel.trySend(event)
    }

    /** Directly persists a pre-built batch, bypassing the streaming buffer (e.g. historic pulls). */
    suspend fun bulkInsertEvents(events: List<SensorEventLog>) {
        store.insertEvents(events)
    }

    /** Unconditionally erases every stored event -- for a right-to-erasure request, not routine pruning. */
    suspend fun eraseAllData() {
        store.deleteAllEvents()
    }

    override suspend fun claimPendingBatch(limit: Int): List<SensorEventLog> {
        return store.claimPendingBatch(limit.toLong())
    }

    override suspend fun markUploaded(ids: List<String>) {
        store.deleteEvents(ids)
    }

    override suspend fun markFailed(ids: List<String>) {
        store.updateSyncStatus(ids, SyncStatus.IDLE)
    }

    /**
     * Drains the ingestion channel into size- and time-bounded batches: waits for at least one
     * event, then keeps collecting until either [PulseKitConfig.ingestionBatchSize] is reached or
     * [PulseKitConfig.ingestionFlushIntervalMillis] elapses, whichever comes first.
     */
    private suspend fun runIngestionLoop() {
        while (scope.isActive) {
            val first = ingestionChannel.receiveCatching().getOrNull() ?: return
            val batch = ArrayList<SensorEventLog>(config.ingestionBatchSize)
            batch.add(first)
            withTimeoutOrNull(config.ingestionFlushIntervalMillis.milliseconds) {
                while (batch.size < config.ingestionBatchSize) {
                    batch.add(ingestionChannel.receive())
                }
            }
            store.insertEvents(batch)
        }
    }

    /**
     * Disk-quota defense: periodically prunes the oldest rows once the row cap is exceeded, and
     * (if [PulseKitConfig.maxEventAgeMillis] is configured) removes rows past that age regardless
     * of the current row count.
     */
    private suspend fun runPruneLoop() {
        while (scope.isActive) {
            delay(config.pruneCheckIntervalMillis.milliseconds)
            val count = store.getEventCount()
            val overflow = count - config.maxStoredEvents
            if (overflow > 0) {
                val pruned = maxOf(overflow, config.minPruneBatchSize.toLong())
                store.pruneOldEvents(pruned)
                logger.debug(
                    TAG,
                    "Pruned $pruned events past maxStoredEvents (${config.maxStoredEvents})",
                )
            }
            val maxAge = config.maxEventAgeMillis
            if (maxAge != null) {
                store.pruneEventsOlderThan(platformCurrentTimeMillis() - maxAge)
            }
        }
    }

    private companion object {
        const val TAG = "TrackingEngine"
    }
}

/** Expected platform-specific UUID generator. */
internal expect fun platformGenerateUuid(): String

/** Expected platform-specific time provider. */
expect fun platformCurrentTimeMillis(): Long
