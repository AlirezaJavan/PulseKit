package io.github.alirezajavan.pulsekit.ui.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.alirezajavan.pulsekit.core.permission.Permission

/** Manifest `<uses-permission>` names this [Permission] needs declared on this device's SDK. */
internal fun Permission.manifestPermissions(): List<String> = when (this) {
    Permission.LOCATION_FOREGROUND -> listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    Permission.LOCATION_BACKGROUND ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            emptyList()
        }
    Permission.NOTIFICATIONS ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }
    Permission.BLUETOOTH_SCAN ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    Permission.ACTIVITY_RECOGNITION ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            emptyList()
        }
    Permission.IGNORE_BATTERY_OPTIMIZATIONS ->
        listOf(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
}

/**
 * The `foregroundServiceType` flag a service collecting under this [Permission] must declare and
 * pass to `startForeground`, or 0 if none applies.
 */
internal fun Permission.foregroundServiceType(): Int = when (this) {
    Permission.LOCATION_FOREGROUND, Permission.LOCATION_BACKGROUND ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
    Permission.BLUETOOTH_SCAN ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
    Permission.ACTIVITY_RECOGNITION ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        } else {
            0
        }
    Permission.NOTIFICATIONS, Permission.IGNORE_BATTERY_OPTIMIZATIONS -> 0
}

/**
 * Whether this [Permission]'s runtime grant is currently held, checked without an Activity (so
 * usable from a Service).
 */
internal fun Permission.isRuntimeGranted(context: Context): Boolean = when (this) {
    Permission.IGNORE_BATTERY_OPTIMIZATIONS -> true
    Permission.LOCATION_FOREGROUND ->
        hasAnyPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    else -> manifestPermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

private fun hasAnyPermission(context: Context, vararg permissions: String): Boolean =
    permissions.any { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
