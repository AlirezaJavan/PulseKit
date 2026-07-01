package io.github.alirezajavan.pulsekit.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CollectionModeTest {
    @Test
    fun periodicRejectsAWindowThatIsNotSmallerThanTheInterval() {
        assertFailsWith<IllegalArgumentException> {
            CollectionMode.Periodic(intervalMillis = 1_000L, windowMillis = 1_000L)
        }
        assertFailsWith<IllegalArgumentException> {
            CollectionMode.Periodic(intervalMillis = 500L, windowMillis = 1_000L)
        }
    }

    @Test
    fun periodicRejectsANonPositiveWindow() {
        assertFailsWith<IllegalArgumentException> {
            CollectionMode.Periodic(intervalMillis = 1_000L, windowMillis = 0L)
        }
    }

    @Test
    fun periodicAcceptsAWindowSmallerThanTheInterval() {
        val mode = CollectionMode.Periodic(intervalMillis = 1_000L, windowMillis = 200L)
        assertEquals(1_000L, mode.intervalMillis)
        assertEquals(200L, mode.windowMillis)
    }
}
