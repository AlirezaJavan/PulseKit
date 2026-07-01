package io.github.alirezajavan.pulsekit.core

/**
 * Tunables for the ingestion, retention and sync behaviour of [TrackingEngine].
 *
 * Defaults are chosen for high-frequency sensor streams (tens to hundreds of samples/sec):
 * writes are batched rather than persisted one row at a time, and storage is capped so a
 * device left offline for a long stretch cannot grow the database unbounded.
 */
data class PulseKitConfig(
    /** Max number of buffered events flushed to disk in a single DB transaction. */
    val ingestionBatchSize: Int = 200,
    /** Upper bound on how long an event can sit buffered before being flushed, even if the batch isn't full. */
    val ingestionFlushIntervalMillis: Long = 1_000L,
    /** How often the engine checks total row count against [maxStoredEvents]. */
    val pruneCheckIntervalMillis: Long = 30_000L,
    /** Hard cap on stored events; oldest rows are pruned once exceeded (disk quota defense). */
    val maxStoredEvents: Int = 50_000,
    /** Minimum number of rows removed per prune pass, to avoid pruning one row at a time near the cap. */
    val minPruneBatchSize: Int = 500,
    /**
     * Age-based retention, alongside [maxStoredEvents]'s count-based cap: rows older than this
     * are pruned on the same cadence as the count check, regardless of how many total rows exist.
     * `null` (the default) disables age-based pruning entirely -- not every integration wants a
     * max-age policy imposed on it by default, so count-based capping alone remains the baseline.
     */
    val maxEventAgeMillis: Long? = null,
)
