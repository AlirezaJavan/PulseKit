package io.github.alirezajavan.pulsekit.geofence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HaversineTest {
    @Test
    fun calculatedKnownDistance() {
        // San Francisco to Los Angeles ~ 559 km
        val distance = haversineMeters(37.7749, -122.4194, 34.0522, -118.2437)
        assertTrue(distance > 558_000 && distance < 560_000)
    }

    @Test
    fun zeroDistance() {
        val distance = haversineMeters(37.7749, -122.4194, 37.7749, -122.4194)
        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun antimeridianCrossing() {
        // Points on opposite sides of 180 longitude
        val distance = haversineMeters(0.0, 179.9, 0.0, -179.9)
        // 0.2 degrees at equator is ~ 22.2 km
        assertTrue(distance > 22_000 && distance < 23_000)
    }
}
