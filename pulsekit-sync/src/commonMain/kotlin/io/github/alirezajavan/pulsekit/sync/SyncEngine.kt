package io.github.alirezajavan.pulsekit.sync

import io.github.alirezajavan.pulsekit.core.NoOpPulseKitLogger
import io.github.alirezajavan.pulsekit.core.PulseKitLogger
import io.github.alirezajavan.pulsekit.core.SyncSource
import io.github.alirezajavan.pulsekit.core.platformCurrentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Uploads locally buffered events to a backend as a transactional queue, entirely decoupled
 * from collection or UI. Chunk lifecycle: claim -> [SyncUploader.upload] -> delete on success /
 * revert-and-backoff on failure, so a prolonged offline period never loses data and never
 * hammers the network once it comes back. The actual wire contract with the backend is owned
 * entirely by the injected [uploader], not by this engine.
 */
class SyncEngine(
    private val syncSource: SyncSource,
    private val uploader: SyncUploader,
    private val config: SyncConfig = SyncConfig(),
    private val logger: PulseKitLogger = NoOpPulseKitLogger,
    private val networkMonitor: NetworkTypeProvider? = null,
) {
    private var loopJob: Job? = null
    private var hasLoggedNetworkMonitorMissing = false

    private val _state = MutableStateFlow(SyncState())

    /** Observable sync status -- last success time, current error, consecutive failure streak. */
    fun observeState(): StateFlow<SyncState> = _state.asStateFlow()

    fun start(scope: CoroutineScope) {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch { runSyncLoop() }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    private suspend fun CoroutineScope.runSyncLoop() {
        var backoff = config.initialBackoffMillis
        while (isActive) {
            if (config.requireUnmeteredNetwork) {
                if (networkMonitor == null) {
                    if (!hasLoggedNetworkMonitorMissing) {
                        logger.warn(
                            TAG,
                            "SyncConfig requires unmetered network but no NetworkMonitor " +
                                "provided. Sync blocked.",
                        )
                        hasLoggedNetworkMonitorMissing = true
                    }
                    _state.value = _state.value.copy(isSyncing = false, isWaitingForNetwork = true)
                    delay(config.idlePollIntervalMillis.milliseconds)
                    continue
                } else if (networkMonitor.currentNetworkType() != NetworkType.UNMETERED) {
                    _state.value = _state.value.copy(isSyncing = false, isWaitingForNetwork = true)
                    delay(config.idlePollIntervalMillis.milliseconds)
                    continue
                }
            }

            val batch = syncSource.claimPendingBatch(config.batchSize)
            if (batch.isEmpty()) {
                _state.value = _state.value.copy(isSyncing = false, isWaitingForNetwork = false)
                delay(config.idlePollIntervalMillis.milliseconds)
                continue
            }

            _state.value = _state.value.copy(isSyncing = true, isWaitingForNetwork = false)
            val result = runCatching { uploader.upload(batch) }
            val uploaded = result.getOrDefault(false)
            if (uploaded) {
                syncSource.markUploaded(batch.map { it.id })
                backoff = config.initialBackoffMillis
                _state.value = SyncState(
                    isSyncing = false,
                    lastSuccessTimestampMillis = platformCurrentTimeMillis(),
                    lastError = null,
                    consecutiveFailures = 0,
                )
            } else {
                syncSource.markFailed(batch.map { it.id })
                val message = result.exceptionOrNull()?.message ?: "uploader returned false"
                logger.warn(
                    TAG,
                    "Batch upload failed (${batch.size} events): $message",
                    result.exceptionOrNull(),
                )
                _state.value = _state.value.copy(
                    isSyncing = false,
                    lastError = message,
                    consecutiveFailures = _state.value.consecutiveFailures + 1,
                )
                delay(backoff.milliseconds)
                backoff = (backoff * 2).coerceAtMost(config.maxBackoffMillis)
            }
        }
    }

    private companion object {
        const val TAG = "SyncEngine"
    }
}
