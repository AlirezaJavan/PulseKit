package io.github.alirezajavan.pulsekit.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.alirezajavan.pulsekit.core.PulseKit
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.ui.permission.PermissionController

/**
 * Live BLE scan results, deduplicated by address (keeping the most recent RSSI/name) since
 * [SensorPayload.BluetoothScan] events aren't otherwise readable back from the store -- only
 * `pulseKit.dataSources`'s `events()` flow exposes them live.
 */
@Composable
fun BluetoothScreen(
    pulseKit: PulseKit,
    permissionController: PermissionController,
    onStartSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeSourceIds by pulseKit.activeSourceIds.collectAsState()
    val bluetoothSource = pulseKit.dataSources.first { it.id == "bluetooth" }
    val isScanning = bluetoothSource.id in activeSourceIds

    val devices = remember { mutableStateMapOf<String, SensorPayload.BluetoothScan>() }
    LaunchedEffect(bluetoothSource) {
        bluetoothSource.events().collect { payload ->
            if (payload is SensorPayload.BluetoothScan) devices[payload.address] = payload
        }
    }
    val sortedDevices = devices.values.sortedByDescending { it.rssi }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DemoCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconBadge(
                        icon = Icons.Filled.Bluetooth,
                        tone = if (isScanning) Tone.POSITIVE else Tone.NEUTRAL,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Nearby devices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "PulseKit scans periodically (30s every 5min) to save battery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        SourceStatusBody(
                            source = bluetoothSource,
                            permissionController = permissionController,
                            isCollecting = isScanning,
                            onStart = { onStartSource(bluetoothSource.id) },
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }

        item { SectionLabel("${sortedDevices.size} device${if (sortedDevices.size == 1) "" else "s"} seen") }

        if (sortedDevices.isEmpty()) {
            item {
                DemoCard {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.BluetoothSearching,
                        title = if (isScanning) "Scanning..." else "No scan results yet",
                        subtitle = if (isScanning) {
                            "Nearby BLE devices will appear here as they're discovered."
                        } else {
                            "Start Bluetooth collection above to begin scanning."
                        },
                    )
                }
            }
        } else {
            items(sortedDevices, key = { it.address }) { device -> DeviceRow(device) }
        }
    }
}

@Composable
private fun DeviceRow(device: SensorPayload.BluetoothScan) {
    DemoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.name?.takeIf { it.isNotBlank() } ?: "Unknown device",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val (label, tone) = when {
                device.rssi >= -60 -> "Strong" to Tone.POSITIVE
                device.rssi >= -80 -> "Fair" to Tone.ATTENTION
                else -> "Weak" to Tone.NEUTRAL
            }
            Column(horizontalAlignment = Alignment.End) {
                Pill("${device.rssi} dBm", tone = tone)
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
