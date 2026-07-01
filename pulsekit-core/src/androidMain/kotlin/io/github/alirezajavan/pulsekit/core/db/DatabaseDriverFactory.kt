package io.github.alirezajavan.pulsekit.core.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.alirezajavan.pulsekit.core.PlatformContext

internal actual class DatabaseDriverFactory actual constructor(
    private val context: PlatformContext,
) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(PulseKitDatabase.Schema, context, "pulsekit.db")
}
