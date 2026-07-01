package io.github.alirezajavan.pulsekit.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import io.github.alirezajavan.pulsekit.core.DataSource
import io.github.alirezajavan.pulsekit.core.PlatformContext
import io.github.alirezajavan.pulsekit.core.PulseKitLogger
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.permission.Permission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

actual class LocationDataSource actual constructor(
    private val context: PlatformContext,
    private val config: LocationConfig,
    private val logger: PulseKitLogger,
) : DataSource {
    actual override val id: String = "location"
    actual override val displayName: String = "GPS"
    actual override val requiredPermissions: List<Permission> =
        listOf(Permission.LOCATION_FOREGROUND)
    actual override val optionalPermissions: List<Permission> =
        listOf(Permission.LOCATION_BACKGROUND)
    actual override val isSupported: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val events = MutableSharedFlow<SensorPayload>(extraBufferCapacity = 16)
    private var listener: LocationListener? = null

    actual override fun events(): Flow<SensorPayload> = events.asSharedFlow()

    @SuppressLint("MissingPermission")
    actual override suspend fun start(): Boolean {
        if (listener != null) return true

        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation && !hasCoarseLocation) {
            logger.warn(TAG, "not starting: neither fine nor coarse location permission is granted")
            return false
        }

        val newListener = LocationListener { location ->
            val emitted = events.tryEmit(
                SensorPayload.Location(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    speed = location.speed,
                ),
            )
            if (!emitted) logger.warn(TAG, "dropped a location sample: events buffer full")
        }
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            else -> {
                logger.warn(
                    TAG,
                    "not starting: no enabled location provider (is device location off?)",
                )
                return false
            }
        }
        // start() runs on the caller's coroutine dispatcher (a plain worker thread with no
        // Looper) -- the requestLocationUpdates overload without an explicit Looper binds the
        // listener to the calling thread's Looper and throws "Can't create handler inside
        // thread ... that has not called Looper.prepare()" there, so callbacks must be pinned
        // to the main Looper explicitly.
        locationManager.requestLocationUpdates(
            provider,
            config.minUpdateIntervalMillis,
            config.minUpdateDistanceMeters,
            newListener,
            Looper.getMainLooper(),
        )
        listener = newListener
        return true
    }

    actual override suspend fun stop() {
        listener?.let { locationManager.removeUpdates(it) }
        listener = null
    }

    private companion object {
        const val TAG = "LocationDataSource"
    }
}
