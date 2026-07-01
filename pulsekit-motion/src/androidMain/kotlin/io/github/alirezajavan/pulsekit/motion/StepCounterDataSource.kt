package io.github.alirezajavan.pulsekit.motion

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.alirezajavan.pulsekit.core.DataSource
import io.github.alirezajavan.pulsekit.core.PlatformContext
import io.github.alirezajavan.pulsekit.core.PulseKitLogger
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.permission.Permission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

actual class StepCounterDataSource actual constructor(
    private val context: PlatformContext,
    private val logger: PulseKitLogger,
) : DataSource {
    actual override val id: String = "step_count"
    actual override val displayName: String = "Steps"
    actual override val requiredPermissions: List<Permission> =
        listOf(Permission.ACTIVITY_RECOGNITION)
    actual override val isSupported: Boolean
        get() = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val events = MutableSharedFlow<SensorPayload>(extraBufferCapacity = 16)
    private var listener: SensorEventListener? = null

    actual override fun events(): Flow<SensorPayload> = events.asSharedFlow()

    @SuppressLint("MissingPermission")
    actual override suspend fun start(): Boolean {
        if (listener != null) return true

        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!hasPermission) {
            logger.warn(TAG, "not starting: ACTIVITY_RECOGNITION permission is not granted")
            return false
        }

        val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepCounter == null) {
            logger.warn(TAG, "not starting: device has no step counter sensor")
            return false
        }
        val newListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                onSample(event.values[0].toLong())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(newListener, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
        listener = newListener
        return true
    }

    @Synchronized
    private fun onSample(steps: Long) {
        val emitted = events.tryEmit(SensorPayload.StepCount(steps = steps))
        if (!emitted) logger.warn(TAG, "dropped a step-count reading: events buffer full")
    }

    actual override suspend fun stop() {
        listener?.let { sensorManager.unregisterListener(it) }
        listener = null
    }

    private companion object {
        const val TAG = "StepCounterDataSource"
    }
}
