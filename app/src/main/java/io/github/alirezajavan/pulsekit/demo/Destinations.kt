package io.github.alirezajavan.pulsekit.demo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One entry per bottom-nav destination/tab. Every PulseKit feature area (collection, live
 * readings, Bluetooth scan results, sync diagnostics, permissions/config) gets its own screen so
 * each is reachable directly instead of being buried inside a single scrolling list.
 */
enum class PulseKitDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home(
        route = "home",
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    Sensors(
        route = "sensors",
        label = "Sensors",
        selectedIcon = Icons.Filled.Sensors,
        unselectedIcon = Icons.Outlined.Sensors,
    ),
    Bluetooth(
        route = "bluetooth",
        label = "Bluetooth",
        selectedIcon = Icons.Filled.Bluetooth,
        unselectedIcon = Icons.Outlined.Bluetooth,
    ),
    Sync(
        route = "sync",
        label = "Sync",
        selectedIcon = Icons.Filled.CloudSync,
        unselectedIcon = Icons.Outlined.CloudSync,
    ),
    Settings(
        route = "settings",
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
}
