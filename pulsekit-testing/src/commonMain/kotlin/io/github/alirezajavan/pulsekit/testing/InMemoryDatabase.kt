package io.github.alirezajavan.pulsekit.testing

import io.github.alirezajavan.pulsekit.core.db.PulseKitDatabase

/**
 * Returns a fresh, in-memory [PulseKitDatabase] for testing.
 * Each call returns a completely isolated database.
 */
expect fun inMemoryPulseKitDatabase(): PulseKitDatabase
