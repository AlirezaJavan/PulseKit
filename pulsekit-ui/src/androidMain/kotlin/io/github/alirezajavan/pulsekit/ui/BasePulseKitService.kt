package io.github.alirezajavan.pulsekit.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.alirezajavan.pulsekit.core.PulseKit
import io.github.alirezajavan.pulsekit.core.SyncStatusSnapshot
import io.github.alirezajavan.pulsekit.ui.permission.foregroundServiceType
import io.github.alirezajavan.pulsekit.ui.permission.isRuntimeGranted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

/**
 * Foreground service for continuous background tracking. Subclasses only need to provide the
 * app's [pulseKit] instance.
 */
abstract class BasePulseKitService : Service() {
    /** The app-wide [PulseKit] instance this service drives. */
    protected abstract val pulseKit: PulseKit

    /**
     * Optional hook to provide the app's sync state (if using `:pulsekit-sync`) to the
     * foreground notification. Host apps should map their `SyncEngine.observeState()` to this
     * property.
     */
    protected open val syncState: StateFlow<SyncStatusSnapshot?> = MutableStateFlow(null)

    private var lastObservedEventCount: Long = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private val renewalHandler = Handler(Looper.getMainLooper())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val collectionDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val collectionScope = CoroutineScope(collectionDispatcher + Job())

    private val renewWakeLock = object : Runnable {
        override fun run() {
            acquireWakeLock()
            renewalHandler.postDelayed(this, WAKE_LOCK_RENEWAL_INTERVAL_MILLIS)
        }
    }

    open val notificationChannelId: String = DEFAULT_NOTIFICATION_CHANNEL_ID
    open val notificationChannelName: CharSequence = DEFAULT_NOTIFICATION_CHANNEL_NAME
    open val notificationContentTitle: CharSequence = DEFAULT_NOTIFICATION_TITLE
    open val notificationContentText: CharSequence = DEFAULT_NOTIFICATION_TEXT
    open val notificationSmallIcon: Int = android.R.drawable.stat_notify_sync
    open val notificationId: Int = DEFAULT_NOTIFICATION_ID

    open fun createForegroundNotification(): Notification {
        val channel = NotificationChannel(
            notificationChannelId,
            notificationChannelName,
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val sync = syncState.value
        val syncStatusText = when {
            sync == null -> ""
            sync.isSyncing -> " · Syncing..."
            sync.isWaitingForNetwork -> " · Waiting for network"
            sync.lastError != null -> " · Sync error"
            else -> ""
        }
        val contentText = "$notificationContentText · $lastObservedEventCount events$syncStatusText"

        return NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle(notificationContentTitle)
            .setContentText(contentText)
            .setSmallIcon(notificationSmallIcon)
            .setOngoing(true)
            .build()
    }

    open fun foregroundServiceTypes(): Int = permittedForegroundServiceTypes()

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        updateForegroundState()
        acquireWakeLock()
        renewalHandler.postDelayed(renewWakeLock, WAKE_LOCK_RENEWAL_INTERVAL_MILLIS)

        collectionScope.launch {
            combine(
                pulseKit.observeEventCount(),
                syncState,
            ) { count, sync -> count to sync }
                .distinctUntilChanged()
                .sample(NOTIFICATION_REFRESH_DEBOUNCE_MILLIS.milliseconds)
                .onEach { (count, _) ->
                    lastObservedEventCount = count
                    updateForegroundState()
                }
                .collect {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateForegroundState()
        val requestedSourceIds = intent?.getStringArrayExtra(EXTRA_SOURCE_IDS)?.toSet()
        if (requestedSourceIds != null) {
            collectionScope.launch {
                pulseKit.startSources(requestedSourceIds)
            }
        }
        return START_NOT_STICKY
    }

    private fun updateForegroundState() {
        ServiceCompat.startForeground(
            this,
            notificationId,
            createForegroundNotification(),
            foregroundServiceTypes(),
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runBlocking {
            withContext(collectionDispatcher) { pulseKit.stop() }
        }
        collectionScope.cancel()
        renewalHandler.removeCallbacks(renewWakeLock)
        releaseWakeLock()
        super.onDestroy()
    }

    private fun permittedForegroundServiceTypes(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        val declared = manifestDeclaredServiceTypes()
        var permitted = 0
        for (dataSource in pulseKit.dataSources) {
            val permissions = dataSource.requiredPermissions + dataSource.optionalPermissions
            for (permission in permissions) {
                val type = permission.foregroundServiceType()
                if (type != 0 && (declared and type == type) && permission.isRuntimeGranted(this)) {
                    permitted = permitted or type
                }
            }
        }

        // Android 14+ requires a non-zero type if the service is foreground.
        // Fall back to SPECIAL_USE if declared in manifest, or any other declared type
        // to avoid an immediate crash if no sensitive permissions are granted yet.
        if (permitted == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permitted = if (declared and ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE != 0) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                // If we have any declared types, use the first one available just to stay alive
                declared
            }
        }
        return permitted
    }

    private fun manifestDeclaredServiceTypes(): Int = try {
        val component = ComponentName(this, javaClass)
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getServiceInfo(component, PackageManager.ComponentInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getServiceInfo(component, 0)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.foregroundServiceType
        } else {
            0
        }
    } catch (e: PackageManager.NameNotFoundException) {
        0
    }

    private fun acquireWakeLock() {
        releaseWakeLock()
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG,
        ).apply {
            acquire(WAKE_LOCK_SAFETY_TIMEOUT_MILLIS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    companion object {
        const val EXTRA_SOURCE_IDS = "io.github.alirezajavan.pulsekit.ui.SOURCE_IDS"

        private const val DEFAULT_NOTIFICATION_CHANNEL_ID = "pulsekit_tracking"
        private const val DEFAULT_NOTIFICATION_CHANNEL_NAME = "Data collection"
        private const val DEFAULT_NOTIFICATION_TITLE = "Collecting sensor data"
        private const val DEFAULT_NOTIFICATION_TEXT = "Sensor data collection is running"
        private const val DEFAULT_NOTIFICATION_ID = 1001
        private const val WAKE_LOCK_TAG = "PulseKit::ExecutionLock"

        private const val WAKE_LOCK_SAFETY_TIMEOUT_MILLIS = 60 * 60 * 1000L // 1h
        private const val WAKE_LOCK_RENEWAL_INTERVAL_MILLIS = 20 * 60 * 1000L // 20min

        private const val NOTIFICATION_REFRESH_DEBOUNCE_MILLIS = 5_000L

        fun startCollection(
            context: Context,
            serviceClass: KClass<out BasePulseKitService>,
            sourceIds: Set<String>? = null,
        ) {
            val intent = Intent(context, serviceClass.java)
            if (sourceIds != null) {
                intent.putExtra(EXTRA_SOURCE_IDS, sourceIds.toTypedArray())
            }
            context.startForegroundService(intent)
        }

        fun stopCollection(context: Context, serviceClass: KClass<out BasePulseKitService>) {
            context.stopService(Intent(context, serviceClass.java))
        }
    }
}
