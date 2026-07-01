package io.github.alirezajavan.pulsekit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import io.github.alirezajavan.pulsekit.core.DataSource
import io.github.alirezajavan.pulsekit.core.permission.Permission
import io.github.alirezajavan.pulsekit.core.permission.PermissionStatus
import io.github.alirezajavan.pulsekit.core.permission.backgroundSessionPermissions
import io.github.alirezajavan.pulsekit.ui.permission.PermissionController

/**
 * Permission state for a single [DataSource], derived entirely from the source itself.
 */
@Stable
class DataSourcePermissionState internal constructor(
    private val gate: PermissionGateState,
    private val requiredPermissions: List<Permission>,
) {
    val isRequesting: Boolean get() = gate.isRequesting

    val missingRequired: List<Permission>
        get() = requiredPermissions.filter { gate.statusOf(it) != PermissionStatus.GRANTED }

    val missingOptional: List<Permission>
        get() = gate.missing.filter { it !in requiredPermissions }

    val canCollect: Boolean get() = missingRequired.isEmpty()

    fun refresh() = gate.refresh()

    fun request(onResult: (canCollect: Boolean) -> Unit = {}) {
        gate.requestAll { results ->
            onResult(requiredPermissions.all { results[it] == PermissionStatus.GRANTED })
        }
    }
}

@Composable
fun rememberDataSourcePermissionState(
    controller: PermissionController,
    dataSource: DataSource,
    sessionPermissions: List<Permission> = backgroundSessionPermissions,
): DataSourcePermissionState {
    val requiredPermissions = dataSource.requiredPermissions
    val permissions = (
        dataSource.requiredPermissions +
            dataSource.optionalPermissions +
            sessionPermissions
        ).distinct()
    val gate = rememberPermissionGateState(controller, permissions)
    return remember(gate, requiredPermissions) {
        DataSourcePermissionState(gate, requiredPermissions)
    }
}
