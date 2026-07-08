package io.github.alirezajavan.pulsekit.testing

import io.github.alirezajavan.pulsekit.core.TimeProvider

/**
 * A [TimeProvider] whose time can be manually advanced or set, for deterministic testing of
 * age-based pruning and time-dependent logic.
 */
class MutableTimeProvider(initialMillis: Long = 0) : TimeProvider {
    private var currentTime: Long = initialMillis

    override fun nowMillis(): Long = currentTime

    fun advanceBy(millis: Long) {
        currentTime += millis
    }

    fun setTime(millis: Long) {
        currentTime = millis
    }
}
