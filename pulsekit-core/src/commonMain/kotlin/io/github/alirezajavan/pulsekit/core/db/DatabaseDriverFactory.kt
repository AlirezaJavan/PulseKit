package io.github.alirezajavan.pulsekit.core.db

import app.cash.sqldelight.db.SqlDriver
import io.github.alirezajavan.pulsekit.core.PlatformContext

/** Creates the platform-specific [SqlDriver] backing [PulseKitDatabase]. */
internal expect class DatabaseDriverFactory(context: PlatformContext) {
    fun createDriver(): SqlDriver
}
