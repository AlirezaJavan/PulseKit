package io.github.alirezajavan.pulsekit.bluetooth

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.github.alirezajavan.pulsekit.core.SensorPayload
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@RunWith(RobolectricTestRunner::class)
class BluetoothDataSourceTest {
    private lateinit var context: Application
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var dataSource: BluetoothDataSource

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        dataSource = BluetoothDataSource(context)
    }

    @Test
    fun startReturnsFalseWhenPermissionsAreMissing() = runTest {
        shadowOf(context).denyPermissions(Manifest.permission.BLUETOOTH_SCAN)

        val result = dataSource.start()

        assertFalse(result, "start() should return false when permissions are missing")
    }

    @Test
    fun startReturnsFalseWhenBluetoothIsDisabled() = runTest {
        shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_SCAN)
        bluetoothAdapter.disable()

        val result = dataSource.start()

        assertFalse(result, "start() should return false when bluetooth is disabled")
    }

    @Test
    fun startReturnsTrueWhenReadyAndCallsStartScan() = runTest {
        shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_SCAN)
        bluetoothAdapter.enable()

        val result = dataSource.start()

        assertTrue(result, "start() should return true when ready")
        val shadowScanner = shadowOf(bluetoothAdapter.bluetoothLeScanner)
        assertTrue(shadowScanner.scanCallbacks.isNotEmpty(), "should be scanning after start()")
    }

    @Test
    fun stopIsIdempotentAndStopsScan() = runTest {
        shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_SCAN)
        bluetoothAdapter.enable()

        dataSource.start()
        val shadowScanner = shadowOf(bluetoothAdapter.bluetoothLeScanner)
        assertTrue(shadowScanner.scanCallbacks.isNotEmpty())

        dataSource.stop()
        assertTrue(shadowScanner.scanCallbacks.isEmpty(), "should stop scanning after stop()")

        dataSource.stop() // safe to call twice
    }

    @Test
    fun startIsIdempotentAndDoesNotRegisterDuplicateCallbacks() = runTest {
        shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_SCAN)
        bluetoothAdapter.enable()

        dataSource.start()
        dataSource.start()

        val shadowScanner = shadowOf(bluetoothAdapter.bluetoothLeScanner)
        assertEquals(
            1,
            shadowScanner.scanCallbacks.size,
            "should only have one active scan callback",
        )
    }

    @Test
    fun eventsFlowEmitsScanResults() = runTest {
        shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_SCAN)
        bluetoothAdapter.enable()

        dataSource.start()

        val testAddress = "00:11:22:33:44:55"
        val testDevice = bluetoothAdapter.getRemoteDevice(testAddress)
        val scanResult = ScanResult(testDevice, 0, 0, 0, 0, 0, -60, 0, null, 0L)

        val deferred = async { dataSource.events().first() }
        yield()

        val shadowScanner = shadowOf(bluetoothAdapter.bluetoothLeScanner)
        shadowScanner.scanCallbacks.forEach { it.onScanResult(0, scanResult) }

        val received = deferred.await() as SensorPayload.BluetoothScan
        assertEquals(testAddress, received.address)
        assertEquals(-60, received.rssi)
    }
}
