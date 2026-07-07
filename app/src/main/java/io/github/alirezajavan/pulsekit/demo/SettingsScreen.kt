package io.github.alirezajavan.pulsekit.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.alirezajavan.pulsekit.bluetooth.BluetoothConfig
import io.github.alirezajavan.pulsekit.core.permission.Permission
import io.github.alirezajavan.pulsekit.core.permission.PermissionStatus
import io.github.alirezajavan.pulsekit.location.LocationConfig
import io.github.alirezajavan.pulsekit.motion.MotionConfig
import io.github.alirezajavan.pulsekit.sync.SyncConfig
import io.github.alirezajavan.pulsekit.ui.permission.PermissionController

@Composable
fun SettingsScreen(
    permissionController: PermissionController,
    locationConfig: LocationConfig,
    motionConfig: MotionConfig,
    bluetoothConfig: BluetoothConfig,
    syncConfig: SyncConfig,
    onEraseAllData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Bump on manual refresh and whenever the screen resumes (e.g. after granting a permission
    // from Settings and switching back) so statuses below reflect reality, not a stale snapshot
    // from first composition.
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTrigger++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val statuses = remember(refreshTrigger) {
        Permission.entries.associateWith { permissionController.status(it) }
    }

    var showEraseConfirmation by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("Permissions")
                IconButton(onClick = { refreshTrigger++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh permissions")
                }
            }
        }
        item {
            DemoCard {
                statuses.entries.forEachIndexed { index, (permission, status) ->
                    PermissionRow(permission, status)
                    if (index != statuses.size - 1) Spacer(Modifier.height(10.dp))
                }
            }
        }

        item { SectionLabel("Configuration") }
        item {
            DemoCard {
                ConfigHeader(Icons.Filled.Tune, "Location")
                ReadingRow("Update interval", "${locationConfig.minUpdateIntervalMillis / 1000}s")
                ReadingRow("Min distance", "${locationConfig.minUpdateDistanceMeters.toInt()} m")
            }
        }
        item {
            DemoCard {
                ConfigHeader(Icons.Filled.Tune, "Motion")
                ReadingRow("Chunk size", "${motionConfig.chunkSize} samples")
                ReadingRow("Sampling rate", "${1_000_000 / motionConfig.samplingPeriodMicros} Hz")
            }
        }
        item {
            DemoCard {
                ConfigHeader(Icons.Filled.Tune, "Bluetooth")
                ReadingRow(
                    "Service UUID filter",
                    bluetoothConfig.serviceUuids.takeIf { it.isNotEmpty() }?.joinToString() ?: "All devices",
                )
            }
        }
        item {
            DemoCard {
                ConfigHeader(Icons.Filled.Tune, "Sync")
                ReadingRow("Batch size", "${syncConfig.batchSize} events")
                ReadingRow("Idle poll interval", "${syncConfig.idlePollIntervalMillis / 1000}s")
                ReadingRow(
                    "Backoff range",
                    "${syncConfig.initialBackoffMillis / 1000}s – ${syncConfig.maxBackoffMillis / 1000}s",
                )
            }
        }

        item { SectionLabel("Data") }
        item {
            DemoCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconBadge(icon = Icons.Filled.DeleteForever, tone = Tone.NEGATIVE)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Erase all data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "Wipes every stored event regardless of age or sync status (PulseKit.eraseAllData).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { showEraseConfirmation = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Erase all data")
                }
            }
        }

        item { SectionLabel("About") }
        item { AboutCard() }
    }

    if (showEraseConfirmation) {
        AlertDialog(
            onDismissRequest = { showEraseConfirmation = false },
            icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null) },
            title = { Text("Erase all data?") },
            text = { Text("This permanently deletes every stored sensor event on this device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showEraseConfirmation = false
                    onEraseAllData()
                }) { Text("Erase") }
            },
            dismissButton = {
                TextButton(onClick = { showEraseConfirmation = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PermissionRow(permission: Permission, status: PermissionStatus) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            permission.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
        )
        val (label, tone) = when (status) {
            PermissionStatus.GRANTED -> "Granted" to Tone.POSITIVE
            PermissionStatus.DENIED -> "Denied" to Tone.ATTENTION
            PermissionStatus.DENIED_PERMANENTLY -> "Blocked" to Tone.NEGATIVE
            PermissionStatus.NOT_DETERMINED -> "Not asked" to Tone.NEUTRAL
        }
        Pill(label, tone = tone)
    }
}

@Composable
private fun ConfigHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(18.dp).width(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ReadingRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val packageInfo = remember {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    DemoCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconBadge(icon = Icons.Filled.Info, tone = Tone.NEUTRAL)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("PulseKit demo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "App v${packageInfo.versionName} · Library v0.2.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(16.dp).width(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Zero-manifest library: this app owns every declared permission and component.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
