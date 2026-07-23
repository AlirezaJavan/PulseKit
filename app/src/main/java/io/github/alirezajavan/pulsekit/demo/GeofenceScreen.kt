package io.github.alirezajavan.pulsekit.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.alirezajavan.pulsekit.geofence.GeofenceEvent
import io.github.alirezajavan.pulsekit.geofence.GeofenceProcessor
import io.github.alirezajavan.pulsekit.geofence.GeofenceRegion
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun GeofenceScreen(
    processor: GeofenceProcessor,
    regions: List<GeofenceRegion>,
) {
    val events = remember { mutableStateListOf<GeofenceEvent>() }
    val currentlyInside by processor.currentlyInside.collectAsState()
    
    LaunchedEffect(processor) {
        processor.events.collect { event ->
            events.add(0, event)
            if (events.size > 50) events.removeAt(events.size - 1)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            SectionLabel("Monitored Regions", modifier = Modifier.padding(16.dp))
        }
        items(regions) { region ->
            RegionItem(region, currentlyInside.contains(region.id))
        }
        item {
            Spacer(Modifier.height(16.dp))
            SectionLabel("Transition Log", modifier = Modifier.padding(16.dp))
        }
        if (events.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Explore,
                    title = "No transitions yet",
                    subtitle = "Move into or out of a region to trigger one."
                )
            }
        }
        items(events) { event ->
            EventItem(event)
        }
    }
}

@Composable
private fun RegionItem(region: GeofenceRegion, isInside: Boolean) {
    DemoCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isInside) Icons.Filled.CheckCircle else Icons.Filled.Circle,
                contentDescription = null,
                tint = if (isInside) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(region.id.replace("_", " ").capitalize(), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${region.latitude}, ${region.longitude} · ${region.radiusMeters}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EventItem(event: GeofenceEvent) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                event.transition.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (event.transition == GeofenceEvent.Transition.ENTER) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                event.regionId.replace("_", " ").capitalize(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatTimestamp(event.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}:${dt.second.toString().padStart(2, '0')}"
}

private fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
