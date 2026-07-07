package io.github.alirezajavan.pulsekit.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.alirezajavan.pulsekit.core.DataSource
import io.github.alirezajavan.pulsekit.core.PulseKit
import io.github.alirezajavan.pulsekit.core.permission.Permission
import io.github.alirezajavan.pulsekit.ui.PermissionGate
import io.github.alirezajavan.pulsekit.ui.permission.PermissionController
import androidx.compose.runtime.collectAsState

/** Maps a [DataSource.id] to the icon shown for it across every screen, kept in one place. */
fun DataSource.icon(): ImageVector = when (id) {
    "motion" -> Icons.Filled.Sensors
    "location" -> Icons.Filled.LocationOn
    "bluetooth" -> Icons.Filled.Bluetooth
    "step_count" -> Icons.AutoMirrored.Filled.DirectionsWalk
    else -> Icons.Filled.Bolt
}

@Composable
fun HomeScreen(
    pulseKit: PulseKit,
    permissionController: PermissionController,
    onStartSource: (String) -> Unit,
    onStartAll: () -> Unit,
    onStopAll: () -> Unit,
    onRecordManualEvent: () -> Unit,
    onReplayBatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eventCount by pulseKit.observeEventCount().collectAsState(initial = 0L)
    val activeSourceIds by pulseKit.activeSourceIds.collectAsState()
    val sources = pulseKit.dataSources

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            HeroCard(
                eventCount = eventCount,
                activeCount = activeSourceIds.size,
                totalCount = sources.size,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onStartAll,
                    enabled = activeSourceIds.size < sources.count { it.isSupported },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Start all")
                }
                OutlinedButton(
                    onClick = onStopAll,
                    enabled = activeSourceIds.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Stop all")
                }
            }
        }

        item { SectionLabel("Data sources") }

        items(sources, key = { it.id }) { source ->
            SourceCard(
                source = source,
                permissionController = permissionController,
                isCollecting = source.id in activeSourceIds,
                onStart = { onStartSource(source.id) },
            )
        }

        item { SectionLabel("Quick actions") }

        item {
            // A second, independent use of PermissionGate (SourceCard below drives its own
            // per-source flow via rememberDataSourcePermissionState) -- demonstrates the
            // general-purpose "hide content until granted" building block pulsekit-ui ships,
            // gated on the permission the foreground notification itself needs on Android 13+.
            PermissionGate(
                controller = permissionController,
                permissions = listOf(Permission.NOTIFICATIONS),
                rationale = { missing ->
                    Text(
                        "Quick actions log directly into PulseKit's event store, which powers the " +
                            "tray notification -- grant notifications to try them.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
            ) {
                DemoCard {
                    Text("Record or replay events", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "Exercise PulseKit.recordEvent / recordEvents directly, independent of any live sensor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onRecordManualEvent, modifier = Modifier.weight(1f)) {
                            Text("Manual event")
                        }
                        OutlinedButton(onClick = onReplayBatch, modifier = Modifier.weight(1f)) {
                            Text("Replay batch")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(eventCount: Long, activeCount: Int, totalCount: Int) {
    DemoCard {
        Text(
            text = "PulseKit Sensor Demo",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Continuous location, motion, steps and Bluetooth collection with network-aware sync.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatBlock(value = eventCount.toString(), label = "Events stored")
            StatBlock(value = "$activeCount/$totalCount", label = "Sources active")
        }
    }
}

@Composable
private fun SourceCard(
    source: DataSource,
    permissionController: PermissionController,
    isCollecting: Boolean,
    onStart: () -> Unit,
) {
    DemoCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconBadge(
                icon = source.icon(),
                tone = when {
                    !source.isSupported -> Tone.NEUTRAL
                    isCollecting -> Tone.POSITIVE
                    else -> Tone.NEUTRAL
                },
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(source.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                SourceStatusBody(
                    source = source,
                    permissionController = permissionController,
                    isCollecting = isCollecting,
                    onStart = onStart,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
