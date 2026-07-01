package io.github.alirezajavan.pulsekit

import android.app.Application
import io.github.alirezajavan.pulsekit.bluetooth.BluetoothDataSource
import io.github.alirezajavan.pulsekit.core.CollectionMode
import io.github.alirezajavan.pulsekit.core.PulseKit
import io.github.alirezajavan.pulsekit.core.db.createPulseKitDatabase
import io.github.alirezajavan.pulsekit.ui.setup.validateAndroidSetup
import io.github.alirezajavan.pulsekit.location.LocationDataSource
import io.github.alirezajavan.pulsekit.motion.MotionDataSource
import io.github.alirezajavan.pulsekit.motion.StepCounterDataSource
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import io.github.alirezajavan.pulsekit.sync.JsonHttpSyncUploader
import io.github.alirezajavan.pulsekit.sync.SyncEngine
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Owns the single [PulseKit] instance for the demo app so both the launcher [MainActivity] and
 * [PulseKitTrackingService] observe/drive the same engine and data sources. Also owns [syncEngine],
 * started/stopped alongside [PulseKit] itself since both need to run for as long as the app
 * process is alive (the process's lifetime is what the foreground service protects, not the sync
 * loop specifically).
 */
class PulseKitApplication : Application() {

    lateinit var pulseKit: PulseKit
        private set

    private lateinit var syncEngine: SyncEngine
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        val database = createPulseKitDatabase(applicationContext)
        val logger = AndroidLogcatPulseKitLogger

        pulseKit = PulseKit.builder(database)
            .logger(logger)
            .addDataSource(MotionDataSource(applicationContext, logger = logger))
            .addDataSource(LocationDataSource(applicationContext, logger = logger))
            // Periodic: scan BLE for 30s every 5 minutes instead of scanning nonstop, since
            // continuous BLE discovery is comparatively power-hungry.
            .addDataSource(
                BluetoothDataSource(applicationContext, logger = logger),
                mode = CollectionMode.Periodic(
                    intervalMillis = 5.minutes.inWholeMilliseconds,
                    windowMillis = 30.seconds.inWholeMilliseconds,
                ),
            )
            .addDataSource(StepCounterDataSource(applicationContext, logger = logger))
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
        val uploader = JsonHttpSyncUploader(
            httpClient = HttpClient(),
            endpointUrl = "https://example.invalid/pulsekit/events",
        )
        syncEngine = SyncEngine(pulseKit.syncSource, uploader, logger = logger)
        syncEngine.start(applicationScope)
    }

    companion object {
        fun from(context: android.content.Context): PulseKitApplication =
            context.applicationContext as PulseKitApplication
    }
}
