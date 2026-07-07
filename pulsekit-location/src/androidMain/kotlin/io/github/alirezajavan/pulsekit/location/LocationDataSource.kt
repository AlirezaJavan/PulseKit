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
    private var currentProvider: String? = null
    private var isQuiescent = false

    actual override fun events(): Flow<SensorPayload> = events.asSharedFlow()

    @SuppressLint("MissingPermission")
    actual override suspend fun start(): Boolean {
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
            // Permission may have been revoked while a session was active -- stop so we don't
            // keep delivering updates (or retrying) against a now-unauthorized listener.
            stop()
            return false
        }

        if (listener != null) return true

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
        currentProvider = provider
        listener = newListener
        return true
    }

    @SuppressLint("MissingPermission")
    actual override fun onQuiescenceChanged(isQuiescent: Boolean) {
        if (this.isQuiescent == isQuiescent) return
        this.isQuiescent = isQuiescent

        val activeListener = listener ?: return
        val provider = currentProvider ?: return

        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation && !hasCoarseLocation) {
            // Permission was revoked while a session was active -- stop rather than risk a
            // SecurityException from requestLocationUpdates on the next quiescence flip.
            logger.warn(TAG, "not adjusting for quiescence: location permission was revoked")
            locationManager.removeUpdates(activeListener)
            listener = null
            currentProvider = null
            return
        }

        // Adjust sampling rate: if quiescent, throttle to 10x the interval (or at least 2 minutes)
        val interval = if (isQuiescent) {
            maxOf(config.minUpdateIntervalMillis * 10, 120_000L)
        } else {
            config.minUpdateIntervalMillis
        }

        logger.debug(TAG, "Quiescence changed to $isQuiescent. Adjusting interval to ${interval}ms")

        locationManager.requestLocationUpdates(
            provider,
            interval,
            config.minUpdateDistanceMeters,
            activeListener,
            Looper.getMainLooper(),
        )
    }

    actual override suspend fun stop() {
        listener?.let { locationManager.removeUpdates(it) }
        listener = null
        currentProvider = null
    }

    private companion object {
        const val TAG = "LocationDataSource"
    }
}
