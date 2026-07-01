package io.github.alirezajavan.pulsekit.bluetooth

import io.github.alirezajavan.pulsekit.core.DataSource
import io.github.alirezajavan.pulsekit.core.NoOpPulseKitLogger
import io.github.alirezajavan.pulsekit.core.PlatformContext
import io.github.alirezajavan.pulsekit.core.PulseKitLogger
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.permission.Permission
import kotlinx.coroutines.flow.Flow

/**
 * Bluetooth Low Energy scan [DataSource]. Wraps Android `BluetoothLeScanner` / iOS
 * `CBCentralManager` and emits [io.github.alirezajavan.pulsekit.core.SensorPayload.BluetoothScan]
 * values for each nearby device discovered.
 *
 * Callers are responsible for requesting the relevant runtime permission
 * (`BLUETOOTH_SCAN` on Android 12+, `ACCESS_FINE_LOCATION` below that; Bluetooth authorization on
 * iOS) before calling [start]; this source assumes permission has already been granted.
 *
 * [logger] receives a `warn` if a scan result is dropped because the internal buffer is full and
 * nothing is currently collecting [events] fast enough.
 */
expect class BluetoothDataSource(
    context: PlatformContext,
    config: BluetoothConfig = BluetoothConfig(),
    logger: PulseKitLogger = NoOpPulseKitLogger,
) : DataSource {
    override val id: String
    override val displayName: String
    override val requiredPermissions: List<Permission>
    override val isSupported: Boolean

    override suspend fun start(): Boolean

    override suspend fun stop()

    override fun events(): Flow<SensorPayload>
}
