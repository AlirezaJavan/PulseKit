package io.github.alirezajavan.pulsekit.sync

import io.github.alirezajavan.pulsekit.core.PlatformContext
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_constrained
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_t
import platform.darwin.dispatch_queue_create

actual class NetworkMonitor actual constructor(context: PlatformContext) : NetworkTypeProvider {
    private val monitor = nw_path_monitor_create()
    private var _currentPath: nw_path_t = null

    init {
        val queue = dispatch_queue_create("io.github.alirezajavan.pulsekit.network.monitor", null)
        nw_path_monitor_set_queue(monitor, queue)
        nw_path_monitor_set_update_handler(monitor) { path ->
            _currentPath = path
        }
        nw_path_monitor_start(monitor)
    }

    actual override fun currentNetworkType(): NetworkType {
        val path = _currentPath ?: return NetworkType.NONE

        val status = nw_path_get_status(path)
        if (status != nw_path_status_satisfied) return NetworkType.NONE

        return if (nw_path_is_expensive(path) || nw_path_is_constrained(path)) {
            NetworkType.METERED
        } else {
            NetworkType.UNMETERED
        }
    }
}
