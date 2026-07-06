package io.github.alirezajavan.pulsekit

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.alirezajavan.pulsekit.ui.rememberDataSourcePermissionState
import io.github.alirezajavan.pulsekit.ui.BasePulseKitService
import io.github.alirezajavan.pulsekit.core.DataSource
import io.github.alirezajavan.pulsekit.core.MotionSample
import io.github.alirezajavan.pulsekit.core.PulseKit
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.permission.Permission
import io.github.alirezajavan.pulsekit.ui.permission.PermissionController
import io.github.alirezajavan.ui.theme.PulseKitTheme
import kotlinx.coroutines.launch

/**
 * Demonstrates the full end-to-end flow with almost no library-specific knowledge in the app: the
 * screen renders one control per attached [DataSource] straight from [PulseKit.dataSources], and
 * each source describes its own permissions, so this activity hardcodes no source ids, labels, or
 * permission lists.
 */
class MainActivity : ComponentActivity() {

    private val pulseKit: PulseKit by lazy { PulseKitApplication.from(this).pulseKit }

    // Must be constructed eagerly, not lazily: registerForActivityResult (called from
    // PermissionController's constructor) throws IllegalStateException if called after the
    // activity reaches STARTED. A `by lazy` here would defer construction until the first
    // permission request -- i.e. a button tap, well after onStart -- and crash every time.
    private val permissionController = PermissionController(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PulseKitTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TrackingScreen(
                        pulseKit = pulseKit,
                        permissionController = permissionController,
                        onStartSource = ::startCollecting,
                        onStopAll = ::stopCollecting,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    private fun startCollecting(sourceId: String) {
        Toast.makeText(this, "Starting $sourceId...", Toast.LENGTH_SHORT).show()
        BasePulseKitService.startCollection(this, PulseKitTrackingService::class, setOf(sourceId))
    }

    private fun stopCollecting() {
        BasePulseKitService.stopCollection(this, PulseKitTrackingService::class)
    }
}

@Composable
fun TrackingScreen(
    pulseKit: PulseKit,
    permissionController: PermissionController,
    onStartSource: (String) -> Unit,
    onStopAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val eventCount by pulseKit.observeEventCount().collectAsState(initial = 0L)
    // Single source of truth: the app-scoped PulseKit reflects what is genuinely collecting, so
    // this survives the activity being recreated (rotation, close/reopen) instead of the UI
    // guessing at its own local flag.
    val activeSourceIds by pulseKit.activeSourceIds.collectAsState()

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "PulseKit Sensor Demo")
        Text(text = "Stored events: $eventCount")

        pulseKit.dataSources.forEach { source ->
            SourceRow(
                source = source,
                permissionController = permissionController,
                isCollecting = source.id in activeSourceIds,
                onStart = { onStartSource(source.id) },
                onFailure = { message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            )
        }

        Button(
            enabled = activeSourceIds.isNotEmpty(),
            onClick = onStopAll,
        ) {
            Text("Stop collecting")
        }

        // One-off event outside the attached data sources, e.g. a custom in-app action worth
        // logging alongside sensor data.
        Button(
            onClick = {
                pulseKit.recordEvent(SensorPayload.StepCount(steps = 1), type = "manual_ping")
                Toast.makeText(context, "Recorded manual event", Toast.LENGTH_SHORT).show()
            },
        ) {
            Text("Record manual event")
        }

        // Simulates replaying a historical batch pulled from a platform buffer (e.g. iOS
        // pedometer history after relaunch) rather than events observed live.
        Button(
            onClick = {
                coroutineScope.launch {
                    val replayed = List(5) { index ->
                        SensorPayload.MotionChunk(
                            samples = listOf(
                                MotionSample(
                                    timestamp = System.currentTimeMillis() - index * 1_000L,
                                    x = 0f,
                                    y = 0f,
                                    z = 0f,
                                ),
                            ),
                        )
                    }
                    pulseKit.recordEvents(replayed, type = "motion_replay")
                    Toast.makeText(context, "Replayed ${replayed.size} events", Toast.LENGTH_SHORT)
                        .show()
                }
            },
        ) {
            Text("Replay historical batch")
        }

        // Right-to-erasure (GDPR/CCPA) style action: wipes every stored row regardless of age
        // or sync status.
        Button(
            onClick = {
                coroutineScope.launch {
                    pulseKit.eraseAllData()
                    Toast.makeText(context, "Erased all stored data", Toast.LENGTH_SHORT).show()
                }
            },
        ) {
            Text("Erase all data")
        }
    }
}

/**
 * Permission-first per-source flow: the tap requests this source's permissions (staged, in the
 * order the source declares); collection starts only once every required permission is granted,
 * and any denied optional permission is surfaced as a "collecting without" note instead of
 * blocking. All of this is derived from [source] — the row knows nothing source-specific.
 */
@Composable
private fun SourceRow(
    source: DataSource,
    permissionController: PermissionController,
    isCollecting: Boolean,
    onStart: () -> Unit,
    onFailure: (String) -> Unit,
) {
    if (!source.isSupported) {
        Column {
            Button(enabled = false, onClick = {}) {
                Text("${source.displayName}: Unsupported Hardware")
            }
        }
        return
    }

    val permissionState = rememberDataSourcePermissionState(
        controller = permissionController,
        dataSource = source,
    )

    Column {
        Button(
            enabled = !isCollecting && !permissionState.isRequesting,
            onClick = {
                permissionState.request { canCollect ->
                    if (canCollect) {
                        onStart()
                    } else {
                        onFailure("Required permissions for ${source.displayName} denied")
                    }
                }
            },
        ) {
            Text(
                when {
                    isCollecting -> "${source.displayName}: collecting..."
                    permissionState.isRequesting -> "${source.displayName}: requesting..."
                    else -> "Collect ${source.displayName}"
                },
            )
        }
        if (!isCollecting && permissionState.missingRequired.isNotEmpty()) {
            Text("Needs: ${permissionState.missingRequired.joinToString { it.readableName() }}")
        } else if (isCollecting && permissionState.missingOptional.isNotEmpty()) {
            val names = permissionState.missingOptional.joinToString { it.readableName() }
            Text("Collecting without: $names")
        }
    }
}

private fun Permission.readableName(): String = name.replace('_', ' ').lowercase()

@Preview(showBackground = true)
@Composable
fun TrackingScreenPreview() {
    PulseKitTheme {
        Text("PulseKit Sensor Demo")
    }
}
