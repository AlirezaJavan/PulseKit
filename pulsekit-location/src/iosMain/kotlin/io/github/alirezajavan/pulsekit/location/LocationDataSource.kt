package io.github.alirezajavan.pulsekit.location

import io.github.alirezajavan.pulsekit.core.DataSource
import io.github.alirezajavan.pulsekit.core.PlatformContext
import io.github.alirezajavan.pulsekit.core.PulseKitLogger
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.permission.Permission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.CoreLocation.kCLLocationAccuracyThreeKilometers
import platform.Foundation.NSError
import platform.darwin.NSObject

private const val TAG = "LocationDataSource"

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
    actual override val isSupported: Boolean = true

    private val locationManager = CLLocationManager()
    private val events = MutableSharedFlow<SensorPayload>(extraBufferCapacity = 16)
    private var delegate: CLLocationManagerDelegateProtocol? = null
    private var isQuiescent = false

    actual override fun events(): Flow<SensorPayload> = events.asSharedFlow()

    @OptIn(ExperimentalForeignApi::class)
    actual override suspend fun start(): Boolean {
        if (delegate != null) return true
        when (locationManager.authorizationStatus) {
            kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> {
                logger.warn(TAG, "not starting: location authorization is denied/restricted")
                return false
            }
            else -> Unit
        }
        val newDelegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                val location = (didUpdateLocations.lastOrNull() as? CLLocation) ?: return
                val (lat, lon) = location.coordinate.useContents { latitude to longitude }
                val emitted = events.tryEmit(
                    SensorPayload.Location(
                        latitude = lat,
                        longitude = lon,
                        accuracy = location.horizontalAccuracy.toFloat(),
                        speed = location.speed.toFloat(),
                    ),
                )
                if (!emitted) logger.warn(TAG, "dropped a location sample: events buffer full")
            }

            override fun locationManager(
                manager: CLLocationManager,
                didFailWithError: NSError,
            ) = Unit
        }
        locationManager.delegate = newDelegate
        locationManager.distanceFilter = config.minUpdateDistanceMeters.toDouble()
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        // Only legal once "Always" authorization is granted and Info.plist declares the
        // `location` UIBackgroundModes entry -- otherwise the OS throws. Continuous multi-day
        // collection needs both flags so iOS doesn't pause/pause-then-suspend updates while
        // backgrounded.
        if (locationManager.authorizationStatus == kCLAuthorizationStatusAuthorizedAlways) {
            locationManager.allowsBackgroundLocationUpdates = true
            locationManager.pausesLocationUpdatesAutomatically = false
        }
        locationManager.startUpdatingLocation()
        delegate = newDelegate
        return true
    }

    actual override fun onQuiescenceChanged(isQuiescent: Boolean) {
        if (this.isQuiescent == isQuiescent) return
        this.isQuiescent = isQuiescent

        if (delegate == null) return

        if (isQuiescent) {
            logger.debug(TAG, "Quiescence detected. Throttling location updates.")
            locationManager.desiredAccuracy = kCLLocationAccuracyThreeKilometers
            locationManager.distanceFilter = maxOf(config.minUpdateDistanceMeters.toDouble(), 1000.0)
        } else {
            logger.debug(TAG, "Movement detected. Restoring location updates.")
            locationManager.desiredAccuracy = kCLLocationAccuracyBest
            locationManager.distanceFilter = config.minUpdateDistanceMeters.toDouble()
        }
    }

    actual override suspend fun stop() {
        if (delegate == null) return
        locationManager.stopUpdatingLocation()
        locationManager.delegate = null
        delegate = null
    }
}
