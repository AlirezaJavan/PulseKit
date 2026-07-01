package io.github.alirezajavan.pulsekit.ui.permission

import io.github.alirezajavan.pulsekit.core.PlatformContext
import io.github.alirezajavan.pulsekit.core.permission.Permission
import io.github.alirezajavan.pulsekit.core.permission.PermissionStatus
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationDenied
import platform.CoreBluetooth.CBManagerAuthorizationRestricted
import platform.CoreMotion.CMAuthorizationStatusAuthorized
import platform.CoreMotion.CMAuthorizationStatusDenied
import platform.CoreMotion.CMAuthorizationStatusRestricted
import platform.CoreMotion.CMPedometer
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.Foundation.NSDate
import platform.darwin.NSObject

/**
 * iOS has no OS-enforced two-step location request the way Android 10+ does, but Apple's
 * documented pattern is the same shape: request When-In-Use first, and only step up to Always
 * once that's already granted. [Permission.NOTIFICATIONS] is a no-op GRANTED here since iOS
 * background location doesn't need a separate notification permission the way an Android
 * foreground service does.
 */
@OptIn(ExperimentalForeignApi::class)
actual class PermissionController actual constructor(
    @Suppress("UNUSED_PARAMETER") context: PlatformContext,
) {
    private val requestMutex = Mutex()

    private val locationManager = CLLocationManager()
    private var pendingAuthorization: CompletableDeferred<Unit>? = null

    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            pendingAuthorization?.complete(Unit)
            pendingAuthorization = null
        }
    }

    private var pendingBluetoothAuthorization: CompletableDeferred<Unit>? = null
    private var bluetoothManager: CBCentralManager? = null
    private val bluetoothDelegate = object : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            pendingBluetoothAuthorization?.complete(Unit)
            pendingBluetoothAuthorization = null
        }
    }

    init {
        locationManager.delegate = delegate
    }

    actual fun status(permission: Permission): PermissionStatus = when (permission) {
        Permission.LOCATION_FOREGROUND -> foregroundStatus()
        Permission.LOCATION_BACKGROUND -> backgroundStatus()
        Permission.NOTIFICATIONS -> PermissionStatus.GRANTED
        Permission.BLUETOOTH_SCAN -> bluetoothStatus()
        Permission.ACTIVITY_RECOGNITION -> activityRecognitionStatus()
        Permission.IGNORE_BATTERY_OPTIMIZATIONS -> PermissionStatus.GRANTED
    }

    actual suspend fun request(permission: Permission): PermissionStatus = requestMutex.withLock {
        when (permission) {
            Permission.LOCATION_FOREGROUND -> requestForeground()
            Permission.LOCATION_BACKGROUND -> requestBackground()
            Permission.NOTIFICATIONS -> PermissionStatus.GRANTED
            Permission.BLUETOOTH_SCAN -> requestBluetooth()
            Permission.ACTIVITY_RECOGNITION -> requestActivityRecognition()
            Permission.IGNORE_BATTERY_OPTIMIZATIONS -> PermissionStatus.GRANTED
        }
    }

    private fun foregroundStatus(): PermissionStatus = when (locationManager.authorizationStatus) {
        kCLAuthorizationStatusAuthorizedAlways,
        kCLAuthorizationStatusAuthorizedWhenInUse,
        -> PermissionStatus.GRANTED
        kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> PermissionStatus.DENIED
        else -> PermissionStatus.NOT_DETERMINED
    }

    private fun backgroundStatus(): PermissionStatus = when (locationManager.authorizationStatus) {
        kCLAuthorizationStatusAuthorizedAlways -> PermissionStatus.GRANTED
        kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> PermissionStatus.DENIED
        else -> PermissionStatus.NOT_DETERMINED
    }

    private suspend fun requestForeground(): PermissionStatus {
        if (foregroundStatus() == PermissionStatus.GRANTED) return PermissionStatus.GRANTED
        awaitAuthorizationChange { locationManager.requestWhenInUseAuthorization() }
        return foregroundStatus()
    }

    private suspend fun requestBackground(): PermissionStatus {
        if (foregroundStatus() != PermissionStatus.GRANTED) {
            val foregroundResult = requestForeground()
            if (foregroundResult != PermissionStatus.GRANTED) return foregroundResult
        }
        if (backgroundStatus() == PermissionStatus.GRANTED) return PermissionStatus.GRANTED
        awaitAuthorizationChange { locationManager.requestAlwaysAuthorization() }
        return backgroundStatus()
    }

    private suspend fun awaitAuthorizationChange(request: () -> Unit) {
        val deferred = CompletableDeferred<Unit>()
        pendingAuthorization = deferred
        request()
        deferred.await()
    }

    private fun bluetoothStatus(): PermissionStatus = when (CBCentralManager.authorization) {
        CBManagerAuthorizationAllowedAlways -> PermissionStatus.GRANTED
        CBManagerAuthorizationDenied, CBManagerAuthorizationRestricted -> PermissionStatus.DENIED
        else -> PermissionStatus.NOT_DETERMINED
    }

    private suspend fun requestBluetooth(): PermissionStatus {
        if (bluetoothStatus() == PermissionStatus.GRANTED) return PermissionStatus.GRANTED
        if (bluetoothManager == null) {
            val deferred = CompletableDeferred<Unit>()
            pendingBluetoothAuthorization = deferred
            bluetoothManager = CBCentralManager(delegate = bluetoothDelegate, queue = null)
            deferred.await()
        }
        return bluetoothStatus()
    }

    private fun activityRecognitionStatus(): PermissionStatus =
        when (CMPedometer.authorizationStatus()) {
            CMAuthorizationStatusAuthorized -> PermissionStatus.GRANTED
            CMAuthorizationStatusDenied, CMAuthorizationStatusRestricted -> PermissionStatus.DENIED
            else -> PermissionStatus.NOT_DETERMINED
        }

    private suspend fun requestActivityRecognition(): PermissionStatus {
        if (activityRecognitionStatus() == PermissionStatus.GRANTED) return PermissionStatus.GRANTED
        if (!CMPedometer.isStepCountingAvailable()) return activityRecognitionStatus()

        val deferred = CompletableDeferred<Unit>()
        val pedometer = CMPedometer()
        pedometer.startPedometerUpdatesFromDate(NSDate()) { _, _ ->
            pedometer.stopPedometerUpdates()
            if (!deferred.isCompleted) deferred.complete(Unit)
        }
        deferred.await()
        return activityRecognitionStatus()
    }
}
