package io.github.alirezajavan.pulsekit.testing

import io.github.alirezajavan.pulsekit.core.PulseKitLogger

/**
 * A [PulseKitLogger] that captures all log events in memory for assertion in tests.
 */
class RecordingPulseKitLogger : PulseKitLogger {
    data class LogEvent(
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null,
    )

    enum class Level { DEBUG, WARN, ERROR }

    private val _events = mutableListOf<LogEvent>()
    val events: List<LogEvent> get() = _events

    override fun debug(tag: String, message: String) {
        _events.add(LogEvent(Level.DEBUG, tag, message))
    }

    override fun warn(tag: String, message: String, throwable: Throwable?) {
        _events.add(LogEvent(Level.WARN, tag, message, throwable))
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        _events.add(LogEvent(Level.ERROR, tag, message, throwable))
    }

    fun clear() {
        _events.clear()
    }
}
