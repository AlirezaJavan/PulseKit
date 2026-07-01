package io.github.alirezajavan.pulsekit.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.github.alirezajavan.pulsekit.core.DataSource
import io.github.alirezajavan.pulsekit.core.MotionSample
import io.github.alirezajavan.pulsekit.core.PlatformContext
import io.github.alirezajavan.pulsekit.core.PulseKitLogger
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.platformCurrentTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

actual class MotionDataSource actual constructor(
    context: PlatformContext,
    private val config: MotionConfig,
    private val logger: PulseKitLogger,
) : DataSource {
    actual override val id: String = "motion"
    actual override val displayName: String = "Motion"
    actual override val isSupported: Boolean
        get() = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val buffer = MotionSampleBuffer(config.chunkSize)
    private val events = MutableSharedFlow<SensorPayload>(extraBufferCapacity = 64)
    private var listener: SensorEventListener? = null

    actual override fun events(): Flow<SensorPayload> = events.asSharedFlow()

    actual override suspend fun start(): Boolean {
        if (listener != null) return true
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            logger.warn(TAG, "not starting: device has no accelerometer")
            return false
        }
        val newListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                onSample(event.values[0], event.values[1], event.values[2])
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(newListener, accelerometer, config.samplingPeriodMicros)
        listener = newListener
        return true
    }

    actual override suspend fun stop() {
        listener?.let { sensorManager.unregisterListener(it) }
        listener = null
        clearBuffer()
    }

    @Synchronized
    private fun onSample(x: Float, y: Float, z: Float) {
        val chunk = buffer.add(MotionSample(platformCurrentTimeMillis(), x, y, z))
        if (chunk != null && !events.tryEmit(SensorPayload.MotionChunk(chunk))) {
            logger.warn(TAG, "dropped a motion chunk: events buffer full")
        }
    }

    // registerListener() without an explicit Handler delivers callbacks on whichever
    // thread/Looper happened to be current when start() was called (the caller's coroutine
    // dispatcher, or the main thread as a fallback) -- stop() can legitimately run on a different
    // thread than onSample()'s callback, so buffer mutation on this path must share onSample's
    // monitor too, or a stop() clearing concurrently with an in-flight onSample can throw
    // ConcurrentModificationException / corrupt the chunk being read.
    @Synchronized
    private fun clearBuffer() {
        buffer.clear()
    }

    private companion object {
        const val TAG = "MotionDataSource"
    }
}
