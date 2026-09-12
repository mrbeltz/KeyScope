package com.jonny.keyscope.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Arbitrary-rate resampling for decoded files, which arrive at whatever the encoder felt like:
 * 48 kHz, 44.1, 22.05, sometimes 32.
 *
 * Low-pass first, then linear interpolation. The filter is what makes the cheap interpolation
 * acceptable — everything above the destination Nyquist is gone before any decimation happens,
 * so there is nothing left to fold back down.
 */
class Resampler(sourceRate: Int, targetRate: Int) {

    private val ratio = sourceRate.toDouble() / targetRate

    private val b = Array(3) { FloatArray(3) }
    private val a = Array(3) { FloatArray(2) }
    private val z1 = FloatArray(3)
    private val z2 = FloatArray(3)

    private var scratch = FloatArray(0)
    private var carry = 0f
    private var position = 0.0

    init {
        // Only filter when actually coming down in rate; upsampling needs no anti-aliasing.
        val cutoff = if (targetRate < sourceRate) targetRate * 0.40 else sourceRate * 0.45
        val qs = doubleArrayOf(0.51764, 0.70711, 1.93185) // Butterworth sections, order 6
        for (section in 0 until 3) design(section, cutoff, sourceRate.toDouble(), qs[section])
    }

    private fun design(section: Int, fc: Double, fs: Double, q: Double) {
        val w0 = 2.0 * PI * fc / fs
        val cosW = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        b[section][0] = ((1.0 - cosW) / 2.0 / a0).toFloat()
        b[section][1] = ((1.0 - cosW) / a0).toFloat()
        b[section][2] = ((1.0 - cosW) / 2.0 / a0).toFloat()
        a[section][0] = (-2.0 * cosW / a0).toFloat()
        a[section][1] = ((1.0 - alpha) / a0).toFloat()
    }

    /** Worst-case output length for an input block of [count] samples. */
    fun maxOutput(count: Int): Int = (count / ratio).toInt() + 2

    /**
     * Resamples [count] samples of [input] into [output], returning how many were written.
     * Safe to call block by block; filter state and fractional position carry across.
     */
    fun process(input: FloatArray, count: Int, output: FloatArray): Int {
        if (count <= 0) return 0
        if (scratch.size < count + 1) scratch = FloatArray(count + 1)

        // Index 0 holds the previous block's final sample so interpolation can span the seam.
        scratch[0] = carry
        for (i in 0 until count) {
            var x = input[i]
            for (s in 0 until 3) {
                val y = b[s][0] * x + z1[s]
                z1[s] = b[s][1] * x - a[s][0] * y + z2[s]
                z2[s] = b[s][2] * x - a[s][1] * y
                x = y
            }
            scratch[i + 1] = x
        }

        var written = 0
        while (position < count && written < output.size) {
            val index = position.toInt()
            val fraction = (position - index).toFloat()
            output[written++] = scratch[index] * (1f - fraction) + scratch[index + 1] * fraction
            position += ratio
        }
        position -= count
        if (position < 0.0) position = 0.0
        carry = scratch[count]
        return written
    }

    fun reset() {
        z1.fill(0f)
        z2.fill(0f)
        carry = 0f
        position = 0.0
    }
}
