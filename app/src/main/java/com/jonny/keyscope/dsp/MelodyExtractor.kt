package com.jonny.keyscope.dsp

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Monophonic pitch tracking, for turning something hummed or played one note at a time into notes.
 *
 * This is YIN's cumulative mean normalised difference rather than plain autocorrelation. Raw
 * autocorrelation loves an octave down — a signal correlates nearly as well with itself at twice
 * the period — and an octave error is the one mistake that makes a transcription useless. The
 * normalisation is what suppresses it.
 *
 * Monophonic is the hard limit: given a chord this will report one of the notes, not all of them.
 * Chords come from the chroma path instead.
 */
object MelodyExtractor {

    private const val FRAME = 1024
    private const val HOP = 512

    private const val MIN_HZ = 65f      // C2
    private const val MAX_HZ = 1000f    // B5

    /** Below this a frame is periodic enough to trust; YIN's own recommended range. */
    private const val THRESHOLD = 0.16f

    private const val SILENCE_RMS = 0.004f

    /** Anything shorter is a transition artefact rather than a note somebody played. */
    private const val MIN_NOTE_SECONDS = 0.09f

    private const val LN2 = 0.6931471805599453

    data class Note(
        val midi: Int,
        val startSeconds: Float,
        val endSeconds: Float
    ) {
        val durationSeconds: Float get() = endSeconds - startSeconds
    }

    fun extract(samples: FloatArray, sampleRate: Int, referenceHz: Float = 440f): List<Note> {
        if (samples.size < FRAME) return emptyList()

        val tauMin = (sampleRate / MAX_HZ).toInt().coerceAtLeast(2)
        val tauMax = (sampleRate / MIN_HZ).toInt().coerceAtMost(FRAME / 2)
        val window = FRAME - tauMax
        if (window <= 0) return emptyList()

        val difference = FloatArray(tauMax + 1)
        val normalised = FloatArray(tauMax + 1)

        // -1 marks an unvoiced frame, which is what ends a note.
        val pitches = ArrayList<Int>()
        var position = 0
        while (position + FRAME <= samples.size) {
            pitches.add(
                pitchAt(samples, position, sampleRate, tauMin, tauMax, window, difference, normalised, referenceHz)
            )
            position += HOP
        }

        return segment(smooth(pitches), sampleRate)
    }

    private fun pitchAt(
        samples: FloatArray,
        offset: Int,
        sampleRate: Int,
        tauMin: Int,
        tauMax: Int,
        window: Int,
        difference: FloatArray,
        normalised: FloatArray,
        referenceHz: Float
    ): Int {
        var energy = 0f
        for (i in 0 until FRAME) energy += samples[offset + i] * samples[offset + i]
        if (sqrt(energy / FRAME) < SILENCE_RMS) return -1

        difference[0] = 0f
        for (tau in 1..tauMax) {
            var sum = 0f
            for (j in 0 until window) {
                val delta = samples[offset + j] - samples[offset + j + tau]
                sum += delta * delta
            }
            difference[tau] = sum
        }

        normalised[0] = 1f
        var running = 0f
        for (tau in 1..tauMax) {
            running += difference[tau]
            normalised[tau] = if (running <= 0f) 1f else difference[tau] * tau / running
        }

        // First dip below the threshold, not the global minimum: the earliest acceptable period is
        // the fundamental, and later ones are its octaves.
        var chosen = -1
        var tau = tauMin
        while (tau < tauMax) {
            if (normalised[tau] < THRESHOLD) {
                while (tau + 1 < tauMax && normalised[tau + 1] < normalised[tau]) tau++
                chosen = tau
                break
            }
            tau++
        }
        if (chosen < 0) return -1

        val refined = interpolate(normalised, chosen)
        val frequency = sampleRate / refined
        if (frequency < MIN_HZ || frequency > MAX_HZ) return -1

        val midi = 69.0 + 12.0 * ln(frequency / referenceHz) / LN2
        return midi.roundToInt().coerceIn(0, 127)
    }

    private fun interpolate(values: FloatArray, index: Int): Float {
        if (index <= 0 || index >= values.size - 1) return index.toFloat()
        val a = values[index - 1]
        val b = values[index]
        val c = values[index + 1]
        val denominator = a - 2f * b + c
        if (abs(denominator) < 1e-9f) return index.toFloat()
        val shift = 0.5f * (a - c) / denominator
        return if (abs(shift) < 1f) index + shift else index.toFloat()
    }

    /** A five-frame median, which removes single-frame octave slips without blurring real moves. */
    private fun smooth(pitches: List<Int>): List<Int> {
        if (pitches.size < 5) return pitches
        val out = ArrayList<Int>(pitches.size)
        val scratch = IntArray(5)
        for (i in pitches.indices) {
            if (i < 2 || i > pitches.size - 3) {
                out.add(pitches[i])
                continue
            }
            for (k in 0 until 5) scratch[k] = pitches[i - 2 + k]
            scratch.sort()
            out.add(scratch[2])
        }
        return out
    }

    private fun segment(pitches: List<Int>, sampleRate: Int): List<Note> {
        val secondsPerFrame = HOP.toFloat() / sampleRate
        val notes = ArrayList<Note>()
        var current = -1
        var startFrame = 0

        fun close(endFrame: Int) {
            if (current < 0) return
            val note = Note(current, startFrame * secondsPerFrame, endFrame * secondsPerFrame)
            if (note.durationSeconds >= MIN_NOTE_SECONDS) notes.add(note)
            current = -1
        }

        for ((index, pitch) in pitches.withIndex()) {
            if (pitch != current) {
                close(index)
                current = pitch
                startFrame = index
            }
        }
        close(pitches.size)
        return notes.filter { it.midi > 0 }
    }
}
