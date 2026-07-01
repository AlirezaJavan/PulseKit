package io.github.alirezajavan.pulsekit.motion

import io.github.alirezajavan.pulsekit.core.DataSource
import io.github.alirezajavan.pulsekit.core.NoOpPulseKitLogger
import io.github.alirezajavan.pulsekit.core.PlatformContext
import io.github.alirezajavan.pulsekit.core.PulseKitLogger
import io.github.alirezajavan.pulsekit.core.SensorPayload
import kotlinx.coroutines.flow.Flow

/**
 * Accelerometer-based motion [DataSource]. Wraps Android `SensorManager` /
 * iOS `CMMotionManager` and emits batched
 * [io.github.alirezajavan.pulsekit.core.SensorPayload.MotionChunk] values.
 *
 * [logger] receives a `warn` if a chunk is dropped because the internal buffer is full and
 * nothing is currently collecting [events] fast enough.
 */
expect class MotionDataSource(
    context: PlatformContext,
    config: MotionConfig = MotionConfig(),
    logger: PulseKitLogger = NoOpPulseKitLogger,
) : DataSource {
    override val id: String
    override val displayName: String
    override val isSupported: Boolean

    override suspend fun start(): Boolean

    override suspend fun stop()

    override fun events(): Flow<SensorPayload>
}
