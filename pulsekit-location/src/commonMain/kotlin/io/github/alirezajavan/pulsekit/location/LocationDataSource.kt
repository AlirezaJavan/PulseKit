package io.github.alirezajavan.pulsekit.location

import io.github.alirezajavan.pulsekit.core.DataSource
import io.github.alirezajavan.pulsekit.core.NoOpPulseKitLogger
import io.github.alirezajavan.pulsekit.core.PlatformContext
import io.github.alirezajavan.pulsekit.core.PulseKitLogger
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.permission.Permission
import kotlinx.coroutines.flow.Flow

/**
 * Location [DataSource]. Wraps Android `LocationManager` / iOS `CLLocationManager` and emits
 * [io.github.alirezajavan.pulsekit.core.SensorPayload.Location] values.
 *
 * Declares [Permission.LOCATION_FOREGROUND] as required and [Permission.LOCATION_BACKGROUND] as
 * optional -- request them via `PermissionController` (or `pulsekit-ui`'s
 * `rememberDataSourcePermissionState`) before starting; [start] returns `false` (and logs why)
 * if the required grant is missing.
 *
 * [logger] receives a `warn` if a sample is dropped because the internal buffer is full and
 * nothing is currently collecting [events] fast enough.
 */
expect class LocationDataSource(
    context: PlatformContext,
    config: LocationConfig = LocationConfig(),
    logger: PulseKitLogger = NoOpPulseKitLogger,
) : DataSource {
    override val id: String
    override val displayName: String
    override val requiredPermissions: List<Permission>
    override val optionalPermissions: List<Permission>
    override val isSupported: Boolean

    override suspend fun start(): Boolean

    override suspend fun stop()

    override fun events(): Flow<SensorPayload>

    override fun onQuiescenceChanged(isQuiescent: Boolean)
}
