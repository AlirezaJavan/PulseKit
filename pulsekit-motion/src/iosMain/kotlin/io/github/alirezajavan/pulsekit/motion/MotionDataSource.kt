package io.github.alirezajavan.pulsekit.motion

import io.github.alirezajavan.pulsekit.core.DataSource
import io.github.alirezajavan.pulsekit.core.MotionSample
import io.github.alirezajavan.pulsekit.core.PlatformContext
import io.github.alirezajavan.pulsekit.core.PulseKitLogger
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.platformCurrentTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue

private const val TAG = "MotionDataSource"

actual class MotionDataSource actual constructor(
    private val context: PlatformContext,
    private val config: MotionConfig,
    private val logger: PulseKitLogger,
) : DataSource {
    actual override val id: String = "motion"
    actual override val displayName: String = "Motion"
    actual override val isSupported: Boolean
        get() = motionManager.accelerometerAvailable

    private val motionManager = CMMotionManager()
    private val buffer = MotionSampleBuffer(config.chunkSize)
    private val quiescenceDetector = MotionQuiescenceDetector()
    private val events = MutableSharedFlow<SensorPayload>(extraBufferCapacity = 64)
    private val quiescenceState = MutableStateFlow(false)
    private var isStarted = false

    // Dedicated serial queue, not NSOperationQueue.mainQueue: at high sampling rates (tens to
    // hundreds of Hz) delivering every sample through the main queue would contend with UI work.
    // maxConcurrentOperationCount = 1 also gives buffer access on this queue a total order for
    // free, without needing a separate lock -- stop()'s buffer.clear() is dispatched onto the
    // same queue so it can never run concurrently with (or interleave misleadingly ahead of) an
    // in-flight onSample callback.
    private val sampleQueue = NSOperationQueue().apply {
        maxConcurrentOperationCount = 1
        name = "io.github.alirezajavan.pulsekit.motion.accelerometer"
    }

    actual override fun events(): Flow<SensorPayload> = events.asSharedFlow()

    actual override val providesQuiescence: Flow<Boolean>? = quiescenceState.asStateFlow()

    @OptIn(ExperimentalForeignApi::class)
    actual override suspend fun start(): Boolean {
        if (isStarted) return true
        if (!motionManager.accelerometerAvailable) {
            logger.warn(TAG, "not starting: accelerometer unavailable on this device")
            return false
        }
        isStarted = true
        motionManager.accelerometerUpdateInterval = config.samplingPeriodMicros / 1_000_000.0
        motionManager.startAccelerometerUpdatesToQueue(sampleQueue) { data, _ ->
            val acceleration = data?.acceleration ?: return@startAccelerometerUpdatesToQueue
            acceleration.useContents {
                onSample(x.toFloat(), y.toFloat(), z.toFloat())
            }
        }
        return true
    }

    actual override suspend fun stop() {
        if (!isStarted) return
        motionManager.stopAccelerometerUpdates()
        isStarted = false
        sampleQueue.addOperationWithBlock { buffer.clear() }
    }

    private fun onSample(x: Float, y: Float, z: Float) {
        val sample = MotionSample(platformCurrentTimeMillis(), x, y, z)
        
        quiescenceDetector.onSample(sample)?.let { isQuiescent ->
            quiescenceState.value = isQuiescent
        }

        val chunk = buffer.add(sample)
        if (chunk != null && !events.tryEmit(SensorPayload.MotionChunk(chunk))) {
            logger.warn(TAG, "dropped a motion chunk: events buffer full")
        }
    }
}
