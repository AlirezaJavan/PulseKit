package io.github.alirezajavan.pulsekit.motion

import io.github.alirezajavan.pulsekit.core.DataSource
import io.github.alirezajavan.pulsekit.core.PlatformContext
import io.github.alirezajavan.pulsekit.core.PulseKitLogger
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.permission.Permission
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import platform.CoreMotion.CMPedometer
import platform.Foundation.NSDate

private const val TAG = "StepCounterDataSource"

@OptIn(ExperimentalForeignApi::class)
actual class StepCounterDataSource actual constructor(
    private val context: PlatformContext,
    private val logger: PulseKitLogger,
) : DataSource {
    actual override val id: String = "step_count"
    actual override val displayName: String = "Steps"
    actual override val requiredPermissions: List<Permission> =
        listOf(Permission.ACTIVITY_RECOGNITION)
    actual override val isSupported: Boolean
        get() = CMPedometer.isStepCountingAvailable()

    private val pedometer = CMPedometer()
    private val events = MutableSharedFlow<SensorPayload>(extraBufferCapacity = 16)
    private var isStarted = false

    actual override fun events(): Flow<SensorPayload> = events.asSharedFlow()

    actual override suspend fun start(): Boolean {
        if (isStarted) return true
        if (!CMPedometer.isStepCountingAvailable()) {
            logger.warn(TAG, "not starting: step counting unavailable on this device")
            return false
        }
        isStarted = true
        pedometer.startPedometerUpdatesFromDate(NSDate()) { data, _ ->
            val steps = data?.numberOfSteps?.longValue ?: return@startPedometerUpdatesFromDate
            if (!events.tryEmit(SensorPayload.StepCount(steps = steps))) {
                logger.warn(TAG, "dropped a step-count reading: events buffer full")
            }
        }
        return true
    }

    actual override suspend fun stop() {
        if (!isStarted) return
        pedometer.stopPedometerUpdates()
        isStarted = false
    }
}
