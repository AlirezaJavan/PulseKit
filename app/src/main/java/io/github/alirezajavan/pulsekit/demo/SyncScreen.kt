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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.alirezajavan.pulsekit.core.SyncStatusSnapshot
import io.github.alirezajavan.pulsekit.sync.NetworkMonitor
import io.github.alirezajavan.pulsekit.sync.NetworkType
import kotlinx.coroutines.delay

/** Showcases pulsekit-sync end to end: live [SyncStatusSnapshot], and the Wi-Fi-only policy toggle. */
@Composable
fun SyncScreen(
    syncStatus: SyncStatusSnapshot?,
    requireUnmeteredNetwork: Boolean,
    onRequireUnmeteredNetworkChange: (Boolean) -> Unit,
    networkMonitor: NetworkMonitor,
    modifier: Modifier = Modifier,
) {
    var currentNetworkType by remember { mutableStateOf(NetworkType.NONE) }
    LaunchedEffect(networkMonitor) {
        while (true) {
            currentNetworkType = networkMonitor.currentNetworkType()
            delay(3_000)
        }
    }

    LazyColumnContent(
        modifier = modifier,
        syncStatus = syncStatus,
        requireUnmeteredNetwork = requireUnmeteredNetwork,
        onRequireUnmeteredNetworkChange = onRequireUnmeteredNetworkChange,
        currentNetworkType = currentNetworkType,
    )
}

@Composable
private fun LazyColumnContent(
    modifier: Modifier,
    syncStatus: SyncStatusSnapshot?,
    requireUnmeteredNetwork: Boolean,
    onRequireUnmeteredNetworkChange: (Boolean) -> Unit,
    currentNetworkType: NetworkType,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { SectionLabel("Status") }
        item { SyncStatusCard(syncStatus) }

        item { SectionLabel("Network") }
        item {
            DemoCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(
                        icon = if (currentNetworkType == NetworkType.NONE) Icons.Filled.SignalWifiOff else Icons.Filled.SignalWifi4Bar,
                        tone = when (currentNetworkType) {
                            NetworkType.UNMETERED -> Tone.POSITIVE
                            NetworkType.METERED -> Tone.ATTENTION
                            NetworkType.NONE -> Tone.NEUTRAL
                        },
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Current connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text(
                            when (currentNetworkType) {
                                NetworkType.UNMETERED -> "Unmetered (e.g. Wi-Fi)"
                                NetworkType.METERED -> "Metered (e.g. cellular)"
                                NetworkType.NONE -> "No connection"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Wi-Fi only sync", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            "SyncConfig.requireUnmeteredNetwork -- pauses uploads on cellular to protect the data plan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = requireUnmeteredNetwork, onCheckedChange = onRequireUnmeteredNetworkChange)
                }
            }
        }
    }
}

@Composable
private fun SyncStatusCard(status: SyncStatusSnapshot?) {
    DemoCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            when {
                status == null -> IconBadge(icon = Icons.Filled.CloudOff, tone = Tone.NEUTRAL)
                status.isSyncing -> IconBadge(icon = Icons.Filled.CloudSync, tone = Tone.ATTENTION)
                status.isWaitingForNetwork -> IconBadge(icon = Icons.Filled.SignalWifiOff, tone = Tone.ATTENTION)
                status.lastError != null -> IconBadge(icon = Icons.Filled.Error, tone = Tone.NEGATIVE)
                else -> IconBadge(icon = Icons.Filled.CheckCircle, tone = Tone.POSITIVE)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        status == null -> "Sync engine not running"
                        status.isSyncing -> "Uploading..."
                        status.isWaitingForNetwork -> "Waiting for Wi-Fi"
                        status.lastError != null -> "Last attempt failed"
                        else -> "Up to date"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    lastSyncedLabel(status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (status?.isSyncing == true) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
            }
        }

        if (status?.lastError != null) {
            Spacer(Modifier.height(12.dp))
            Pill("Error: ${status.lastError}", tone = Tone.NEGATIVE)
        }
    }
}

private fun lastSyncedLabel(status: SyncStatusSnapshot?): String {
    val lastSuccess = status?.lastSuccessTimestampMillis ?: return "No successful sync yet"
    val secondsAgo = (System.currentTimeMillis() - lastSuccess) / 1000
    return "Last synced " + when {
        secondsAgo < 60 -> "${secondsAgo}s ago"
        secondsAgo < 3600 -> "${secondsAgo / 60}m ago"
        else -> "${secondsAgo / 3600}h ago"
    }
}
