package com.jonny.keyscope.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 6th-order Butterworth low-pass (three cascaded biquads) followed by integer decimation.
 *
 * Dropping 44.1 kHz to 11.025 kHz costs nothing musically (chroma only cares about roughly
 * 55 Hz - 5 kHz) and quadruples the frequency resolution of a fixed-size FFT, which is what
 * actually matters for telling a bass note from its neighbour.
 */
class Decimator(inputRate: Int, private val factor: Int) {

    val outputRate: Int = inputRate / factor

    private val b = Array(3) { FloatArray(3) }
    private val a = Array(3) { FloatArray(2) }
    private val z1 = FloatArray(3)
    private val z2 = FloatArray(3)
    private var phase = 0

    init {
        // Cutoff safely below the new Nyquist (outputRate / 2).
        val cutoff = outputRate * 0.40f
        val qs = floatArrayOf(0.51764f, 0.70711f, 1.93185f) // Butterworth section Qs, order 6
        for (s in 0 until 3) designLowPass(s, cutoff.toDouble(), inputRate.toDouble(), qs[s].toDouble())
    }

    private fun designLowPass(section: Int, fc: Double, fs: Double, q: Double) {
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

    /** Filters [count] samples of [input] and writes every [factor]-th one to [output]. */
    fun process(input: FloatArray, count: Int, output: FloatArray): Int {
        var written = 0
        for (i in 0 until count) {
            var x = input[i]
            for (s in 0 until 3) {
                val y = b[s][0] * x + z1[s]
                z1[s] = b[s][1] * x - a[s][0] * y + z2[s]
                z2[s] = b[s][2] * x - a[s][1] * y
                x = y
            }
            if (phase == 0) output[written++] = x
            phase = (phase + 1) % factor
        }
        return written
    }

    fun reset() {
        z1.fill(0f); z2.fill(0f); phase = 0
    }
}
