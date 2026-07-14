package io.github.alirezajavan.pulsekit

import android.app.Application
import io.github.alirezajavan.pulsekit.bluetooth.BluetoothConfig
import io.github.alirezajavan.pulsekit.bluetooth.BluetoothDataSource
import io.github.alirezajavan.pulsekit.core.CollectionMode
import io.github.alirezajavan.pulsekit.core.PulseKit
import io.github.alirezajavan.pulsekit.core.PulseKitLogger
import io.github.alirezajavan.pulsekit.core.SyncStatusSnapshot
import io.github.alirezajavan.pulsekit.core.db.createPulseKitDatabase
import io.github.alirezajavan.pulsekit.core.processor.LocationPrecisionProcessor
import io.github.alirezajavan.pulsekit.location.LocationConfig
import io.github.alirezajavan.pulsekit.location.LocationDataSource
import io.github.alirezajavan.pulsekit.motion.MotionConfig
import io.github.alirezajavan.pulsekit.motion.MotionDataSource
import io.github.alirezajavan.pulsekit.motion.StepCounterDataSource
import io.github.alirezajavan.pulsekit.sync.JsonHttpSyncUploader
import io.github.alirezajavan.pulsekit.sync.NetworkMonitor
import io.github.alirezajavan.pulsekit.sync.SyncConfig
import io.github.alirezajavan.pulsekit.sync.SyncEngine
import io.github.alirezajavan.pulsekit.ui.setup.validateAndroidSetup
import io.ktor.client.HttpClient
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Owns the single [PulseKit] instance for the demo app so both [MainActivity] (and its screens)
 * and [PulseKitTrackingService] observe/drive the same engine and data sources. Also owns the
 * [SyncEngine], started/stopped alongside [PulseKit] itself since both need to run for as long as
 * the app process is alive (the process's lifetime is what the foreground service protects, not
 * the sync loop specifically).
 *
 * Tunables for every data source are kept as named `val`s (not just inline constructor args) so
 * the demo's Settings screen can display the real, live configuration instead of a hardcoded copy
 * that could drift from it.
 */
class PulseKitApplication : Application() {

    lateinit var pulseKit: PulseKit
        private set

    val locationConfig = LocationConfig()
    val motionConfig = MotionConfig()
    val bluetoothConfig = BluetoothConfig()

    lateinit var networkMonitor: NetworkMonitor
        private set

    private lateinit var uploader: JsonHttpSyncUploader
    private val logger: PulseKitLogger = AndroidLogcatPulseKitLogger

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val mutableRequireUnmeteredNetwork = MutableStateFlow(false)

    /** Current Wi-Fi-only sync policy; toggled from the Sync screen. */
    val requireUnmeteredNetwork: StateFlow<Boolean> = mutableRequireUnmeteredNetwork.asStateFlow()

    private val mutableCoarseLocationEnabled = MutableStateFlow(true)

    /** Whether location coordinates are rounded for privacy; toggled from Settings. */
    val coarseLocationEnabled: StateFlow<Boolean> = mutableCoarseLocationEnabled.asStateFlow()

    private val mutableSyncEngine = MutableStateFlow<SyncEngine?>(null)
    private val mutableSyncConfig = MutableStateFlow(SyncConfig())

    /** Follows whichever [SyncEngine] is currently active, so UI can survive a policy swap. */
    val syncEngineFlow: StateFlow<SyncEngine?> = mutableSyncEngine.asStateFlow()

    /** The [SyncConfig] the currently-active [SyncEngine] was built with. */
    val syncConfig: StateFlow<SyncConfig> = mutableSyncConfig.asStateFlow()

    /**
     * Sync status that survives [setRequireUnmeteredNetwork] swapping the underlying [SyncEngine]
     * out from under it -- both the Sync screen and [PulseKitTrackingService]'s notification read
     * this instead of snapshotting one engine's `observeState()` directly.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val syncStatus: StateFlow<SyncStatusSnapshot?> = mutableSyncEngine
        .flatMapLatest { engine -> engine?.observeState() ?: flowOf(null) }
        .stateIn(applicationScope, SharingStarted.Eagerly, null)

    override fun onCreate() {
        super.onCreate()

        val database = createPulseKitDatabase(applicationContext)

        pulseKit = PulseKit.builder(database)
            .logger(logger)
            .addDataSource(MotionDataSource(applicationContext, motionConfig, logger))
            .addDataSource(LocationDataSource(applicationContext, locationConfig, logger))
            // Periodic: scan BLE for 30s every 5 minutes instead of scanning nonstop, since
            // continuous BLE discovery is comparatively power-hungry.
            .addDataSource(
                BluetoothDataSource(applicationContext, bluetoothConfig, logger),
                mode = CollectionMode.Periodic(
                    intervalMillis = 5.minutes.inWholeMilliseconds,
                    windowMillis = 30.seconds.inWholeMilliseconds,
                ),
            )
            .addDataSource(StepCounterDataSource(applicationContext, logger = logger))
            .addEventProcessor(
                LocationPrecisionProcessor(
                    decimalPlacesProvider = { if (mutableCoarseLocationEnabled.value) 3 else null },
                ),
            )
            // Fails fast here, at process startup, with a message naming every missing manifest
            // permission/service/receiver declaration -- instead of a mismatched manifest
            // surfacing later as an opaque SecurityException from startForegroundService().
            .validateAndroidSetup(
                context = applicationContext,
                serviceClass = PulseKitTrackingService::class,
                bootReceiverClass = PulseKitBootReceiverImpl::class,
            )
            .build()

        // Placeholder endpoint -- integrating apps should replace this with their own backend
        // URL, or supply their own SyncUploader entirely instead of JsonHttpSyncUploader if their
        // backend expects a different wire contract.
        uploader = JsonHttpSyncUploader(
            httpClient = HttpClient(),
            endpointUrl = "https://example.invalid/pulsekit/events",
        )
        networkMonitor = NetworkMonitor(applicationContext)

        startSyncEngine()
    }

    /**
     * Toggles the network-aware sync policy (Phase 7): [SyncConfig] is immutable per [SyncEngine]
     * instance, so honoring a live policy change means stopping the current engine and starting a
     * fresh one with the new [SyncConfig] rather than mutating one in place. [syncEngineFlow]
     * (and [syncStatus]) let observers follow the swap instead of holding a stale reference.
     */
    fun setRequireUnmeteredNetwork(require: Boolean) {
        if (require == mutableRequireUnmeteredNetwork.value) return
        mutableRequireUnmeteredNetwork.value = require
        mutableSyncEngine.value?.stop()
        startSyncEngine()
    }

    fun setCoarseLocationEnabled(enabled: Boolean) {
        mutableCoarseLocationEnabled.value = enabled
    }

    private fun startSyncEngine() {
        val config = SyncConfig(requireUnmeteredNetwork = mutableRequireUnmeteredNetwork.value)
        val engine = SyncEngine(
            syncSource = pulseKit.syncSource,
            uploader = uploader,
            config = config,
            logger = logger,
            networkMonitor = networkMonitor,
        )
        engine.start(applicationScope)
        mutableSyncEngine.value = engine
        mutableSyncConfig.value = config
    }

    // Only invoked by the OS in the emulator, never on a real device -- but it's the correct
    // symmetric teardown for the pulseKit instance built in onCreate().
    override fun onTerminate() {
        pulseKit.dispose()
        super.onTerminate()
    }

    companion object {
        fun from(context: android.content.Context): PulseKitApplication =
            context.applicationContext as PulseKitApplication
    }
}
