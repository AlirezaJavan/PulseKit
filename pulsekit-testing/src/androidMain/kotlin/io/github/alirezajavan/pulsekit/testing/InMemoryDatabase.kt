package io.github.alirezajavan.pulsekit.testing

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.alirezajavan.pulsekit.core.db.PulseKitDatabase
import io.github.alirezajavan.pulsekit.core.db.createPulseKitDatabase

actual fun inMemoryPulseKitDatabase(): PulseKitDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    PulseKitDatabase.Schema.create(driver)
    return createPulseKitDatabase(driver)
}
