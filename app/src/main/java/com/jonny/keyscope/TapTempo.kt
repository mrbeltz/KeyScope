package com.jonny.keyscope

/**
 * Tempo from taps.
 *
 * Uses the median interval rather than the mean: one late tap should not drag the answer, and
 * over a handful of taps the median throws it away entirely.
 */
class TapTempo(
    private val resetAfterMs: Long = 2500L,
    private val maxTaps: Int = 8
) {
    private val taps = ArrayDeque<Long>()

    val tapCount: Int get() = taps.size

    /** Returns the current estimate, or null until there are two taps to measure between. */
    fun tap(nowMs: Long = System.currentTimeMillis()): Float? {
        // A long gap means a new count-in, not a very slow tempo.
        if (taps.isNotEmpty() && nowMs - taps.last() > resetAfterMs) taps.clear()
        taps.addLast(nowMs)
        while (taps.size > maxTaps) taps.removeFirst()
        if (taps.size < 2) return null

        val intervals = taps.zipWithNext { a, b -> (b - a).toFloat() }.sorted()
        val median = intervals[intervals.size / 2]
        return if (median <= 0f) null else 60_000f / median
    }

    fun reset() = taps.clear()

    companion object {
        /**
         * Whether [tapped] and [detected] are the same tempo an octave apart. The autocorrelator
         * is half and double prone, so a tap that lands on 2x or 0.5x is confirming the detection
         * rather than contradicting it.
         */
        fun isOctaveOf(tapped: Float, detected: Float, tolerance: Float = 0.06f): Boolean {
            if (tapped <= 0f || detected <= 0f) return false
            for (ratio in floatArrayOf(0.5f, 2f)) {
                if (kotlin.math.abs(tapped / (detected * ratio) - 1f) < tolerance) return true
            }
            return false
        }

        fun isSameTempo(a: Float, b: Float, tolerance: Float = 0.04f): Boolean {
            if (a <= 0f || b <= 0f) return false
            return kotlin.math.abs(a / b - 1f) < tolerance
        }
    }
}
