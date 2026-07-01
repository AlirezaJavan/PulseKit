package io.github.alirezajavan.pulsekit.ui.permission

import io.github.alirezajavan.pulsekit.core.PlatformContext
import io.github.alirezajavan.pulsekit.core.permission.Permission
import io.github.alirezajavan.pulsekit.core.permission.PermissionStatus

/**
 * Checks and requests the OS permissions PulseKit's data sources need. Permission dialogs can
 * only be triggered from the app's UI layer (an Activity on Android, implicitly the app process
 * on iOS), so this type is meant to be owned by the app, not by a background service.
 *
 * On Android, [context] must actually be a `ComponentActivity` (passed as [PlatformContext]
 * since that's the platform-agnostic constructor shape) — constructing it from a non-Activity
 * Context will fail, because [request] needs an Activity to launch the system permission UI.
 */
expect class PermissionController(context: PlatformContext) {
    fun status(permission: Permission): PermissionStatus

    suspend fun request(permission: Permission): PermissionStatus
}
