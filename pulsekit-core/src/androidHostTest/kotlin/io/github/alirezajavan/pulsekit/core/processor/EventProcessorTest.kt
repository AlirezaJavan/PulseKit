package io.github.alirezajavan.pulsekit.core.processor

import io.github.alirezajavan.pulsekit.core.PulseKitConfig
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.TrackingEngine
import io.github.alirezajavan.pulsekit.core.db.createPulseKitDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EventProcessorTest {
    private fun newEngine(
        processors: List<EventProcessor>,
        scope: CoroutineScope,
    ) = TrackingEngine(
        database = createPulseKitDatabase(RuntimeEnvironment.getApplication()),
        config = PulseKitConfig(ingestionBatchSize = 1),
        scope = scope,
        processors = processors,
    )

    @Test
    fun processorsRunInRegistrationOrder() = runTest {
        val order = mutableListOf<String>()
        val p1 = EventProcessor { event ->
            order.add("p1")
            event
        }
        val p2 = EventProcessor { event ->
            order.add("p2")
            event
        }

        val engine = newEngine(listOf(p1, p2), this)
        engine.start()

        engine.logSensorEvent(SensorPayload.StepCount(1L), "step_count")
        runCurrent()

        assertEquals(listOf("p1", "p2"), order)
        engine.stop()
    }

    @Test
    fun processorCanDropEventsByReturningNull() = runTest {
        val p = EventProcessor { null }
        val engine = newEngine(listOf(p), this)
        engine.start()

        engine.logSensorEvent(SensorPayload.StepCount(1L), "step_count")
        runCurrent()

        val claimed = engine.claimPendingBatch(10)
        assertEquals(0, claimed.size, "Event should have been dropped")
        engine.stop()
    }

    @Test
    fun failingProcessorFailsOpenAndPassesEventThroughUnmodified() = runTest {
        val p = EventProcessor { throw RuntimeException("Boom") }
        val engine = newEngine(listOf(p), this)
        engine.start()

        engine.logSensorEvent(SensorPayload.StepCount(42L), "step_count")
        runCurrent()

        val claimed = engine.claimPendingBatch(10)
        assertEquals(1, claimed.size)
        val payload = claimed.first().payload
        assertIs<SensorPayload.StepCount>(payload)
        assertEquals(42L, payload.steps)
        engine.stop()
    }

    @Test
    fun samplingProcessorCorrectlyDropsEvents() = runTest {
        val p = SamplingProcessor(sensorType = "chatty", keepEveryNth = 3)
        val engine = newEngine(listOf(p), this)
        engine.start()

        engine.logSensorEvent(SensorPayload.StepCount(1L), "chatty") // 1 (drop)
        engine.logSensorEvent(SensorPayload.StepCount(2L), "chatty") // 2 (drop)
        engine.logSensorEvent(SensorPayload.StepCount(3L), "chatty") // 3 (keep)
        engine.logSensorEvent(SensorPayload.StepCount(4L), "other") // (keep)
        runCurrent()

        val claimed = engine.claimPendingBatch(10)
        assertEquals(2, claimed.size)
        assertEquals(3L, (claimed[0].payload as SensorPayload.StepCount).steps)
        assertEquals(4L, (claimed[1].payload as SensorPayload.StepCount).steps)
        engine.stop()
    }

    @Test
    fun locationPrecisionProcessorCorrectlyRoundsCoordinates() = runTest {
        val p = LocationPrecisionProcessor(decimalPlaces = 2)
        val engine = newEngine(listOf(p), this)
        engine.start()

        engine.logSensorEvent(
            SensorPayload.Location(12.34567, 98.76543, 5f, 0f),
            "location",
        )
        runCurrent()

        val claimed = engine.claimPendingBatch(10)
        val location = claimed.first().payload as SensorPayload.Location
        assertEquals(12.35, location.latitude)
        assertEquals(98.77, location.longitude)
        engine.stop()
    }
}
