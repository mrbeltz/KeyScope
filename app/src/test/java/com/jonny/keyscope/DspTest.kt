package com.jonny.keyscope

import com.jonny.keyscope.dsp.ChromaAccumulator
import com.jonny.keyscope.dsp.ChromaExtractor
import com.jonny.keyscope.dsp.Fft
import com.jonny.keyscope.dsp.KeyDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The analysis chain is plain Kotlin with no Android dependencies, so the interesting half of
 * the app can be tested on the JVM: run ./gradlew test.
 */
class DspTest {

    private val sampleRate = 11025
    private val fftSize = 8192
    private val hop = 2048

    // ------------------------------------------------------------- FFT

    @Test
    fun `fft matches a naive dft`() {
        val n = 256
        val random = Random(7)
        val signal = FloatArray(n) { random.nextFloat() * 2f - 1f }

        val re = signal.copyOf()
        val im = FloatArray(n)
        Fft(n).forward(re, im)

        for (k in intArrayOf(0, 1, 5, 37, 128, 200)) {
            var dre = 0.0
            var dim = 0.0
            for (t in 0 until n) {
                val angle = -2.0 * PI * k * t / n
                dre += signal[t] * cos(angle)
                dim += signal[t] * sin(angle)
            }
            assertEquals("real bin $k", dre, re[k].toDouble(), 1e-2)
            assertEquals("imag bin $k", dim, im[k].toDouble(), 1e-2)
        }
    }

    // ------------------------------------------------------------- key detection

    @Test
    fun `detects a major progression`() {
        // I - V - vi - IV in C major.
        val signal = render(
            listOf(
                triad(60, MAJOR) to 3.0,
                triad(55, MAJOR) to 2.0,
                triad(57, MINOR) to 2.0,
                triad(53, MAJOR) to 2.0
            )
        )
        val key = detect(signal)
        assertEquals("C major", key.name)
        assertEquals("8B", key.camelot)
    }

    @Test
    fun `detects a minor progression`() {
        // i - iv - v - i in A minor.
        val signal = render(
            listOf(
                triad(57, MINOR) to 3.0,
                triad(62, MINOR) to 2.0,
                triad(64, MINOR) to 2.0,
                triad(57, MINOR) to 3.0
            )
        )
        val key = detect(signal)
        assertEquals("A minor", key.name)
        assertEquals("8A", key.camelot)
    }

    @Test
    fun `finds the tonic of a transposed progression`() {
        // The same major progression moved up three semitones should land on Eb major.
        val signal = render(
            listOf(
                triad(63, MAJOR) to 3.0,
                triad(58, MAJOR) to 2.0,
                triad(60, MINOR) to 2.0,
                triad(56, MAJOR) to 2.0
            )
        )
        assertEquals("Eb major", detect(signal).name)
    }

    @Test
    fun `estimates a flat reference tuning`() {
        val signal = render(
            listOf(triad(60, MAJOR) to 3.0, triad(55, MAJOR) to 3.0),
            reference = 432.0
        )
        val cents = tuningOf(signal)
        // 1200 * log2(432/440) is about -31.8 cents.
        assertTrue("estimated $cents cents", abs(cents + 31.8f) < 9f)
    }

    // ------------------------------------------------------------- naming

    @Test
    fun `spells scales with one letter per degree`() {
        assertEquals(
            listOf("F#", "G#", "A", "B", "C#", "D", "E"),
            MusicalKey(6, Mode.MINOR).scaleNotes
        )
        assertEquals(
            listOf("Eb", "F", "G", "Ab", "Bb", "C", "D"),
            MusicalKey(3, Mode.MAJOR).scaleNotes
        )
        assertEquals(
            listOf("C", "D", "E", "F", "G", "A", "B"),
            MusicalKey(0, Mode.MAJOR).scaleNotes
        )
    }

    @Test
    fun `camelot and open key codes follow the wheel`() {
        assertEquals("8B", MusicalKey(0, Mode.MAJOR).camelot)   // C major
        assertEquals("8A", MusicalKey(9, Mode.MINOR).camelot)   // A minor
        assertEquals("11A", MusicalKey(6, Mode.MINOR).camelot)  // F# minor
        assertEquals("1d", MusicalKey(0, Mode.MAJOR).openKey)
        assertEquals("1m", MusicalKey(9, Mode.MINOR).openKey)

        // Every key must map to a distinct wheel position.
        val codes = (0 until 12).flatMap { pc ->
            listOf(MusicalKey(pc, Mode.MAJOR).camelot, MusicalKey(pc, Mode.MINOR).camelot)
        }
        assertEquals(24, codes.toSet().size)
    }

    @Test
    fun `relative keys are reciprocal`() {
        for (pc in 0 until 12) {
            for (mode in Mode.entries) {
                val key = MusicalKey(pc, mode)
                assertEquals(key, key.relative.relative)
            }
        }
    }

    // ------------------------------------------------------------- helpers

    private val MAJOR = intArrayOf(0, 4, 7)
    private val MINOR = intArrayOf(0, 3, 7)

    private fun triad(rootMidi: Int, quality: IntArray) = quality.map { rootMidi + it }

    /** Additive synthesis: six harmonics per note, so the whitening and harmonic summing get used. */
    private fun render(chords: List<Pair<List<Int>, Double>>, reference: Double = 440.0): FloatArray {
        val out = ArrayList<Float>()
        for ((pitches, seconds) in chords) {
            val samples = (seconds * sampleRate).toInt()
            for (i in 0 until samples) {
                var value = 0.0
                val t = i.toDouble() / sampleRate
                // Short fades stop chord boundaries from splattering broadband clicks.
                val fade = minOf(1.0, i / (sampleRate * 0.02), (samples - i) / (sampleRate * 0.02))
                for (midi in pitches) {
                    val f0 = reference * Math.pow(2.0, (midi - 69) / 12.0)
                    for (h in 1..6) {
                        val f = f0 * h
                        if (f > sampleRate * 0.45) break
                        value += sin(2.0 * PI * f * t) / h
                    }
                }
                out.add((value * fade * 0.06).toFloat())
            }
        }
        return out.toFloatArray()
    }

    private fun chroma(signal: FloatArray): FloatArray {
        val extractor = ChromaExtractor(sampleRate, fftSize)
        val accumulator = ChromaAccumulator(4096)
        val frame = FloatArray(fftSize)
        var position = 0
        while (position + fftSize <= signal.size) {
            System.arraycopy(signal, position, frame, 0, fftSize)
            if (extractor.process(frame)) accumulator.add(extractor.profile)
            position += hop
        }
        return accumulator.snapshot()
    }

    private fun tuningOf(signal: FloatArray) = ChromaAccumulator.estimateTuningCents(chroma(signal))

    private fun detect(signal: FloatArray): MusicalKey {
        val profile36 = chroma(signal)
        val folded = ChromaAccumulator.fold(profile36, ChromaAccumulator.estimateTuningCents(profile36))
        return KeyDetector().rank(folded).first().key
    }
}
