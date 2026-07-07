package io.github.alirezajavan.pulsekit

import io.github.alirezajavan.pulsekit.ui.BasePulseKitService
import io.github.alirezajavan.pulsekit.core.PulseKit
import io.github.alirezajavan.pulsekit.core.SyncStatusSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's foreground tracking service. [BasePulseKitService] owns the entire collection
 * lifecycle, wake lock, foreground-service typing and a default notification, so a real
 * integration only needs to point it at the shared [PulseKit] instance.
 *
 * The notification overrides below are here purely to demonstrate that the tray notification is
 * fully customizable (title/text/icon/channel, or `createForegroundNotification()` wholesale) —
 * they aren't required.
 */
class PulseKitTrackingService : BasePulseKitService() {
    override val pulseKit: PulseKit get() = PulseKitApplication.from(this).pulseKit
    override val syncState: StateFlow<SyncStatusSnapshot?>
        get() = PulseKitApplication.from(this).syncEngine.observeState()

    override val notificationContentTitle: CharSequence = "PulseKit Demo"
    override val notificationContentText: CharSequence = "Continuous sensor tracking active"
    override val notificationSmallIcon: Int = android.R.drawable.ic_menu_mylocation
}
