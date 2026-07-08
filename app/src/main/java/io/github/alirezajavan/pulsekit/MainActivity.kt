package io.github.alirezajavan.pulsekit

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.alirezajavan.pulsekit.core.PulseKit
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.MotionSample
import io.github.alirezajavan.pulsekit.demo.BluetoothScreen
import io.github.alirezajavan.pulsekit.demo.HistoryScreen
import io.github.alirezajavan.pulsekit.demo.HomeScreen
import io.github.alirezajavan.pulsekit.demo.PulseKitDestination
import io.github.alirezajavan.pulsekit.demo.SensorsScreen
import io.github.alirezajavan.pulsekit.demo.SettingsScreen
import io.github.alirezajavan.pulsekit.demo.SyncScreen
import io.github.alirezajavan.pulsekit.ui.BasePulseKitService
import io.github.alirezajavan.pulsekit.ui.permission.PermissionController
import io.github.alirezajavan.ui.theme.PulseKitTheme
import kotlinx.coroutines.launch

/**
 * Hosts every PulseKit feature area behind bottom navigation: Home (collection controls), Sensors
 * (live readings), Bluetooth (scan results), Sync (network-aware sync diagnostics/policy) and
 * Settings (permissions/config/erase). The screens themselves hold no library-specific knowledge
 * beyond what [PulseKit.dataSources] and the app's [PulseKitApplication] already expose.
 */
class MainActivity : ComponentActivity() {

    private val pulseKit: PulseKit by lazy { PulseKitApplication.from(this).pulseKit }
    private val app: PulseKitApplication by lazy { PulseKitApplication.from(this) }

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
                PulseKitApp(
                    pulseKit = pulseKit,
                    app = app,
                    permissionController = permissionController,
                    onStartSource = ::startCollecting,
                    onStartAll = ::startAllSupported,
                    onStopAll = ::stopCollecting,
                )
            }
        }
    }

    private fun startCollecting(sourceId: String) {
        BasePulseKitService.startCollection(this, PulseKitTrackingService::class, setOf(sourceId))
    }

    private fun startAllSupported() {
        val ids = pulseKit.dataSources.filter { it.isSupported }.map { it.id }.toSet()
        BasePulseKitService.startCollection(this, PulseKitTrackingService::class, ids)
    }

    private fun stopCollecting() {
        BasePulseKitService.stopCollection(this, PulseKitTrackingService::class)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PulseKitApp(
    pulseKit: PulseKit,
    app: PulseKitApplication,
    permissionController: PermissionController,
    onStartSource: (String) -> Unit,
    onStartAll: () -> Unit,
    onStopAll: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(PulseKitDestination.entries.find { it.route == currentRoute }?.label ?: "PulseKit")
                },
            )
        },
        bottomBar = {
            NavigationBar {
                PulseKitDestination.entries.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = PulseKitDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(PulseKitDestination.Home.route) {
                HomeScreen(
                    pulseKit = pulseKit,
                    permissionController = permissionController,
                    onStartSource = onStartSource,
                    onStartAll = onStartAll,
                    onStopAll = onStopAll,
                    onRecordManualEvent = {
                        pulseKit.recordEvent(SensorPayload.StepCount(steps = 1), type = "manual_ping")
                        Toast.makeText(context, "Recorded manual event", Toast.LENGTH_SHORT).show()
                    },
                    onReplayBatch = {
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
                            Toast.makeText(context, "Replayed ${replayed.size} events", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
            composable(PulseKitDestination.Sensors.route) {
                SensorsScreen(
                    pulseKit = pulseKit,
                    permissionController = permissionController,
                    onStartSource = onStartSource,
                )
            }
            composable(PulseKitDestination.Bluetooth.route) {
                BluetoothScreen(
                    pulseKit = pulseKit,
                    permissionController = permissionController,
                    onStartSource = onStartSource,
                )
            }
            composable(PulseKitDestination.Sync.route) {
                val syncStatus by app.syncStatus.collectAsState()
                val requireUnmetered by app.requireUnmeteredNetwork.collectAsState()
                SyncScreen(
                    syncStatus = syncStatus,
                    requireUnmeteredNetwork = requireUnmetered,
                    onRequireUnmeteredNetworkChange = { app.setRequireUnmeteredNetwork(it) },
                    networkMonitor = app.networkMonitor,
                )
            }
            composable(PulseKitDestination.History.route) {
                HistoryScreen(pulseKit = pulseKit)
            }
            composable(PulseKitDestination.Settings.route) {
                val syncConfig by app.syncConfig.collectAsState()
                SettingsScreen(
                    permissionController = permissionController,
                    locationConfig = app.locationConfig,
                    motionConfig = app.motionConfig,
                    bluetoothConfig = app.bluetoothConfig,
                    syncConfig = syncConfig,
                    onEraseAllData = {
                        coroutineScope.launch {
                            pulseKit.eraseAllData()
                            Toast.makeText(context, "Erased all stored data", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
    }
}
