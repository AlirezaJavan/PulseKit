package io.github.alirezajavan.pulsekit.core

import io.github.alirezajavan.pulsekit.core.permission.Permission
import kotlinx.coroutines.flow.Flow

/**
 * Contract implemented by every pluggable data source (motion, location, etc.).
 *
 * A [DataSource] is only responsible for producing [SensorPayload] values while started, and for
 * *describing itself*: its [requiredPermissions]/[optionalPermissions] are the single source of
 * truth consumed by everything above it — Android's `validateAndroidSetup` derives the manifest
 * declarations and foreground-service types to check from them, `BasePulseKitService` derives its
 * runtime foreground-service type from them, and `pulsekit-ui`'s
 * `rememberDataSourcePermissionState` derives the staged permission-request flow from them. A new
 * source that fills these in gets validation, service typing and permission UI for free, with no
 * per-source registration anywhere else.
 *
 * Persistence, batching, pruning and sync are entirely owned by [PulseKit] / [TrackingEngine],
 * so implementations should stay as thin platform wrappers around the underlying sensor API.
 */
interface DataSource {
    /** Stable identifier stored as `sensorType` in the event log, e.g. "motion", "location". */
    val id: String

    /** Human-readable name for UI ("GPS", "Bluetooth"). Defaults to [id]. */
    val displayName: String get() = id

    /**
     * Permissions that must be granted before [start] can produce anything — [start] returns
     * `false` without them. List order is the staged request order where the OS mandates one
     * (foreground location before background location).
     */
    val requiredPermissions: List<Permission> get() = emptyList()

    /**
     * Permissions that improve collection (e.g. background location keeps GPS flowing while the
     * app is backgrounded) but whose denial must not block [start].
     */
    val optionalPermissions: List<Permission> get() = emptyList()

    /** `true` if this device's hardware supports this source. */
    val isSupported: Boolean get() = true

    /**
     * Starts producing values into [events]. Must be safe to call again if already started.
     *
     * @return `true` if collection actually began; `false` if a precondition was missing — a
     * denied permission in [requiredPermissions], absent hardware, or a disabled adapter/provider.
     * Implementations log the specific reason through their [PulseKitLogger].
     */
    suspend fun start(): Boolean

    /** Stops producing values and releases underlying platform resources (sensors, listeners). */
    suspend fun stop()

    /** Flow of payloads emitted by this source while started. Empty/idle once [stop] is called. */
    fun events(): Flow<SensorPayload>

    /**
     * Optional hook for adjusting sampling rate based on device movement.
     * @param isQuiescent `true` if the device has been stationary for some time; `false` otherwise.
     */
    fun onQuiescenceChanged(isQuiescent: Boolean) {}

    /**
     * Optional flow that emits `true` when this source detects the device has become stationary,
     * and `false` when it starts moving again. [PulseKit] observes this to notify other sources.
     */
    val providesQuiescence: Flow<Boolean>? get() = null
}
