package io.github.alirezajavan.pulsekit.core

/**
 * Abstraction for UUID generation to facilitate deterministic testing.
 */
fun interface IdProvider {
    fun nextId(): String
}

/**
 * Default implementation using the platform's UUID generator.
 */
object SystemIdProvider : IdProvider {
    override fun nextId(): String = platformGenerateUuid()
}
