package io.github.alirezajavan.pulsekit.location

/** Tunables for [LocationDataSource]. */
data class LocationConfig(
    /** Minimum time between location updates, in milliseconds. */
    val minUpdateIntervalMillis: Long = 5_000L,
    /** Minimum distance between location updates, in meters. */
    val minUpdateDistanceMeters: Float = 10f,
)
