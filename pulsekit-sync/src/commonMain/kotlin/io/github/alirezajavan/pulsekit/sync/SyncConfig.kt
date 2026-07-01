package io.github.alirezajavan.pulsekit.sync

/** Tunables for [SyncEngine]. */
data class SyncConfig(
    /** Max events uploaded per request. */
    val batchSize: Int = 500,
    /** How long to wait before checking again when there was nothing to sync. */
    val idlePollIntervalMillis: Long = 15_000L,
    /** Initial delay before retrying a failed batch. */
    val initialBackoffMillis: Long = 2_000L,
    /** Ceiling for exponential backoff, so prolonged outages don't grow the delay unbounded. */
    val maxBackoffMillis: Long = 5 * 60_000L,
)
