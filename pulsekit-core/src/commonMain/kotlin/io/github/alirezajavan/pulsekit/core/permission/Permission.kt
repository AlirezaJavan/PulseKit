package io.github.alirezajavan.pulsekit.core.permission

/**
 * OS-level permissions PulseKit data sources might require.
 */
enum class Permission {
    LOCATION_FOREGROUND,
    LOCATION_BACKGROUND,
    NOTIFICATIONS,
    BLUETOOTH_SCAN,
    ACTIVITY_RECOGNITION,
    IGNORE_BATTERY_OPTIMIZATIONS,
}

/**
 * Default permissions suggested for background collection sessions to keep the app process alive
 * and healthy.
 */
expect val backgroundSessionPermissions: List<Permission>
