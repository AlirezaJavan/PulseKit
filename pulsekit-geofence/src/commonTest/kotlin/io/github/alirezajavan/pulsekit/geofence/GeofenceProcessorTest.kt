package io.github.alirezajavan.pulsekit.geofence

import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.db.SensorEventLog
import io.github.alirezajavan.pulsekit.core.SyncStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeofenceProcessorTest {
    private val home = GeofenceRegion("home", 37.7749, -122.4194, 100.0)

    @Test
    fun triggersEnterTransition() = runTest {
        val processor = GeofenceProcessor(listOf(home))
        val events = mutableListOf<GeofenceEvent>()
        backgroundScope.launch { processor.events.collect { events.add(it) } }
        runCurrent()

        // Start far away
        processor.process(createLocationEvent(34.0, -118.0))
        runCurrent()
        assertTrue(events.isEmpty())

        // Move inside
        processor.process(createLocationEvent(37.7749, -122.4194))
        runCurrent()

        assertEquals(1, events.size)
        assertEquals("home", events[0].regionId)
        assertEquals(GeofenceEvent.Transition.ENTER, events[0].transition)
        assertTrue(processor.currentlyInside.value.contains("home"))
    }

    @Test
    fun triggersExitTransitionWithHysteresis() = runTest {
        val processor = GeofenceProcessor(listOf(home), hysteresisMeters = 50.0)
        val events = mutableListOf<GeofenceEvent>()
        backgroundScope.launch { processor.events.collect { events.add(it) } }
        runCurrent()

        // Start inside
        processor.process(createLocationEvent(37.7749, -122.4194))
        runCurrent()
        assertEquals(1, events.size)
        assertEquals(GeofenceEvent.Transition.ENTER, events[0].transition)

        // Move just outside (110m away, radius is 100m)
        // 0.001 deg lat is ~111m
        processor.process(createLocationEvent(37.7749 + 0.001, -122.4194))
        runCurrent()
        assertEquals(1, events.size) // No EXIT yet due to hysteresis (111m < 100m + 50m)

        // Move further out (160m away)
        processor.process(createLocationEvent(37.7749 + 0.0015, -122.4194))
        runCurrent()
        assertEquals(2, events.size)
        assertEquals(GeofenceEvent.Transition.EXIT, events[1].transition)
        assertTrue(processor.currentlyInside.value.isEmpty())
    }

    @Test
    fun ignoresLowAccuracyLocations() = runTest {
        val processor = GeofenceProcessor(listOf(home), minAccuracyMeters = 10f)
        val events = mutableListOf<GeofenceEvent>()
        backgroundScope.launch { processor.events.collect { events.add(it) } }
        runCurrent()

        // Move inside with bad accuracy (100m accuracy, threshold 10m)
        processor.process(createLocationEvent(37.7749, -122.4194, accuracy = 100f))
        runCurrent()
        assertTrue(events.isEmpty())

        // Move inside with good accuracy
        processor.process(createLocationEvent(37.7749, -122.4194, accuracy = 5f))
        runCurrent()
        assertEquals(1, events.size)
    }

    private fun createLocationEvent(
        lat: Double,
        lng: Double,
        accuracy: Float = 5f,
        timestamp: Long = 1000L,
    ): SensorEventLog {
        return SensorEventLog(
            id = "id",
            sensorType = "location",
            timestamp = timestamp,
            payload = SensorPayload.Location(
                latitude = lat,
                longitude = lng,
                accuracy = accuracy,
                speed = 0f,
            ),
            syncStatus = SyncStatus.PENDING_UPLOAD,
        )
    }
}
