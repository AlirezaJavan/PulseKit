package io.github.alirezajavan.pulsekit.ui.setup

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import io.github.alirezajavan.pulsekit.core.DataSource
import io.github.alirezajavan.pulsekit.core.PulseKit
import io.github.alirezajavan.pulsekit.core.permission.Permission
import io.github.alirezajavan.pulsekit.ui.BasePulseKitService
import io.github.alirezajavan.pulsekit.ui.PulseKitBootReceiver
import io.github.alirezajavan.pulsekit.ui.permission.foregroundServiceType
import io.github.alirezajavan.pulsekit.ui.permission.manifestPermissions
import kotlin.reflect.KClass

/**
 * Thrown by [validateAndroidSetup] listing every manifest/component gap found in one message.
 */
class PulseKitSetupException(issues: List<String>) : Exception(
    "PulseKit setup is incomplete:\n" + issues.joinToString("\n") { "- $it" },
)

/**
 * Cross-checks [context]'s merged `AndroidManifest.xml` against the data sources already added to
 * this builder and the [serviceClass]/[bootReceiverClass] the app intends to run tracking from.
 */
fun PulseKit.Builder.validateAndroidSetup(
    context: Context,
    serviceClass: KClass<out BasePulseKitService>? = null,
    bootReceiverClass: KClass<out PulseKitBootReceiver>? = null,
): PulseKit.Builder = apply {
    val issues = mutableListOf<String>()
    val packageManager = context.packageManager
    val packageName = context.packageName

    val declaredPermissions = requestedManifestPermissions(packageManager, packageName)
    val logger = loggerSnapshot()
    val requiredServiceTypes = mutableSetOf<Int>()

    for (dataSource in dataSourcesSnapshot()) {
        for (permission in dataSource.requiredPermissions) {
            reportMissingManifestPermissions(dataSource, permission, declaredPermissions) {
                issues += it
            }
            val serviceType = permission.foregroundServiceType()
            if (serviceType != 0) requiredServiceTypes += serviceType
        }
        for (permission in dataSource.optionalPermissions) {
            reportMissingManifestPermissions(dataSource, permission, declaredPermissions) {
                logger.warn(TAG, "$it -- collection will run degraded without it")
            }
        }
    }

    if (serviceClass == null) {
        logger.warn(
            TAG,
            "No BasePulseKitService subclass was provided to validateAndroidSetup -- PulseKit " +
                "can only collect data while the app is in the foreground. Extend " +
                "BasePulseKitService and declare it in your manifest for background collection.",
        )
    } else {
        val serviceComponent = ComponentName(packageName, serviceClass.java.name)
        val serviceInfo = serviceDeclared(packageManager, serviceComponent)
        if (serviceInfo == null) {
            issues += "${serviceClass.java.name} is not declared as a <service> in " +
                "AndroidManifest.xml"
        } else if (requiredServiceTypes.isNotEmpty() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            val declaredType = serviceInfo.foregroundServiceType
            val missingTypes = requiredServiceTypes.filter { declaredType and it != it }
            if (missingTypes.isNotEmpty()) {
                val missingNames = missingTypes.joinToString { foregroundServiceTypeName(it) }
                issues += "${serviceClass.java.name}'s <service> tag is missing " +
                    "foregroundServiceType flag(s) [$missingNames] " +
                    "required by the data sources added to this builder"
            }
        }
    }

    if (bootReceiverClass != null) {
        val receiverComponent = ComponentName(packageName, bootReceiverClass.java.name)
        if (!receiverDeclared(packageManager, receiverComponent)) {
            issues += "${bootReceiverClass.java.name} is not declared as a <receiver> in " +
                "AndroidManifest.xml -- tracking will not resume after a device reboot"
        }
    }

    if (issues.isNotEmpty()) {
        throw PulseKitSetupException(issues)
    }
}

private const val TAG = "PulseKitSetup"

private inline fun reportMissingManifestPermissions(
    dataSource: DataSource,
    permission: Permission,
    declaredPermissions: Set<String>,
    report: (String) -> Unit,
) {
    for (manifestPermission in permission.manifestPermissions()) {
        if (manifestPermission !in declaredPermissions) {
            report(
                "Manifest is missing \"$manifestPermission\" " +
                    "(needed by the \"${dataSource.id}\" data source's $permission)",
            )
        }
    }
}

private fun requestedManifestPermissions(pm: PackageManager, packageName: String): Set<String> {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
        pm.getPackageInfo(packageName, flags)
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
    }
    return packageInfo.requestedPermissions?.toSet() ?: emptySet()
}

private fun serviceDeclared(pm: PackageManager, component: ComponentName): ServiceInfo? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getServiceInfo(component, PackageManager.ComponentInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getServiceInfo(component, 0)
    }
} catch (e: PackageManager.NameNotFoundException) {
    null
}

private fun receiverDeclared(pm: PackageManager, component: ComponentName): Boolean = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getReceiverInfo(component, PackageManager.ComponentInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getReceiverInfo(component, 0)
    }
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

private fun foregroundServiceTypeName(type: Int): String = when (type) {
    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION -> "location"
    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE -> "connectedDevice"
    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH -> "health"
    else -> "0x${type.toString(16)}"
}
