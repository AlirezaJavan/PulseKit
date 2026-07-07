package io.github.alirezajavan.pulsekit.core

import io.github.alirezajavan.pulsekit.core.db.PulseKitDatabase
import io.github.alirezajavan.pulsekit.core.db.SensorEventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Public entry point for the PulseKit library.
 *
 * Consumers never talk to sensors directly: they build a [PulseKit] instance around a database
 * and one or more [DataSource]s (from `pulsekit-motion`, `pulsekit-location`, or a custom one),
 * then call [start]/[stop] — or [startSources]/[stopSources] to control each source
 * independently, e.g. starting only location once the user opts into it. PulseKit owns each
 * source's lifecycle (including [CollectionMode.Periodic] burst scheduling) and funnels its
 * output through [TrackingEngine] for batched, bounded persistence.
 *
 * [start]/[startSources]/[stop]/[stopSources] expect a single owner (on Android, typically the
 * app's `BasePulseKitService` subclass, which serializes them on one dispatcher); concurrent
 * calls from different threads are not synchronized.
 */
class PulseKit private constructor(
    private val engine: TrackingEngine,
    private val sources: List<RegisteredSource>,
    private val scope: CoroutineScope,
    private val logger: PulseKitLogger,
) {
    private class RegisteredSource(val dataSource: DataSource, val mode: CollectionMode)

    /**
     * Narrow view of the engine for `pulsekit-sync`; keeps sync decoupled from engine internals.
     */
    val syncSource: SyncSource get() = engine

    private val collectionJobs = mutableMapOf<String, Job>()
    private var quiescenceJob: Job? = null
    private val mutableActiveSourceIds = MutableStateFlow(emptySet<String>())

    /**
     * Every [DataSource] attached to this instance, e.g. to render one "collect X" control each.
     */
    val dataSources: List<DataSource> get() = sources.map { it.dataSource }

    /** Ids of every attached [DataSource]. */
    val availableSourceIds: Set<String> get() = sources.map { it.dataSource.id }.toSet()

    /**
     * Ids of the sources currently collecting (for a [CollectionMode.Periodic] source: currently
     * scheduled, whether mid-window or between windows). A source whose `start()` reported
     * failure — denied permission, missing hardware — leaves this set again immediately, so UI
     * bound to this flow reflects what is genuinely running, and survives the UI being recreated
     * as long as this instance (usually app-wide) lives.
     */
    val activeSourceIds: StateFlow<Set<String>> = mutableActiveSourceIds.asStateFlow()

    /**
     * Starts the engine and every attached [DataSource]. Safe to call again while already running.
     */
    fun start() {
        startSources(availableSourceIds)
    }

    /**
     * Starts collection for just the sources in [sourceIds] (by [DataSource.id]), leaving the
     * rest untouched — e.g. start "location" alone once location permissions are granted, then
     * add "bluetooth" later from a separate user action. Already-running sources are left alone,
     * unknown ids are logged and skipped, and the shared engine is started on first use.
     * Whether each source actually began collecting is observable via [activeSourceIds].
     */
    fun startSources(sourceIds: Set<String>) {
        val unknown = sourceIds - availableSourceIds
        if (unknown.isNotEmpty()) {
            logger.warn(TAG, "startSources: no data source attached with id(s) $unknown")
        }
        val toStart = sources.filter { registered ->
            registered.dataSource.id in sourceIds &&
                collectionJobs[registered.dataSource.id]?.isActive != true
        }
        if (toStart.isEmpty()) return
        engine.start()
        startQuiescenceOrchestration()

        for (registered in toStart) {
            val sourceId = registered.dataSource.id
            if (!registered.dataSource.isSupported) {
                logger.warn(
                    TAG,
                    "startSources: source \"$sourceId\" is not supported on this device",
                )
                continue
            }
            // Mark active before launching: the job may complete (failed start) on another
            // thread before this loop iteration even returns, and completion must observe the
            // "active" mark it is undoing.
            mutableActiveSourceIds.update {
                val next = it + sourceId
                logger.debug(TAG, "Active sources changed: $next")
                next
            }
            val job = scope.launch { runCollection(registered) }
            collectionJobs[sourceId] = job
            job.invokeOnCompletion {
                mutableActiveSourceIds.update {
                    val next = it - sourceId
                    logger.debug(TAG, "Active sources changed: $next")
                    next
                }
            }
        }
    }

    /**
     * Stops collection for just the sources in [sourceIds], returning once each is fully stopped.
     * The engine keeps running (it idles cheaply) so other active sources and one-off
     * [recordEvent] calls are unaffected; a full teardown is [stop].
     */
    suspend fun stopSources(sourceIds: Set<String>) {
        for (registered in sources) {
            val sourceId = registered.dataSource.id
            if (sourceId !in sourceIds) continue
            collectionJobs.remove(sourceId)?.let { job ->
                job.cancel()
                job.join()
            }
        }
    }

    /** Stops every attached [DataSource] and the engine's ingestion/prune loops. */
    suspend fun stop() {
        stopSources(availableSourceIds)
        engine.stop()
        quiescenceJob?.cancel()
        quiescenceJob = null
    }

    /** Reactive total event count, e.g. to render "N events queued" in UI. */
    fun observeEventCount(): Flow<Long> = engine.observeEventCount()

    /** Records a single one-off event outside the attached data sources. */
    fun recordEvent(payload: SensorPayload, type: String) {
        engine.logSensorEvent(payload, type)
    }

    /**
     * Persists a historical batch pulled from a platform buffer (e.g. iOS pedometer replay
     * after relaunch).
     */
    suspend fun recordEvents(events: List<SensorPayload>, type: String) {
        if (events.isEmpty()) return
        val entities = events.map { payload ->
            SensorEventLog(
                id = platformGenerateUuid(),
                sensorType = type,
                timestamp = platformCurrentTimeMillis(),
                payload = payload,
                syncStatus = SyncStatus.IDLE,
            )
        }
        engine.bulkInsertEvents(entities)
    }

    /**
     * Unconditionally erases every stored event, e.g. to fulfil a right-to-erasure (GDPR/CCPA)
     * request. Distinct from [PulseKitConfig]'s routine count-/age-based pruning, which only ever
     * removes the oldest rows to stay under a cap -- this removes everything, regardless of age
     * or sync status.
     */
    suspend fun eraseAllData() {
        engine.eraseAllData()
    }

    /**
     * Releases the coroutine scope backing this instance. Call once the instance is no longer
     * needed.
     */
    fun dispose() {
        scope.cancel()
    }

    private fun startQuiescenceOrchestration() {
        if (quiescenceJob?.isActive == true) return

        quiescenceJob = scope.launch {
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            activeSourceIds
                .flatMapLatest { ids ->
                    val activeFlows = sources
                        .filter { it.dataSource.id in ids }
                        .mapNotNull { it.dataSource.providesQuiescence }
                    
                    if (activeFlows.isEmpty()) {
                        flowOf(false)
                    } else {
                        combine(activeFlows) { states -> states.all { it } }
                    }
                }
                .distinctUntilChanged()
                .collect { isQuiescent ->
                    sources.forEach { it.dataSource.onQuiescenceChanged(isQuiescent) }
                }
        }
    }

    private suspend fun runCollection(registered: RegisteredSource) {
        when (val mode = registered.mode) {
            is CollectionMode.Continuous -> collectContinuously(registered.dataSource)
            is CollectionMode.Periodic -> collectPeriodically(registered.dataSource, mode)
        }
    }

    private suspend fun collectContinuously(source: DataSource) {
        if (!source.start()) {
            logger.warn(TAG, "Source \"${source.id}\" failed to start")
            return
        }
        logger.debug(TAG, "Source \"${source.id}\" started collecting")
        try {
            source.events().collect { payload -> engine.logSensorEvent(payload, source.id) }
        } finally {
            logger.debug(TAG, "Source \"${source.id}\" stopped collecting")
            // Runs on cancellation from stopSources() too; the source must still be released.
            withContext(NonCancellable) { source.stop() }
        }
    }

    private suspend fun collectPeriodically(source: DataSource, mode: CollectionMode.Periodic) {
        while (currentCoroutineContext().isActive) {
            val started = source.start()
            if (started) {
                logger.debug(TAG, "Source \"${source.id}\" started periodic burst")
                try {
                    withTimeoutOrNull(mode.windowMillis.milliseconds) {
                        source.events().collect { payload ->
                            engine.logSensorEvent(payload, source.id)
                        }
                    }
                } finally {
                    logger.debug(TAG, "Source \"${source.id}\" stopped periodic burst")
                    withContext(NonCancellable) { source.stop() }
                }
                delay((mode.intervalMillis - mode.windowMillis).milliseconds)
            } else {
                // Skipped cycle (permission denied, adapter off, ...): retry next interval.
                logger.warn(TAG, "Source \"${source.id}\" failed periodic burst start")
                delay(mode.intervalMillis.milliseconds)
            }
        }
    }

    class Builder(private val database: PulseKitDatabase) {
        private val sources = mutableListOf<RegisteredSource>()
        private var config = PulseKitConfig()
        private var logger: PulseKitLogger = NoOpPulseKitLogger
        private var scopeOverride: CoroutineScope? = null

        /**
         * Attaches [dataSource], collected per [mode]: [CollectionMode.Continuous] (default) or
         * [CollectionMode.Periodic] bursts.
         */
        fun addDataSource(
            dataSource: DataSource,
            mode: CollectionMode = CollectionMode.Continuous,
        ) = apply {
            require(sources.none { it.dataSource.id == dataSource.id }) {
                "a data source with id \"${dataSource.id}\" is already attached"
            }
            sources.add(RegisteredSource(dataSource, mode))
        }

        fun config(config: PulseKitConfig) = apply { this.config = config }

        /** Wire your own Timber/Crashlytics/os_log-backed [PulseKitLogger]; defaults to silent. */
        fun logger(logger: PulseKitLogger) = apply { this.logger = logger }

        /** Test seam: run collection/persistence on an injected (e.g. virtual-time) scope. */
        internal fun collectionScope(scope: CoroutineScope) = apply { scopeOverride = scope }

        fun build(): PulseKit {
            val scope = scopeOverride
                ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val engine = TrackingEngine(database, config, scope, logger)
            return PulseKit(engine, sources.toList(), scope, logger)
        }

        /**
         * Exposes the sources added so far to platform-specific validators (e.g. Android's setup
         * check).
         */
        fun dataSourcesSnapshot(): List<DataSource> = sources.map { it.dataSource }

        /**
         * Exposes the configured logger to platform-specific validators (e.g. Android's setup
         * check).
         */
        fun loggerSnapshot(): PulseKitLogger = logger
    }

    companion object {
        private const val TAG = "PulseKit"

        fun builder(database: PulseKitDatabase): Builder = Builder(database)
    }
}
