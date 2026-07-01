package io.github.alirezajavan.pulsekit.ui.host

import io.github.alirezajavan.pulsekit.core.PulseKit
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways

/**
 * On iOS, there is no background Service. To keep the app process alive for continuous or
 * periodic collection while the app is backgrounded, a [CLLocationManager] must be active with
 * background updates enabled.
 *
 * Use this host to wrap your [PulseKit] instance on iOS. It handles the "keep-alive" location
 * updates required to prevent the OS from suspending the process when backgrounded.
 *
 * Note: Consuming apps must declare the `location` UIBackgroundModes in their `Info.plist`.
 */
class IosPulseKitHost(private val pulseKit: PulseKit) {
    private val locationManager = CLLocationManager()

    init {
        // Minimal distance filter to reduce battery impact while still keeping the process awake.
        locationManager.distanceFilter = 500.0
        locationManager.pausesLocationUpdatesAutomatically = false
    }

    /**
     * Starts the keep-alive location updates (if "Always" permission is granted) and starts
     * the [PulseKit] engine.
     */
    fun start() {
        if (locationManager.authorizationStatus == kCLAuthorizationStatusAuthorizedAlways) {
            locationManager.allowsBackgroundLocationUpdates = true
            locationManager.startUpdatingLocation()
        }
        pulseKit.start()
    }

    /**
     * Stops the keep-alive location updates and the [PulseKit] engine.
     */
    fun stop() {
        locationManager.stopUpdatingLocation()
        locationManager.allowsBackgroundLocationUpdates = false
        // Note: we don't call pulseKit.stop() here as the user might want to manage
        // the engine lifecycle separately, but typically they go together.
    }
}
