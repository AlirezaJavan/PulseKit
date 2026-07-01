package io.github.alirezajavan.pulsekit.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import io.github.alirezajavan.pulsekit.core.DataSource
import io.github.alirezajavan.pulsekit.core.PlatformContext
import io.github.alirezajavan.pulsekit.core.PulseKitLogger
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.permission.Permission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

actual class BluetoothDataSource actual constructor(
    private val context: PlatformContext,
    private val config: BluetoothConfig,
    private val logger: PulseKitLogger,
) : DataSource {
    actual override val id: String = "bluetooth"
    actual override val displayName: String = "Bluetooth"
    actual override val requiredPermissions: List<Permission> =
        listOf(Permission.BLUETOOTH_SCAN)
    actual override val isSupported: Boolean
        get() = bluetoothManager.adapter != null

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val events = MutableSharedFlow<SensorPayload>(extraBufferCapacity = 16)
    private var scanner: BluetoothLeScanner? = null
    private var callback: ScanCallback? = null

    actual override fun events(): Flow<SensorPayload> = events.asSharedFlow()

    @SuppressLint("MissingPermission")
    actual override suspend fun start(): Boolean {
        if (callback != null) return true

        val hasScanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        }
        if (!hasScanPermission) {
            logger.warn(TAG, "not starting: BLE scan permission is not granted")
            return false
        }

        val adapter = bluetoothManager.adapter
        if (adapter == null) {
            logger.warn(TAG, "not starting: device has no Bluetooth adapter")
            return false
        }
        if (!adapter.isEnabled) {
            logger.warn(TAG, "not starting: Bluetooth is turned off")
            return false
        }
        val leScanner = adapter.bluetoothLeScanner
        if (leScanner == null) {
            logger.warn(TAG, "not starting: BLE scanner unavailable")
            return false
        }

        val newCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val emitted = events.tryEmit(
                    SensorPayload.BluetoothScan(
                        address = result.device.address,
                        name = result.scanRecord?.deviceName,
                        rssi = result.rssi,
                    ),
                )
                if (!emitted) logger.warn(TAG, "dropped a BLE scan result: events buffer full")
            }
        }
        val filters = config.serviceUuids.map { uuid ->
            ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(uuid))).build()
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()
        leScanner.startScan(filters, settings, newCallback)
        scanner = leScanner
        callback = newCallback
        return true
    }

    @SuppressLint("MissingPermission")
    actual override suspend fun stop() {
        val leScanner = scanner ?: return
        callback?.let { leScanner.stopScan(it) }
        scanner = null
        callback = null
    }

    private companion object {
        const val TAG = "BluetoothDataSource"
    }
}
