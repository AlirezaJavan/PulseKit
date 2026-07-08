package io.github.alirezajavan.pulsekit.export

import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.db.SensorEventLog
import kotlinx.coroutines.flow.Flow

object CsvExporter {
    suspend fun export(events: Flow<SensorEventLog>, output: Appendable) {
        output.append("timestamp,type,lat,lon,accuracy,speed,steps,x,y,z\n")

        events.collect { event ->
            val timestamp = event.timestamp
            val type = event.sensorType
            when (val payload = event.payload) {
                is SensorPayload.Location -> {
                    output.append("$timestamp,$type,${payload.latitude},${payload.longitude},")
                    output.append("${payload.accuracy},${payload.speed},,,,\n")
                }
                is SensorPayload.StepCount -> {
                    output.append("$timestamp,$type,,,,,${payload.steps},,,\n")
                }
                is SensorPayload.MotionChunk -> {
                    payload.samples.forEach { sample ->
                        output.append("${sample.timestamp},$type,,,,,,")
                        output.append("${sample.x},${sample.y},${sample.z}\n")
                    }
                }
                is SensorPayload.BluetoothScan -> {
                    // Skip or add a generic row if desired.
                }
            }
        }
    }
}
