package io.github.alirezajavan.pulsekit.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.reflect.KClass

/**
 * Resumes PulseKit tracking automatically after a device reboot.
 */
abstract class PulseKitBootReceiver : BroadcastReceiver() {
    /**
     * The app's [BasePulseKitService] subclass to restart on boot.
     */
    protected abstract val serviceClass: KClass<out BasePulseKitService>

    override fun onReceive(context: Context, intent: Intent) {
        // No-op by default: starting collection automatically on boot without a way to
        // know what was previously active is usually not what's desired. Apps should
        // override this to resume specific sources from their own persistent state.
    }
}
