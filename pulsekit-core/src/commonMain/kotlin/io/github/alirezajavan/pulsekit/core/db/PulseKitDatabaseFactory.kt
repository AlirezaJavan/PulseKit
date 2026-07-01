package io.github.alirezajavan.pulsekit.core.db

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import io.github.alirezajavan.pulsekit.core.PlatformContext
import io.github.alirezajavan.pulsekit.core.SensorPayload
import io.github.alirezajavan.pulsekit.core.SensorPayloadMapper

private val payloadAdapter = object : ColumnAdapter<SensorPayload, String> {
    override fun decode(databaseValue: String): SensorPayload =
        SensorPayloadMapper.fromJson(databaseValue)

    override fun encode(value: SensorPayload): String = SensorPayloadMapper.toJson(value)
}

/**
 * Escape hatch for a custom [SqlDriver] -- most apps should use [createPulseKitDatabase] (context
 * overload) instead. This is PulseKit's way of not imposing at-rest encryption on every consumer
 * (raw location/motion data is sensitive, but SQLCipher is a heavyweight native dependency, and
 * unlike Android there's no turnkey SQLDelight+SQLCipher story on iOS without custom cinterop
 * against SQLCipher's own build), while still letting an app that needs GDPR/CCPA-grade at-rest
 * encryption supply one: e.g. wrap `AndroidSqliteDriver` with SQLCipher-for-Android's
 * `net.zetetic:sqlcipher-android` `SupportOpenHelperFactory` and pass the result here instead of
 * calling the context overload.
 */
fun createPulseKitDatabase(driver: SqlDriver): PulseKitDatabase =
    PulseKitDatabase(
        driver = driver,
        SensorEventLogAdapter = SensorEventLog.Adapter(
            payloadAdapter = payloadAdapter,
            syncStatusAdapter = EnumColumnAdapter(),
        ),
    )

/** Convenience entry point: builds the platform driver and the adapted [PulseKitDatabase] in one call. */
fun createPulseKitDatabase(context: PlatformContext): PulseKitDatabase =
    createPulseKitDatabase(DatabaseDriverFactory(context).createDriver())
