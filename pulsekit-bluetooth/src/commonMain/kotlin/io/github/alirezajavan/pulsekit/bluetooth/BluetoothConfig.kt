package io.github.alirezajavan.pulsekit.bluetooth

/** Tunables for [BluetoothDataSource]. */
data class BluetoothConfig(
    /**
     * Service UUIDs to filter scan results by (as lowercase 128-bit UUID strings). Empty means
     * scan for any nearby device.
     *
     * On iOS, an empty filter only returns results while the app is foregrounded — background BLE
     * scanning without specific service UUIDs is not delivered by the OS at all (Apple's Core
     * Bluetooth background execution rules), so continuous multi-day background collection on iOS
     * requires narrowing this to the specific service UUID(s) the integrating app cares about.
     */
    val serviceUuids: List<String> = emptyList(),
)
