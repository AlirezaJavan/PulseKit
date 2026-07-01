package io.github.alirezajavan.pulsekit.ui.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import io.github.alirezajavan.pulsekit.core.PlatformContext
import io.github.alirezajavan.pulsekit.core.permission.Permission
import io.github.alirezajavan.pulsekit.core.permission.PermissionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

actual class PermissionController actual constructor(context: PlatformContext) {
    private val activity = context as? ComponentActivity
        ?: error(
            "PermissionController requires a ComponentActivity on Android, got ${context::class}",
        )

    private val requestMutex = Mutex()

    private var pendingSingle: CompletableDeferred<Boolean>? = null
    private val singleLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> pendingSingle?.complete(granted) }

    private var pendingMultiple: CompletableDeferred<Map<String, Boolean>>? = null
    private val multipleLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> pendingMultiple?.complete(result) }

    private var pendingBatteryOptimization: CompletableDeferred<Unit>? = null
    private val batteryOptimizationLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { pendingBatteryOptimization?.complete(Unit) }

    actual fun status(permission: Permission): PermissionStatus = when (permission) {
        Permission.LOCATION_FOREGROUND -> foregroundLocationStatus()
        Permission.LOCATION_BACKGROUND -> backgroundLocationStatus()
        Permission.NOTIFICATIONS -> notificationsStatus()
        Permission.BLUETOOTH_SCAN -> bluetoothScanStatus()
        Permission.ACTIVITY_RECOGNITION -> activityRecognitionStatus()
        Permission.IGNORE_BATTERY_OPTIMIZATIONS -> batteryOptimizationStatus()
    }

    actual suspend fun request(permission: Permission): PermissionStatus = requestMutex.withLock {
        when (permission) {
            Permission.LOCATION_FOREGROUND -> requestForegroundLocation()
            Permission.LOCATION_BACKGROUND -> requestBackgroundLocation()
            Permission.NOTIFICATIONS -> requestNotifications()
            Permission.BLUETOOTH_SCAN -> requestBluetoothScan()
            Permission.ACTIVITY_RECOGNITION -> requestActivityRecognition()
            Permission.IGNORE_BATTERY_OPTIMIZATIONS -> requestIgnoreBatteryOptimizations()
        }
    }

    private fun foregroundLocationStatus(): PermissionStatus {
        val granted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (granted) return PermissionStatus.GRANTED
        return deniedStatus(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun backgroundLocationStatus(): PermissionStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return foregroundLocationStatus()
        }
        if (hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            return PermissionStatus.GRANTED
        }
        return deniedStatus(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    private fun notificationsStatus(): PermissionStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return PermissionStatus.GRANTED
        if (hasPermission(Manifest.permission.POST_NOTIFICATIONS)) return PermissionStatus.GRANTED
        return deniedStatus(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun bluetoothScanStatus(): PermissionStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return foregroundLocationStatus()
        if (hasPermission(Manifest.permission.BLUETOOTH_SCAN)) return PermissionStatus.GRANTED
        return deniedStatus(Manifest.permission.BLUETOOTH_SCAN)
    }

    private fun activityRecognitionStatus(): PermissionStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return PermissionStatus.GRANTED
        if (hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)) return PermissionStatus.GRANTED
        return deniedStatus(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    private suspend fun requestForegroundLocation(): PermissionStatus {
        if (foregroundLocationStatus() == PermissionStatus.GRANTED) return PermissionStatus.GRANTED
        val deferred = CompletableDeferred<Map<String, Boolean>>()
        pendingMultiple = deferred
        multipleLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
        deferred.await()
        return foregroundLocationStatus()
    }

    private suspend fun requestBackgroundLocation(): PermissionStatus {
        if (foregroundLocationStatus() != PermissionStatus.GRANTED) {
            val foregroundResult = requestForegroundLocation()
            if (foregroundResult != PermissionStatus.GRANTED) return foregroundResult
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return PermissionStatus.GRANTED
        if (backgroundLocationStatus() == PermissionStatus.GRANTED) return PermissionStatus.GRANTED

        val deferred = CompletableDeferred<Boolean>()
        pendingSingle = deferred
        singleLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        deferred.await()
        return backgroundLocationStatus()
    }

    private suspend fun requestNotifications(): PermissionStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return PermissionStatus.GRANTED
        if (notificationsStatus() == PermissionStatus.GRANTED) return PermissionStatus.GRANTED

        val deferred = CompletableDeferred<Boolean>()
        pendingSingle = deferred
        singleLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        deferred.await()
        return notificationsStatus()
    }

    private suspend fun requestBluetoothScan(): PermissionStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return requestForegroundLocation()
        if (bluetoothScanStatus() == PermissionStatus.GRANTED) return PermissionStatus.GRANTED

        val deferred = CompletableDeferred<Boolean>()
        pendingSingle = deferred
        singleLauncher.launch(Manifest.permission.BLUETOOTH_SCAN)
        deferred.await()
        return bluetoothScanStatus()
    }

    private suspend fun requestActivityRecognition(): PermissionStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return PermissionStatus.GRANTED
        if (activityRecognitionStatus() == PermissionStatus.GRANTED) return PermissionStatus.GRANTED

        val deferred = CompletableDeferred<Boolean>()
        pendingSingle = deferred
        singleLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        deferred.await()
        return activityRecognitionStatus()
    }

    private fun batteryOptimizationStatus(): PermissionStatus {
        val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (powerManager.isIgnoringBatteryOptimizations(activity.packageName)) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.NOT_DETERMINED
        }
    }

    private suspend fun requestIgnoreBatteryOptimizations(): PermissionStatus {
        if (batteryOptimizationStatus() == PermissionStatus.GRANTED) return PermissionStatus.GRANTED

        val deferred = CompletableDeferred<Unit>()
        pendingBatteryOptimization = deferred
        batteryOptimizationLauncher.launch(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${activity.packageName}"),
            ),
        )
        deferred.await()
        return batteryOptimizationStatus()
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    private fun deniedStatus(permission: String): PermissionStatus =
        if (activity.shouldShowRequestPermissionRationale(permission)) {
            PermissionStatus.DENIED
        } else {
            PermissionStatus.DENIED_PERMANENTLY
        }
}
