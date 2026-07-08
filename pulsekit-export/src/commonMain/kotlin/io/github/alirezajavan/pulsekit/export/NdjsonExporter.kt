package io.github.alirezajavan.pulsekit.export

import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.db.SensorEventLog
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object NdjsonExporter {
    @Serializable
    private data class ExportEvent(
        val id: String,
        val type: String,
        val timestamp: Long,
        val payload: SensorPayload,
    )

    suspend fun export(events: Flow<SensorEventLog>, output: Appendable) {
        events.collect { event ->
            val dto = ExportEvent(
                id = event.id,
                type = event.sensorType,
                timestamp = event.timestamp,
                payload = event.payload,
            )
            output.append(Json.encodeToString(dto))
            output.append("\n")
        }
    }
}
