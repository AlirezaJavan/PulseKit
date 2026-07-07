package io.github.alirezajavan.pulsekit.motion

import io.github.alirezajavan.pulsekit.core.MotionSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MotionQuiescenceDetectorTest {
    @Test
    fun detectsTransitionFromMovingToQuiescent() {
        val detector = MotionQuiescenceDetector(
            varianceThreshold = 0.1f,
            windowSize = 10,
            debounceWindows = 2,
        )

        val quiescentSample = MotionSample(0, x = 0f, y = 0f, z = 0f)

        // Window 1: Moving (alternate 0 and 1 to create variance of 0.25)
        repeat(5) {
            assertNull(detector.onSample(MotionSample(0, 0f, 0f, 0f)))
            assertNull(detector.onSample(MotionSample(0, 1f, 0f, 0f)))
        } // Evaluation 1: Moving (variance 0.25 > 0.1), state stays false

        // Window 2: Quiescent
        repeat(9) { assertNull(detector.onSample(quiescentSample)) }
        assertNull(detector.onSample(quiescentSample)) // Evaluation 2: Quiescent, agreement = 1

        // Window 3: Quiescent
        repeat(9) { assertNull(detector.onSample(quiescentSample)) }
        assertEquals(true, detector.onSample(quiescentSample)) // Evaluation 3: Quiescent, agreement = 2 -> FLIP
    }

    @Test
    fun detectsTransitionFromQuiescentToMoving() {
        val detector = MotionQuiescenceDetector(
            varianceThreshold = 0.1f,
            windowSize = 10,
            debounceWindows = 2,
        )
        val quiescentSample = MotionSample(0, x = 0f, y = 0f, z = 0f)

        // Initialize as quiescent (requires 2 windows to flip from initial false to true)
        repeat(20) { detector.onSample(quiescentSample) }
        
        // Window 3: Moving (alternate 0 and 1)
        repeat(5) {
            assertNull(detector.onSample(MotionSample(0, 0f, 0f, 0f)))
            assertNull(detector.onSample(MotionSample(0, 1f, 0f, 0f)))
        } // Evaluation: Moving, agreement = 1

        // Window 4: Moving
        repeat(4) {
            assertNull(detector.onSample(MotionSample(0, 0f, 0f, 0f)))
            assertNull(detector.onSample(MotionSample(0, 1f, 0f, 0f)))
        }
        assertNull(detector.onSample(MotionSample(0, 0f, 0f, 0f)))
        assertEquals(false, detector.onSample(MotionSample(0, 1f, 0f, 0f))) // Evaluation: Moving, agreement = 2 -> FLIP
    }
}
