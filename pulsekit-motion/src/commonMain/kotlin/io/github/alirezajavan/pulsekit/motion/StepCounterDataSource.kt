package io.github.alirezajavan.pulsekit.motion

import io.github.alirezajavan.pulsekit.core.DataSource
import io.github.alirezajavan.pulsekit.core.NoOpPulseKitLogger
import io.github.alirezajavan.pulsekit.core.PlatformContext
import io.github.alirezajavan.pulsekit.core.PulseKitLogger
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.permission.Permission
import kotlinx.coroutines.flow.Flow

/**
 * Step-count [DataSource]. Wraps Android `Sensor.TYPE_STEP_COUNTER` / iOS `CMPedometer` and emits
 * [io.github.alirezajavan.pulsekit.core.SensorPayload.StepCount] values.
 *
 * Unlike [MotionDataSource]'s raw accelerometer, this needs a runtime permission on both
 * platforms (`ACTIVITY_RECOGNITION` on Android 10+, `CMPedometer` authorization on iOS) --
 * callers must request `Permission.ACTIVITY_RECOGNITION` before calling [start]; this source
 * assumes it has already been granted.
 *
 * [logger] receives a `warn` if a reading is dropped because the internal buffer is full and
 * nothing is currently collecting [events] fast enough.
 */
expect class StepCounterDataSource(
    context: PlatformContext,
    logger: PulseKitLogger = NoOpPulseKitLogger,
) : DataSource {
    override val id: String
    override val displayName: String
    override val requiredPermissions: List<Permission>
    override val isSupported: Boolean

    override suspend fun start(): Boolean

    override suspend fun stop()

    override fun events(): Flow<SensorPayload>
}
