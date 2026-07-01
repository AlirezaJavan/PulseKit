package io.github.alirezajavan.pulsekit.sync

import kotlinx.serialization.Serializable

/**
 * Wire format for a single event uploaded to the sync backend. Only [JsonHttpSyncUploader]'s
 * own JSON shape uses this -- apps supplying a custom [SyncUploader] define their own wire
 * format entirely, so this type isn't part of the module's public contract.
 */
@Serializable
internal data class SyncEventDto(
    val id: String,
    val sensorType: String,
    val timestamp: Long,
    val payload: String,
)
