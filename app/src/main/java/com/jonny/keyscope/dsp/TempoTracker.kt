package com.jonny.keyscope.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Spectral-flux onset detection followed by autocorrelation of the onset envelope.
 *
 * Runs on the same decimated stream as the chroma path but with a much shorter frame, because
 * tempo needs time resolution where key needs frequency resolution.
 */
class TempoTracker(sampleRate: Int) {

    companion object {
        private const val FRAME = 1024
        private const val HOP = 256
        private const val HISTORY_SECONDS = 12
        private const val MIN_BPM = 60f
        private const val MAX_BPM = 200f
    }

    private val fft = Fft(FRAME)
    private val window = FloatArray(FRAME) { 0.5f - 0.5f * cos(2.0 * PI * it / FRAME).toFloat() }
    private val re = FloatArray(FRAME)
    private val im = FloatArray(FRAME)
    private val prevMag = FloatArray(FRAME / 2 + 1)

    private val frameBuffer = FloatArray(FRAME)
    private val hopBuffer = FloatArray(HOP)
    private var hopFill = 0
    private var primed = false

    private val odfRate = sampleRate.toFloat() / HOP
    private val odf = FloatArray((odfRate * HISTORY_SECONDS).toInt())
    private var odfWrite = 0
    private var odfCount = 0

    private val minLag = (60f * odfRate / MAX_BPM).toInt().coerceAtLeast(2)
    private val maxLag = (60f * odfRate / MIN_BPM).toInt().coerceAtMost(odf.size / 3)

    var bpm: Float = 0f
        private set
    var confidence: Float = 0f
        private set

    fun feed(input: FloatArray, count: Int) {
        var i = 0
        while (i < count) {
            val take = minOf(HOP - hopFill, count - i)
            System.arraycopy(input, i, hopBuffer, hopFill, take)
            hopFill += take
            i += take
            if (hopFill == HOP) {
                System.arraycopy(frameBuffer, HOP, frameBuffer, 0, FRAME - HOP)
                System.arraycopy(hopBuffer, 0, frameBuffer, FRAME - HOP, HOP)
                hopFill = 0
                pushOnset(spectralFlux())
            }
        }
    }

    fun reset() {
        odf.fill(0f); odfWrite = 0; odfCount = 0
        prevMag.fill(0f); primed = false
        hopFill = 0; frameBuffer.fill(0f)
        bpm = 0f; confidence = 0f
    }

    private fun spectralFlux(): Float {
        for (k in 0 until FRAME) {
            re[k] = frameBuffer[k] * window[k]
            im[k] = 0f
        }
        fft.forward(re, im)
        var flux = 0f
        for (k in 0..FRAME / 2) {
            val m = sqrt(re[k] * re[k] + im[k] * im[k])
            // Log compression keeps a loud kick from swamping every other onset in the window.
            val c = ln(1f + 40f * m)
            if (primed) {
                val d = c - prevMag[k]
                if (d > 0f) flux += d
            }
            prevMag[k] = c
        }
        primed = true
        return flux
    }

    private fun pushOnset(value: Float) {
        odf[odfWrite] = value
        odfWrite = (odfWrite + 1) % odf.size
        if (odfCount < odf.size) odfCount++
        if (odfCount >= odf.size && odfWrite % 16 == 0) estimate()
    }

    private fun estimate() {
        val n = odf.size
        val x = FloatArray(n)
        for (i in 0 until n) x[i] = odf[(odfWrite + i) % n]

        // Remove the slow-moving floor so only the beat-rate fluctuation is correlated.
        val w = (odfRate * 0.7f).toInt().coerceAtLeast(3)
        val cum = FloatArray(n + 1)
        for (i in 0 until n) cum[i + 1] = cum[i] + x[i]
        var mean = 0f
        for (i in 0 until n) {
            val lo = maxOf(0, i - w)
            val hi = minOf(n - 1, i + w)
            x[i] = max(0f, x[i] - (cum[hi + 1] - cum[lo]) / (hi - lo + 1))
            mean += x[i]
        }
        mean /= n
        for (i in 0 until n) x[i] -= mean

        var bestLag = -1
        var bestScore = 0f
        var scoreSum = 0f
        var scoreCount = 0
        val scores = FloatArray(maxLag + 1)
        for (lag in minLag..maxLag) {
            var acc = 0f
            for (i in 0 until n - lag) acc += x[i] * x[i + lag]
            acc /= (n - lag)
            // Prior against absurd tempos; centred on 120 BPM, about an octave wide.
            val candidateBpm = 60f * odfRate / lag
            val prior = exp(-0.5 * square(ln(candidateBpm / 120f) / 0.7)).toFloat()
            val score = acc * prior
            scores[lag] = score
            scoreSum += abs(score)
            scoreCount++
            if (score > bestScore) { bestScore = score; bestLag = lag }
        }
        if (bestLag < 0 || bestScore <= 0f) { confidence = 0f; return }

        // Parabolic interpolation for sub-bin tempo resolution.
        var refined = bestLag.toFloat()
        if (bestLag > minLag && bestLag < maxLag) {
            val y1 = scores[bestLag - 1]
            val y2 = scores[bestLag]
            val y3 = scores[bestLag + 1]
            val denom = y1 - 2f * y2 + y3
            if (abs(denom) > 1e-9f) {
                val d = 0.5f * (y1 - y3) / denom
                if (abs(d) < 1f) refined += d
            }
        }

        bpm = 60f * odfRate / refined
        val avg = if (scoreCount > 0) scoreSum / scoreCount else 0f
        confidence = if (avg <= 0f) 0f else ((bestScore / avg - 1f) / 3f).coerceIn(0f, 1f)
    }

    private fun square(v: Double) = v * v
}
