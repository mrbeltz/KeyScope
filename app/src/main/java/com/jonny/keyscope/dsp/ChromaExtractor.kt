package com.jonny.keyscope.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Harmonic Pitch Class Profile extractor.
 *
 * Per frame: window -> FFT -> spectral whitening -> peak picking with parabolic interpolation ->
 * each peak votes for the pitch class of every plausible fundamental it could be a harmonic of.
 *
 * Whitening is what makes this work on real recordings: without it a bright synth pad and a dull
 * piano playing the same chord produce very different profiles, because the raw magnitudes are
 * dominated by timbre rather than by which notes are sounding.
 *
 * Output has three bins per semitone so the reference tuning can be estimated afterwards and
 * corrected before folding down to twelve.
 */
class ChromaExtractor(private val sampleRate: Int, private val fftSize: Int) {

    companion object {
        const val BINS_PER_SEMITONE = 3
        const val BINS = 12 * BINS_PER_SEMITONE

        private const val F_MIN = 55f      // A1
        private const val F_MAX = 5000f
        private const val HARMONICS = 8
        private const val HARMONIC_DECAY = 0.6f
        private const val LN2 = 0.6931471805599453
    }

    private val fft = Fft(fftSize)
    private val window = FloatArray(fftSize) { 0.5f - 0.5f * cos(2.0 * PI * it / fftSize).toFloat() }
    private val re = FloatArray(fftSize)
    private val im = FloatArray(fftSize)
    private val spectrumSize = fftSize / 2 + 1
    private val mag = FloatArray(spectrumSize)
    private val cum = FloatArray(spectrumSize + 1)
    private val white = FloatArray(spectrumSize)

    /** Latest frame's profile, indexed 0..35 with bin 0 centred on C. */
    val profile = FloatArray(BINS)

    /** Fraction of spectral energy that sat in resolved peaks; low for noise, high for music. */
    var tonalness: Float = 0f
        private set

    private val binToFreq = sampleRate.toFloat() / fftSize
    private val kMin = max(1, (F_MIN / binToFreq).toInt())
    private val kMax = min(spectrumSize - 2, (F_MAX / binToFreq).toInt())

    /** @param frame exactly [fftSize] samples; returns false when the frame held nothing usable. */
    fun process(frame: FloatArray): Boolean {
        profile.fill(0f)
        tonalness = 0f

        for (i in 0 until fftSize) {
            re[i] = frame[i] * window[i]
            im[i] = 0f
        }
        fft.forward(re, im)

        var total = 0f
        for (k in 0 until spectrumSize) {
            val m = sqrt(re[k] * re[k] + im[k] * im[k])
            mag[k] = m
            total += m
        }
        if (total <= 1e-6f) return false

        // Whitening: subtract a local average taken over a band that widens with frequency, so the
        // smoothing window is roughly constant in octaves rather than in Hz.
        cum[0] = 0f
        for (k in 0 until spectrumSize) cum[k + 1] = cum[k] + mag[k]
        for (k in kMin..kMax) {
            val w = max(2, (k * 0.18f).toInt())
            val lo = max(0, k - w)
            val hi = min(spectrumSize - 1, k + w)
            val avg = (cum[hi + 1] - cum[lo]) / (hi - lo + 1)
            white[k] = max(0f, mag[k] - avg)
        }

        var peakEnergy = 0f
        var contributed = false
        for (k in kMin..kMax) {
            val y2 = white[k]
            if (y2 <= 0f) continue
            val y1 = white[k - 1]
            val y3 = white[k + 1]
            if (y1 >= y2 || y3 > y2) continue

            val denom = y1 - 2f * y2 + y3
            val delta = if (abs(denom) < 1e-12f) 0f else 0.5f * (y1 - y3) / denom
            if (abs(delta) > 1f) continue
            val freq = (k + delta) * binToFreq
            val amp = y2 - 0.25f * (y1 - y3) * delta
            if (freq < F_MIN || amp <= 0f) continue

            peakEnergy += amp
            contributed = true
            addWithHarmonics(freq, amp)
        }

        tonalness = if (total > 0f) (peakEnergy / total).coerceIn(0f, 1f) else 0f
        return contributed
    }

    /** A peak at [freq] might be the h-th harmonic of freq/h, so it votes for each candidate root. */
    private fun addWithHarmonics(freq: Float, amp: Float) {
        var weight = 1f
        for (h in 1..HARMONICS) {
            val root = freq / h
            if (root < F_MIN) break
            splat(root, amp * weight)
            weight *= HARMONIC_DECAY
        }
    }

    private fun splat(freq: Float, amount: Float) {
        val midi = 69.0 + 12.0 * ln(freq / 440.0) / LN2
        var pc = midi % 12.0
        if (pc < 0) pc += 12.0
        val center = pc * BINS_PER_SEMITONE
        val base = floor(center).toInt()
        // cos^2 splatting over the neighbouring bins is a partition of unity, so total energy
        // is preserved no matter where between two bins the peak lands.
        for (offset in -1..2) {
            val idx = base + offset
            val d = idx - center
            if (abs(d) >= 1.0) continue
            val w = cos(PI * d / 2.0)
            profile[((idx % BINS) + BINS) % BINS] += amount * (w * w).toFloat()
        }
    }
}
