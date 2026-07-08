package io.github.alirezajavan.pulsekit.testing

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.github.alirezajavan.pulsekit.core.db.PulseKitDatabase
import io.github.alirezajavan.pulsekit.core.db.createPulseKitDatabase

actual fun inMemoryPulseKitDatabase(): PulseKitDatabase {
    // Passing null or empty string as name to NativeSqliteDriver results in an in-memory database.
    val driver = NativeSqliteDriver(PulseKitDatabase.Schema, "")
    return createPulseKitDatabase(driver)
}
