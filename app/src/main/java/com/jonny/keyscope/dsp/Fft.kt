package com.jonny.keyscope.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimal in-place radix-2 Cooley-Tukey FFT. Allocation-free once constructed, so a single
 * instance can be reused on the audio thread for every hop.
 */
class Fft(private val n: Int) {

    private val cosTable = FloatArray(n / 2)
    private val sinTable = FloatArray(n / 2)
    private val reversed = IntArray(n)

    init {
        require(n >= 2 && (n and (n - 1)) == 0) { "FFT size must be a power of two, was $n" }
        for (i in 0 until n / 2) {
            cosTable[i] = cos(2.0 * PI * i / n).toFloat()
            sinTable[i] = sin(2.0 * PI * i / n).toFloat()
        }
        val bits = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) reversed[i] = Integer.reverse(i) ushr (32 - bits)
    }

    /** Forward transform of [re]/[im], both length [n], overwritten with the result. */
    fun forward(re: FloatArray, im: FloatArray) {
        for (i in 0 until n) {
            val j = reversed[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var base = 0
            while (base < n) {
                var j = base
                var k = 0
                while (j < base + half) {
                    val l = j + half
                    val c = cosTable[k]
                    val s = -sinTable[k]
                    val tre = re[l] * c - im[l] * s
                    val tim = re[l] * s + im[l] * c
                    re[l] = re[j] - tre
                    im[l] = im[j] - tim
                    re[j] += tre
                    im[j] += tim
                    j++
                    k += step
                }
                base += size
            }
            size = size shl 1
        }
    }
}
