package io.github.alirezajavan.pulsekit.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface SensorPayload {
    @Serializable
    @SerialName("location")
    data class Location(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val speed: Float,
    ) : SensorPayload

    @Serializable
    @SerialName("motion_chunk")
    data class MotionChunk(
        val samples: List<MotionSample>,
    ) : SensorPayload

    @Serializable
    @SerialName("bluetooth_scan")
    data class BluetoothScan(
        val address: String,
        val name: String?,
        val rssi: Int,
    ) : SensorPayload

    /**
     * Step count reading. The two platforms report fundamentally different quantities, which
     * this type deliberately doesn't normalize (normalizing would require guessing a reset
     * policy the client app might not want): Android's `TYPE_STEP_COUNTER` reports a cumulative
     * total since the device's last reboot, while iOS's `CMPedometer` reports a delta since
     * pedometer updates were started. Consumers must handle each `sensorType`/platform
     * accordingly rather than assuming a shared meaning for [steps].
     */
    @Serializable
    @SerialName("step_count")
    data class StepCount(
        val steps: Long,
    ) : SensorPayload
}

@Serializable
data class MotionSample(
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val z: Float,
)
