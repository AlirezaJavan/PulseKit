package io.github.alirezajavan.pulsekit

import io.github.alirezajavan.pulsekit.ui.PulseKitBootReceiver
import io.github.alirezajavan.pulsekit.ui.BasePulseKitService
import kotlin.reflect.KClass

/**
 * Resumes tracking after a device reboot.
 */
class PulseKitBootReceiverImpl : PulseKitBootReceiver() {
    override val serviceClass: KClass<out BasePulseKitService> = PulseKitTrackingService::class
}
