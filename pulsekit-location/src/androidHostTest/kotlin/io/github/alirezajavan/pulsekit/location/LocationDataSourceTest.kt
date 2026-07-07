package io.github.alirezajavan.pulsekit.location

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
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
import org.robolectric.shadows.ShadowLocationManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@RunWith(RobolectricTestRunner::class)
class LocationDataSourceTest {
    private lateinit var context: Application
    private lateinit var locationManager: LocationManager
    private lateinit var shadowLocationManager: ShadowLocationManager
    private lateinit var dataSource: LocationDataSource

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        shadowLocationManager = shadowOf(locationManager)
        dataSource = LocationDataSource(context)
    }

    @Test
    fun startReturnsFalseWhenPermissionsAreMissing() = runTest {
        // Ensure permissions are NOT granted
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

        val result = dataSource.start()

        assertFalse(result, "start() should return false when permissions are missing")
    }

    @Test
    fun startReturnsFalseWhenLocationProvidersAreDisabled() = runTest {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)

        val result = dataSource.start()

        assertFalse(result, "start() should return false when providers are disabled")
    }

    @Test
    fun startReturnsTrueWhenFineLocationGrantedAndProviderEnabled() = runTest {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)

        val result = dataSource.start()

        assertTrue(result, "start() should return true when permissions and providers are ready")
        assertEquals(1, shadowLocationManager.requestLocationUpdateListeners.size)
    }

    @Test
    fun stopIsIdempotentAndRemovesListener() = runTest {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)

        dataSource.start()
        assertEquals(1, shadowLocationManager.requestLocationUpdateListeners.size)

        dataSource.stop()
        assertEquals(0, shadowLocationManager.requestLocationUpdateListeners.size)

        dataSource.stop() // safe to call twice
        assertEquals(0, shadowLocationManager.requestLocationUpdateListeners.size)
    }

    @Test
    fun startIsIdempotentAndDoesNotRegisterDuplicateListeners() = runTest {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)

        dataSource.start()
        dataSource.start()

        assertEquals(1, shadowLocationManager.requestLocationUpdateListeners.size)
    }

    @Test
    fun startReturnsFalseWhenPermissionIsRevokedMidSession() = runTest {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)

        assertTrue(dataSource.start())

        // Revoke permission
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

        // On next start() call (e.g. from a periodic cycle or a retry), it should return false
        assertFalse(dataSource.start())
    }

    @Test
    fun eventsFlowEmitsLocationUpdates() = runTest {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)

        dataSource.start()

        val testLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 37.7749
            longitude = -122.4194
            accuracy = 10.0f
            speed = 5.0f
            time = System.currentTimeMillis()
        }

        val deferred = async { dataSource.events().first() }
        yield()

        // Trigger the listener
        shadowLocationManager.simulateLocation(testLocation)
        shadowOf(Looper.getMainLooper()).idle()

        val received = deferred.await() as SensorPayload.Location
        assertEquals(testLocation.latitude, received.latitude)
        assertEquals(testLocation.longitude, received.longitude)
        assertEquals(testLocation.accuracy, received.accuracy)
        assertEquals(testLocation.speed, received.speed)
    }
}
