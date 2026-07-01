package io.github.alirezajavan.pulsekit.core

/**
 * How [PulseKit] schedules a [DataSource]'s collection, chosen per source via
 * [PulseKit.Builder.addDataSource].
 */
sealed interface CollectionMode {
    /** Collect continuously from `start` until `stop` (the default). */
    data object Continuous : CollectionMode

    /**
     * Collect in bursts: every [intervalMillis] (measured start-to-start), the source is started,
     * collected from for [windowMillis], then stopped until the next cycle. Suits sources where
     * continuous sampling is wasteful — e.g. a BLE scan for 30 seconds every 5 minutes instead of
     * scanning nonstop.
     *
     * If the source fails to start for a cycle (permission still denied, adapter off), the cycle
     * is skipped and retried at the next interval — a periodic source self-heals once the
     * precondition is met.
     */
    data class Periodic(
        val intervalMillis: Long,
        val windowMillis: Long,
    ) : CollectionMode {
        init {
            require(windowMillis > 0) { "windowMillis must be positive, was $windowMillis" }
            require(intervalMillis > windowMillis) {
                "intervalMillis ($intervalMillis) must exceed windowMillis ($windowMillis); " +
                    "for gapless collection use CollectionMode.Continuous instead"
            }
        }
    }
}
