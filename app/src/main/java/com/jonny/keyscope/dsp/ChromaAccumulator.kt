package com.jonny.keyscope.dsp

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Rolling average of the last N chroma frames, plus reference-tuning estimation.
 *
 * A key is a property of a passage, not of a 0.7 s window, so everything downstream reads this
 * average rather than individual frames. Frames are peak-normalised before they go in: a loud
 * chorus and a quiet intro should get the same vote.
 */
class ChromaAccumulator(windowFrames: Int) {

    private val frames = Array(windowFrames.coerceAtLeast(1)) { FloatArray(ChromaExtractor.BINS) }
    private val sum = FloatArray(ChromaExtractor.BINS)
    private var writeIndex = 0
    private var count = 0

    /** 0..1, how full the analysis window is. */
    val fill: Float get() = count.toFloat() / frames.size

    fun add(frame: FloatArray) {
        var peak = 0f
        for (v in frame) if (v > peak) peak = v
        if (peak <= 0f) return
        val scale = 1f / peak

        val slot = frames[writeIndex]
        for (i in sum.indices) {
            sum[i] -= slot[i]
            slot[i] = frame[i] * scale
            sum[i] += slot[i]
        }
        writeIndex = (writeIndex + 1) % frames.size
        if (count < frames.size) count++
    }

    fun reset() {
        for (f in frames) f.fill(0f)
        sum.fill(0f)
        writeIndex = 0
        count = 0
    }

    fun snapshot(out: FloatArray = FloatArray(ChromaExtractor.BINS)): FloatArray {
        System.arraycopy(sum, 0, out, 0, sum.size)
        return out
    }

    companion object {
        private const val CENTS_PER_BIN = 100f / ChromaExtractor.BINS_PER_SEMITONE // 33.33

        /**
         * Estimates how far the recording sits from A=440 by reading the phase of the period-3
         * component of the 36-bin profile. Range is +/-50 cents, which covers tape-speed drift,
         * A=432 tunings and most orchestral A=442/443 conventions.
         */
        fun estimateTuningCents(profile36: FloatArray): Float {
            var real = 0.0
            var imag = 0.0
            for (i in profile36.indices) {
                val angle = 2.0 * PI * i / ChromaExtractor.BINS_PER_SEMITONE
                real += profile36[i] * cos(angle)
                imag -= profile36[i] * sin(angle)
            }
            if (real == 0.0 && imag == 0.0) return 0f
            val phase = atan2(imag, real) // -pi..pi
            val offsetBins = (phase / (2.0 * PI)) * ChromaExtractor.BINS_PER_SEMITONE
            return (offsetBins * CENTS_PER_BIN).toFloat()
        }

        /** Folds 36 tuning-corrected bins into the 12 pitch classes, C first. */
        fun fold(profile36: FloatArray, tuningCents: Float): FloatArray {
            val shift = (tuningCents / CENTS_PER_BIN).roundToInt()
            val out = FloatArray(12)
            val n = profile36.size
            for (i in 0 until n) {
                val j = (((i - shift) % n) + n) % n
                out[((j + 1) / ChromaExtractor.BINS_PER_SEMITONE) % 12] += profile36[i]
            }
            return out
        }
    }
}
