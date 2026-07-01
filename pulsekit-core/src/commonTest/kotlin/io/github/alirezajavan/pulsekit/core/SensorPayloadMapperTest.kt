package io.github.alirezajavan.pulsekit.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SensorPayloadMapperTest {
    @Test
    fun locationRoundTrips() {
        val payload = SensorPayload.Location(
            latitude = 37.7749,
            longitude = -122.4194,
            accuracy = 5.5f,
            speed = 1.2f,
        )
        assertRoundTrips(payload)
    }

    @Test
    fun motionChunkRoundTrips() {
        val payload = SensorPayload.MotionChunk(
            samples = listOf(
                MotionSample(timestamp = 1_000L, x = 0.1f, y = -0.2f, z = 9.8f),
                MotionSample(timestamp = 1_010L, x = 0.2f, y = -0.1f, z = 9.7f),
            ),
        )
        assertRoundTrips(payload)
    }

    @Test
    fun motionChunkWithNoSamplesRoundTrips() {
        assertRoundTrips(SensorPayload.MotionChunk(samples = emptyList()))
    }

    @Test
    fun bluetoothScanRoundTrips() {
        assertRoundTrips(
            SensorPayload.BluetoothScan(
                address = "AA:BB:CC:DD:EE:FF",
                name = "Sensor Tag",
                rssi = -62,
            ),
        )
    }

    @Test
    fun bluetoothScanWithNullNameRoundTrips() {
        assertRoundTrips(
            SensorPayload.BluetoothScan(address = "AA:BB:CC:DD:EE:FF", name = null, rssi = -80),
        )
    }

    @Test
    fun stepCountRoundTrips() {
        assertRoundTrips(SensorPayload.StepCount(steps = 12_345L))
    }

    @Test
    fun jsonEncodesDiscriminatorForPolymorphicDecoding() {
        val json = SensorPayloadMapper.toJson(SensorPayload.StepCount(steps = 1L))
        // The sealed interface relies on this discriminator to pick the right subtype back out
        // on fromJson -- if the @SerialName annotations on the subtypes ever get renamed without
        // updating this, decoding silently breaks for any already-persisted or in-flight rows.
        assertEquals(true, json.contains("\"type\":\"step_count\""))
    }

    private fun assertRoundTrips(payload: SensorPayload) {
        val json = SensorPayloadMapper.toJson(payload)
        val decoded = SensorPayloadMapper.fromJson(json)
        assertEquals(payload, decoded)
    }
}
