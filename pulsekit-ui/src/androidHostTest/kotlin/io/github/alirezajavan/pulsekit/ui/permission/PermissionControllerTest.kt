package io.github.alirezajavan.pulsekit.ui.permission

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import io.github.alirezajavan.pulsekit.core.permission.Permission
import io.github.alirezajavan.pulsekit.core.permission.PermissionStatus
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Exercises the Android [PermissionController]'s status/already-granted logic against a real
 * (Robolectric-backed) [ComponentActivity].
 */
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
@RunWith(RobolectricTestRunner::class)
class PermissionControllerTest {
    private fun newController(): Pair<ComponentActivity, PermissionController> {
        val activityController = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity = activityController.get()
        val permissionController = PermissionController(activity)
        activityController.start().resume()
        return activity to permissionController
    }

    @Test
    fun requestLocationBackgroundReturnsGrantedWithoutRequestingWhenAlreadyGranted() = runTest {
        val (activity, permissionController) = newController()
        shadowOf(activity).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        )

        val result = permissionController.request(Permission.LOCATION_BACKGROUND)

        assertEquals(PermissionStatus.GRANTED, result)
    }

    @Test
    fun statusReportsDeniedPermanentlyWhenRationaleIsNoLongerShown() {
        val (activity, permissionController) = newController()
        shadowOf(activity).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(activity.packageManager).setShouldShowRequestPermissionRationale(
            Manifest.permission.ACCESS_FINE_LOCATION,
            false,
        )

        assertEquals(
            PermissionStatus.DENIED_PERMANENTLY,
            permissionController.status(Permission.LOCATION_FOREGROUND),
        )
    }

    @Test
    fun statusReportsDeniedWhenRationaleIsStillShowable() {
        val (activity, permissionController) = newController()
        shadowOf(activity).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(activity.packageManager).setShouldShowRequestPermissionRationale(
            Manifest.permission.ACCESS_FINE_LOCATION,
            true,
        )

        assertEquals(
            PermissionStatus.DENIED,
            permissionController.status(Permission.LOCATION_FOREGROUND),
        )
    }

    @Test
    fun statusReflectsAlreadyGrantedPermissionsWithoutRequesting() {
        val (activity, permissionController) = newController()
        shadowOf(activity).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

        assertEquals(
            PermissionStatus.GRANTED,
            permissionController.status(Permission.LOCATION_FOREGROUND),
        )
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun backgroundLocationIsImpliedByForegroundBelowApi29() = runTest {
        val (activity, permissionController) = newController()
        shadowOf(activity).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        val result = permissionController.request(Permission.LOCATION_BACKGROUND)

        assertEquals(PermissionStatus.GRANTED, result)
    }
}
