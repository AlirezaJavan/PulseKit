package io.github.alirezajavan.pulsekit.sync

import io.github.alirezajavan.pulsekit.core.SyncStatusSnapshot

/**
 * Observable snapshot of [SyncEngine]'s current sync status, so a client app can build its own
 * "last synced 2m ago" / "sync failing" UI instead of guessing at internal retry/backoff state.
 */
data class SyncState(
    /** True while a claimed batch is being uploaded (i.e. between claim and success/failure). */
    override val isSyncing: Boolean = false,
    /** Wall-clock time of the most recent successful batch upload, or `null` if none has occurred yet. */
    override val lastSuccessTimestampMillis: Long? = null,
    /** Message from the most recent failed upload attempt, or `null` if the last attempt succeeded. */
    override val lastError: String? = null,
    /** Consecutive failed attempts since the last success; reset to 0 on every success. */
    val consecutiveFailures: Int = 0,
    /** True if syncing is paused because unmetered network is required but not available. */
    override val isWaitingForNetwork: Boolean = false,
) : SyncStatusSnapshot
