package io.github.alirezajavan.pulsekit.core

import io.github.alirezajavan.pulsekit.core.db.SensorEventLog

/**
 * Narrow contract consumed by `pulsekit-sync`, so the sync module never needs to depend on
 * [TrackingEngine] internals or the underlying database directly.
 */
interface SyncSource {
    /**
     * Atomically claims up to [limit] not-yet-uploaded rows by moving them to
     * [SyncStatus.PENDING_UPLOAD]. Acts as an optimistic lock preventing two concurrent
     * sync passes from uploading the same rows twice.
     */
    suspend fun claimPendingBatch(limit: Int): List<SensorEventLog>

    /** Marks rows as durably delivered; implementations delete them to reclaim storage. */
    suspend fun markUploaded(ids: List<String>)

    /** Reverts a failed batch back to [SyncStatus.IDLE] so it is retried on a later pass. */
    suspend fun markFailed(ids: List<String>)
}
