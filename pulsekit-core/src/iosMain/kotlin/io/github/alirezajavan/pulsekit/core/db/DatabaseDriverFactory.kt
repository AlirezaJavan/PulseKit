package io.github.alirezajavan.pulsekit.core.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.github.alirezajavan.pulsekit.core.PlatformContext

internal actual class DatabaseDriverFactory actual constructor(
    @Suppress("UNUSED_PARAMETER") context: PlatformContext,
) {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(PulseKitDatabase.Schema, "pulsekit.db")
}
