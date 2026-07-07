package io.github.alirezajavan.pulsekit.core

/**
 * Platform-neutral snapshot of synchronization status, used to surface sync diagnostics in
 * UI (e.g. notifications) without requiring a dependency on the full `:pulsekit-sync` engine.
 */
interface SyncStatusSnapshot {
    /** True while a batch is currently being uploaded. */
    val isSyncing: Boolean

    /** Wall-clock time of the most recent successful sync, or `null` if none yet. */
    val lastSuccessTimestampMillis: Long?

    /** Message from the most recent failed sync attempt, if any. */
    val lastError: String?

    /** True if syncing is paused because network requirements (e.g. Wi-Fi) are not met. */
    val isWaitingForNetwork: Boolean
}
