package io.github.alirezajavan.pulsekit.sync

import io.github.alirezajavan.pulsekit.core.SensorPayloadMapper
import io.github.alirezajavan.pulsekit.core.db.SensorEventLog
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Default [SyncUploader] for client apps happy to accept PulseKit's own JSON wire shape:
 * a plain POST of `List<SyncEventDto>` to [endpointUrl]. Apps whose backend expects a different
 * contract should implement [SyncUploader] directly instead of using this class.
 */
class JsonHttpSyncUploader(
    private val httpClient: HttpClient,
    private val endpointUrl: String,
) : SyncUploader {
    override suspend fun upload(batch: List<SensorEventLog>): Boolean {
        val dtos = batch.map {
            SyncEventDto(
                id = it.id,
                sensorType = it.sensorType,
                timestamp = it.timestamp,
                payload = SensorPayloadMapper.toJson(it.payload),
            )
        }
        val body = json.encodeToString(ListSerializer(SyncEventDto.serializer()), dtos)
        val response = httpClient.post(endpointUrl) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return response.status.isSuccess()
    }

    private companion object {
        val json = Json { encodeDefaults = true }
    }
}
