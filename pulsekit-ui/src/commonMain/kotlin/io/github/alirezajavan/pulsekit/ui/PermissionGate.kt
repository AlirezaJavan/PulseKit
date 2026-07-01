package io.github.alirezajavan.pulsekit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.alirezajavan.pulsekit.core.permission.Permission
import io.github.alirezajavan.pulsekit.core.permission.PermissionStatus
import io.github.alirezajavan.pulsekit.ui.permission.PermissionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Tracks the status of [permissions] against [controller] and drives the sequential
 * staged-request flow PulseKit's platform actuals require.
 */
@Stable
class PermissionGateState internal constructor(
    private val controller: PermissionController,
    private val permissions: List<Permission>,
    private val scope: CoroutineScope,
) {
    var statuses: Map<Permission, PermissionStatus> by mutableStateOf(emptyMap())
        private set

    var isRequesting: Boolean by mutableStateOf(false)
        private set

    val allGranted: Boolean
        get() = permissions.isNotEmpty() &&
            permissions.all { statuses[it] == PermissionStatus.GRANTED }

    val missing: List<Permission>
        get() = permissions.filter { statuses[it] != PermissionStatus.GRANTED }

    fun statusOf(permission: Permission): PermissionStatus =
        statuses[permission] ?: controller.status(permission)

    fun refresh() {
        statuses = permissions.associateWith { controller.status(it) }
    }

    fun requestAll(onComplete: (Map<Permission, PermissionStatus>) -> Unit = {}) {
        if (isRequesting) return
        scope.launch {
            isRequesting = true
            try {
                val results = LinkedHashMap<Permission, PermissionStatus>()
                for (permission in permissions) {
                    results[permission] = controller.request(permission)
                    statuses = LinkedHashMap(results)
                }
                onComplete(results)
            } finally {
                isRequesting = false
            }
        }
    }
}

@Composable
fun rememberPermissionGateState(
    controller: PermissionController,
    permissions: List<Permission>,
): PermissionGateState {
    val scope = rememberCoroutineScope()
    val state = remember(controller, permissions) {
        PermissionGateState(controller, permissions, scope)
    }
    LaunchedEffect(state) { state.refresh() }
    return state
}

@Composable
fun PermissionGate(
    controller: PermissionController,
    permissions: List<Permission>,
    modifier: Modifier = Modifier,
    rationale: @Composable (missing: List<Permission>) -> Unit = { DefaultRationale(it) },
    content: @Composable () -> Unit,
) {
    val state = rememberPermissionGateState(controller, permissions)
    if (state.allGranted) {
        content()
    } else {
        Column(modifier = modifier) {
            rationale(state.missing)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { state.requestAll() }, enabled = !state.isRequesting) {
                Text(if (state.isRequesting) "Requesting..." else "Grant permissions")
            }
        }
    }
}

@Composable
private fun DefaultRationale(missing: List<Permission>) {
    Column {
        Text("This feature needs the following permissions:")
        missing.forEach { permission ->
            Text("• ${permission.name.replace('_', ' ').lowercase()}")
        }
    }
}
