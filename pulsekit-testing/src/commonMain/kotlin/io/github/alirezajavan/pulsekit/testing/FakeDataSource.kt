package io.github.alirezajavan.pulsekit.testing

import io.github.alirezajavan.pulsekit.core.DataSource
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.permission.Permission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A scriptable [DataSource] for testing [PulseKit] orchestration without real sensors.
 */
class FakeDataSource(
    override val id: String = "fake",
    override val isSupported: Boolean = true,
    override val requiredPermissions: List<Permission> = emptyList(),
) : DataSource {
    private val _events = MutableSharedFlow<SensorPayload>()
    private var startCount = 0
    private var stopCount = 0
    private var isStarted = false

    var startResult = true

    override fun events(): Flow<SensorPayload> = _events.asSharedFlow()

    override suspend fun start(): Boolean {
        startCount++
        if (startResult) {
            isStarted = true
        }
        return startResult
    }

    override suspend fun stop() {
        stopCount++
        isStarted = false
    }

    suspend fun emit(payload: SensorPayload) {
        _events.emit(payload)
    }

    fun getStartCount() = startCount
    fun getStopCount() = stopCount
    fun isRunning() = isStarted
}
