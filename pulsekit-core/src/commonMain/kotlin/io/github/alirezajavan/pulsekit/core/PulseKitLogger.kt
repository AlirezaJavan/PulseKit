package io.github.alirezajavan.pulsekit.core

/**
 * Pluggable logging sink so PulseKit's internals (and `pulsekit-sync`) never hard-depend on
 * `android.util.Log` or any other platform-specific logging framework. Wire your own
 * Timber/Crashlytics/os_log-backed implementation via [PulseKit.Builder.logger] (and pass the
 * same instance into `SyncEngine`'s constructor, if used).
 */
interface PulseKitLogger {
    fun debug(tag: String, message: String)

    fun warn(tag: String, message: String, throwable: Throwable? = null)

    fun error(tag: String, message: String, throwable: Throwable? = null)
}

/** Default no-op [PulseKitLogger]: PulseKit stays silent unless a consumer opts into logging. */
object NoOpPulseKitLogger : PulseKitLogger {
    override fun debug(tag: String, message: String) = Unit

    override fun warn(tag: String, message: String, throwable: Throwable?) = Unit

    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}
