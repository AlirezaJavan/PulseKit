package io.github.alirezajavan.pulsekit.bluetooth

import io.github.alirezajavan.pulsekit.core.DataSource
import io.github.alirezajavan.pulsekit.core.PlatformContext
import io.github.alirezajavan.pulsekit.core.PulseKitLogger
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.permission.Permission
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerScanOptionAllowDuplicatesKey
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSNumber
import platform.darwin.NSObject

private const val TAG = "BluetoothDataSource"

/**
 * Bluetooth LE scan [DataSource] wrapping `CBCentralManager`.
 *
 * Note the real platform constraint (unlike Android): iOS only redelivers scan results in the
 * background for peripherals advertising one of the service UUIDs the scan was started with --
 * an unfiltered background scan (`config.serviceUuids` empty) effectively stops discovering new
 * devices once the app is backgrounded. This isn't a bug in this data source, it's Core
 * Bluetooth's documented background execution behavior; integrating apps that need continuous
 * multi-day background discovery must supply the specific service UUID(s) they care about.
 */
@OptIn(ExperimentalForeignApi::class)
actual class BluetoothDataSource actual constructor(
    private val context: PlatformContext,
    private val config: BluetoothConfig,
    private val logger: PulseKitLogger,
) : DataSource {
    actual override val id: String = "bluetooth"
    actual override val displayName: String = "Bluetooth"
    actual override val requiredPermissions: List<Permission> =
        listOf(Permission.BLUETOOTH_SCAN)
    actual override val isSupported: Boolean = true

    private val events = MutableSharedFlow<SensorPayload>(extraBufferCapacity = 16)
    private var centralManager: CBCentralManager? = null
    private var delegate: CBCentralManagerDelegateProtocol? = null

    actual override fun events(): Flow<SensorPayload> = events.asSharedFlow()

    actual override suspend fun start(): Boolean {
        if (centralManager != null) return true

        val newDelegate = object : NSObject(), CBCentralManagerDelegateProtocol {
            override fun centralManagerDidUpdateState(central: CBCentralManager) {
                if (central.state == CBManagerStatePoweredOn) {
                    startScanning(central)
                }
            }

            override fun centralManager(
                central: CBCentralManager,
                didDiscoverPeripheral: CBPeripheral,
                advertisementData: Map<Any?, *>,
                RSSI: NSNumber,
            ) {
                val emitted = events.tryEmit(
                    SensorPayload.BluetoothScan(
                        address = didDiscoverPeripheral.identifier.UUIDString,
                        name = didDiscoverPeripheral.name,
                        rssi = RSSI.intValue,
                    ),
                )
                if (!emitted) logger.warn(TAG, "dropped a BLE scan result: events buffer full")
            }
        }
        // queue = null dispatches every delegate callback (including didDiscoverPeripheral,
        // which can fire frequently in BLE-dense environments) onto the main queue, risking
        // contention with UI work during sustained scanning. The real fix is passing a
        // dedicated serial dispatch queue here, but bridging dispatch_queue_create's raw
        // CPointer to the NSObject? this interop's `queue` parameter expects needs a Kotlin/
        // Native cinterop trick (interpretObjCPointer) that couldn't be verified from this
        // Windows environment (no Xcode/simulator access) -- tracked as a backlog item in
        // REFACTOR_PLAN.md rather than shipped unverified.
        val manager = CBCentralManager(delegate = newDelegate, queue = null)
        delegate = newDelegate
        centralManager = manager
        if (manager.state == CBManagerStatePoweredOn) {
            startScanning(manager)
        }
        return true
    }

    private fun startScanning(central: CBCentralManager) {
        val serviceUuids = config.serviceUuids.takeIf { it.isNotEmpty() }
            ?.map { CBUUID.UUIDWithString(it) }
        central.scanForPeripheralsWithServices(
            serviceUuids,
            mapOf(CBCentralManagerScanOptionAllowDuplicatesKey to false),
        )
    }

    actual override suspend fun stop() {
        centralManager?.stopScan()
        centralManager = null
        delegate = null
    }
}
