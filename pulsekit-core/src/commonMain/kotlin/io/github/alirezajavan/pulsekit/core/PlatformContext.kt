package io.github.alirezajavan.pulsekit.core

/**
 * Opaque platform handle threaded through to [DataSource] implementations that need one
 * (e.g. Android's `Context` for `SensorManager`/`LocationManager`). On iOS there is no
 * equivalent object, so it carries no data there.
 */
expect abstract class PlatformContext
