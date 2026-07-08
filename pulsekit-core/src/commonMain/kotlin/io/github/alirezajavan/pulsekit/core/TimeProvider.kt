package io.github.alirezajavan.pulsekit.core

/**
 * Abstraction for system time to facilitate testing with virtual clocks.
 */
fun interface TimeProvider {
    fun nowMillis(): Long
}

/**
 * Default implementation using the platform's system clock.
 */
object SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long = platformCurrentTimeMillis()
}
