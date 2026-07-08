package io.github.alirezajavan.pulsekit.core

/**
 * Descriptor for querying stored events.
 */
data class EventQuery(
    val types: Set<String>? = null,
    val fromTimestamp: Long = 0L,
    val toTimestamp: Long = Long.MAX_VALUE,
    val limit: Long = 100L,
)
