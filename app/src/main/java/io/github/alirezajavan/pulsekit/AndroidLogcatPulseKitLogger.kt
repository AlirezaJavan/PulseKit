package io.github.alirezajavan.pulsekit

import android.util.Log
import io.github.alirezajavan.pulsekit.core.PulseKitLogger

/** Demo [PulseKitLogger] backed by `android.util.Log`; real apps would wire Timber/Crashlytics/etc. */
object AndroidLogcatPulseKitLogger : PulseKitLogger {
    override fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun warn(tag: String, message: String, throwable: Throwable?) {
        Log.w(tag, message, throwable)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }
}
