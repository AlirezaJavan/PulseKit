package io.github.alirezajavan.pulsekit.sync

import io.github.alirezajavan.pulsekit.core.PlatformContext

/**
 * Interface for providing the current [NetworkType].
 */
fun interface NetworkTypeProvider {
    fun currentNetworkType(): NetworkType
}

/**
 * Platform-specific implementation for monitoring network state.
 */
expect class NetworkMonitor(context: PlatformContext) : NetworkTypeProvider {
    override fun currentNetworkType(): NetworkType
}
