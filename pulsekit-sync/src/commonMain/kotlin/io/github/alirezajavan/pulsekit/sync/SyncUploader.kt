package io.github.alirezajavan.pulsekit.sync

import io.github.alirezajavan.pulsekit.core.db.SensorEventLog

/**
 * Client-app-owned upload strategy. SyncEngine handles claiming, retry/backoff and marking
 * batches uploaded/failed; this interface is the only thing that knows what the destination
 * backend's wire contract actually looks like.
 */
interface SyncUploader {
    suspend fun upload(batch: List<SensorEventLog>): Boolean
}
