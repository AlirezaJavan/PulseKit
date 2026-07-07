package io.github.alirezajavan.pulsekit.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.alirezajavan.pulsekit.core.DataSource
import io.github.alirezajavan.pulsekit.core.PulseKit
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.ui.permission.PermissionController
import kotlinx.coroutines.flow.flowOf
import kotlin.math.sqrt

/**
 * Live readings straight from each [DataSource.events] flow -- these are never persisted to a
 * local variable by the library itself (only aggregate `observeEventCount()` is public), so this
 * screen subscribes directly rather than needing any new library API.
 */
@Composable
fun SensorsScreen(
    pulseKit: PulseKit,
    permissionController: PermissionController,
    onStartSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeSourceIds by pulseKit.activeSourceIds.collectAsState()
    val locationSource = pulseKit.dataSources.first { it.id == "location" }
    val motionSource = pulseKit.dataSources.first { it.id == "motion" }
    val stepsSource = pulseKit.dataSources.first { it.id == "step_count" }

    val lastLocation by produceState<SensorPayload.Location?>(null, locationSource) {
        locationSource.events().collect { payload ->
            if (payload is SensorPayload.Location) value = payload
        }
    }
    val lastMotionChunk by produceState<SensorPayload.MotionChunk?>(null, motionSource) {
        motionSource.events().collect { payload ->
            if (payload is SensorPayload.MotionChunk) value = payload
        }
    }
    val isQuiescent by (motionSource.providesQuiescence ?: flowOf(false))
        .collectAsState(initial = false)
    val lastStepCount by produceState<SensorPayload.StepCount?>(null, stepsSource) {
        stepsSource.events().collect { payload ->
            if (payload is SensorPayload.StepCount) value = payload
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { SectionLabel("Location") }
        item {
            SensorCard(
                source = locationSource,
                permissionController = permissionController,
                isCollecting = locationSource.id in activeSourceIds,
                onStart = { onStartSource(locationSource.id) },
            ) {
                val location = lastLocation
                if (location == null) {
                    EmptyState(
                        icon = Icons.Filled.LocationOn,
                        title = "No fix yet",
                        subtitle = "Waiting for the first GPS/network location update.",
                    )
                } else {
                    ReadingRow("Latitude", "%.5f".format(location.latitude))
                    ReadingRow("Longitude", "%.5f".format(location.longitude))
                    ReadingRow("Accuracy", "±${location.accuracy.toInt()} m")
                    ReadingRow("Speed", "%.1f m/s".format(location.speed))
                }
            }
        }

        item { SectionLabel("Motion") }
        item {
            SensorCard(
                source = motionSource,
                permissionController = permissionController,
                isCollecting = motionSource.id in activeSourceIds,
                onStart = { onStartSource(motionSource.id) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Adaptive sampling", style = MaterialTheme.typography.bodyMedium)
                    Pill(
                        text = if (isQuiescent) "Stationary" else "Moving",
                        tone = if (isQuiescent) Tone.ATTENTION else Tone.POSITIVE,
                    )
                }
                val chunk = lastMotionChunk
                if (chunk == null || chunk.samples.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.Sensors,
                        title = "No samples yet",
                        subtitle = "Waiting for the first accelerometer chunk to flush.",
                    )
                } else {
                    val latest = chunk.samples.last()
                    val magnitude = sqrt(latest.x * latest.x + latest.y * latest.y + latest.z * latest.z)
                    ReadingRow("Chunk size", "${chunk.samples.size} samples")
                    ReadingRow("Last magnitude", "%.2f m/s²".format(magnitude))
                    ReadingRow("x / y / z", "%.2f / %.2f / %.2f".format(latest.x, latest.y, latest.z))
                }
            }
        }

        item { SectionLabel("Steps") }
        item {
            SensorCard(
                source = stepsSource,
                permissionController = permissionController,
                isCollecting = stepsSource.id in activeSourceIds,
                onStart = { onStartSource(stepsSource.id) },
            ) {
                val steps = lastStepCount
                if (steps == null) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                        title = "No steps yet",
                        subtitle = "The step counter reports a cumulative total since last reboot.",
                    )
                } else {
                    ReadingRow("Step count", steps.steps.toString())
                }
            }
        }
    }
}

@Composable
private fun SensorCard(
    source: DataSource,
    permissionController: PermissionController,
    isCollecting: Boolean,
    onStart: () -> Unit,
    content: @Composable () -> Unit,
) {
    DemoCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconBadge(icon = source.icon(), tone = if (isCollecting) Tone.POSITIVE else Tone.NEUTRAL)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(source.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                SourceStatusBody(
                    source = source,
                    permissionController = permissionController,
                    isCollecting = isCollecting,
                    onStart = onStart,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}

@Composable
private fun ReadingRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
