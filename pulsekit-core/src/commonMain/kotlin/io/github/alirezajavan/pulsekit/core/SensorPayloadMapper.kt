package io.github.alirezajavan.pulsekit.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

object SensorPayloadMapper {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun toJson(payload: SensorPayload): String {
        return json.encodeToString(payload)
    }

    fun fromJson(jsonString: String): SensorPayload {
        return json.decodeFromString(jsonString)
    }
}
