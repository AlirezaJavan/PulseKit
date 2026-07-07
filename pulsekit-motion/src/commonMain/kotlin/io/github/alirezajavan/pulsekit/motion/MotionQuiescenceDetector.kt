package io.github.alirezajavan.pulsekit.motion

import io.github.alirezajavan.pulsekit.core.MotionSample
import kotlin.math.sqrt

/**
 * Pure logic component that evaluates a stream of [MotionSample]s to determine if the device is
 * stationary (quiescent) or in motion. Uses a rolling variance of acceleration magnitude.
 *
 * @param varianceThreshold Acceleration variance (m/s²)^2 below which a window is considered
 * quiescent.
 * @param windowSize Number of samples to evaluate for each decision.
 * @param debounceWindows Number of consecutive windows that must agree before flipping the
 * resulting state, to prevent thrashing at the threshold boundary.
 */
class MotionQuiescenceDetector(
    private val varianceThreshold: Float = 0.01f,
    private val windowSize: Int = 100,
    private val debounceWindows: Int = 3,
) {
    private val buffer = FloatArray(windowSize)
    private var bufferIndex = 0

    private var currentQuiescentState = false
    private var consecutiveAgreements = 0

    /**
     * Feed a new sample into the detector.
     * @return The updated "is quiescent" state, or `null` if the state didn't change this sample.
     */
    fun onSample(sample: MotionSample): Boolean? {
        val magnitude = sqrt(sample.x * sample.x + sample.y * sample.y + sample.z * sample.z)
        buffer[bufferIndex] = magnitude
        bufferIndex++

        if (bufferIndex >= windowSize) {
            bufferIndex = 0

            val isWindowQuiescent = calculateVariance(buffer) < varianceThreshold

            if (isWindowQuiescent != currentQuiescentState) {
                consecutiveAgreements++
                if (consecutiveAgreements >= debounceWindows) {
                    currentQuiescentState = isWindowQuiescent
                    consecutiveAgreements = 0
                    return currentQuiescentState
                }
            } else {
                consecutiveAgreements = 0
            }
        }

        return null
    }

    private fun calculateVariance(data: FloatArray): Float {
        val mean = data.average().toFloat()
        var sum = 0f
        for (value in data) {
            sum += (value - mean) * (value - mean)
        }
        return sum / data.size
    }
}
