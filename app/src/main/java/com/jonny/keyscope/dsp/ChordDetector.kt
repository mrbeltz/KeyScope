package com.jonny.keyscope.dsp

import com.jonny.keyscope.Mode
import com.jonny.keyscope.MusicalKey
import kotlin.math.sqrt

enum class ChordQuality(val symbol: String, val intervals: IntArray) {
    MAJOR("", intArrayOf(0, 4, 7)),
    MINOR("m", intArrayOf(0, 3, 7)),
    DIMINISHED("dim", intArrayOf(0, 3, 6)),
    SUS4("sus4", intArrayOf(0, 5, 7)),
    DOMINANT7("7", intArrayOf(0, 4, 7, 10)),
    MINOR7("m7", intArrayOf(0, 3, 7, 10)),
    MAJOR7("maj7", intArrayOf(0, 4, 7, 11))
}

data class DetectedChord(val root: Int, val quality: ChordQuality, val score: Float) {

    /** Spelled inside the key where possible, so F major shows Bb rather than A#. */
    fun name(key: MusicalKey?): String {
        val rootName = key?.scaleNotes
            ?.firstOrNull { MusicalKey.pitchClassOf(it) == root }
            ?: MusicalKey.CHROMA_LABELS[root]
        return rootName + quality.symbol
    }

    fun pitchClasses(): List<Int> = quality.intervals.map { Math.floorMod(root + it, 12) }
}

/** One chord as it appeared in time. */
data class ChordSpan(val chord: DetectedChord, val startMillis: Long, val endMillis: Long)

/**
 * Chord matching against binary templates, biased by the key.
 *
 * This is deliberately less ambitious than the key detector and will be less accurate. Chords move
 * far faster than the 743 ms analysis window, so a frame that straddles a change sees both chords
 * at once; sevenths and their relative triads share three of four notes; and an inversion looks
 * identical to its root position in a chroma, which has thrown the octave away by construction.
 * Treat the readout as a strong hint rather than a transcription.
 */
class ChordDetector {

    companion object {
        /** Below this the frame is not confidently any chord, so nothing is reported. */
        private const val MIN_SCORE = 0.62f

        /** Richer templates fit anything slightly better, so they have to earn the extra notes. */
        private const val NOTE_PENALTY = 0.018f

        /** Chords drawn from the detected key are simply far more likely than the alternatives. */
        private const val DIATONIC_BONUS = 0.045f

        /** Frames a candidate must win before it replaces the standing chord. */
        private const val COMMIT_FRAMES = 2
    }

    private val templates: List<Pair<DetectedChord, FloatArray>> = buildList {
        for (root in 0 until 12) {
            for (quality in ChordQuality.entries) {
                val template = FloatArray(12)
                for (interval in quality.intervals) {
                    template[Math.floorMod(root + interval, 12)] = 1f
                }
                normalise(template)
                add(DetectedChord(root, quality, 0f) to template)
            }
        }
    }

    private val smoothed = FloatArray(12)
    private var primed = false
    private var candidate: DetectedChord? = null
    private var candidateFrames = 0
    private var committed: DetectedChord? = null

    /**
     * Feeds one frame of chroma and returns the chord currently being held, or null.
     * Smoothing is inside because a per-frame answer flickers far too much to read.
     */
    fun track(chroma12: FloatArray, key: MusicalKey?): DetectedChord? {
        // A short exponential average steadies the readout without blurring changes into mush.
        if (!primed) {
            System.arraycopy(chroma12, 0, smoothed, 0, 12)
            primed = true
        } else {
            for (i in 0 until 12) smoothed[i] += (chroma12[i] - smoothed[i]) * 0.5f
        }

        val best = match(smoothed, key)
        if (best == null) {
            candidateFrames = 0
            candidate = null
            return committed
        }

        if (candidate != null && candidate!!.root == best.root && candidate!!.quality == best.quality) {
            candidateFrames++
        } else {
            candidate = best
            candidateFrames = 1
        }
        if (candidateFrames >= COMMIT_FRAMES) committed = best
        return committed
    }

    fun reset() {
        smoothed.fill(0f)
        primed = false
        candidate = null
        candidateFrames = 0
        committed = null
    }

    /** Single-frame match with no smoothing; the useful entry point for tests. */
    fun match(chroma12: FloatArray, key: MusicalKey?): DetectedChord? {
        val query = chroma12.copyOf()
        normalise(query)
        if (query.all { it == 0f }) return null

        val inKey = key?.let { scalePitchClasses(it) }

        var bestChord: DetectedChord? = null
        var bestScore = 0f
        for ((chord, template) in templates) {
            var score = dot(query, template) - NOTE_PENALTY * (chord.quality.intervals.size - 3)
            if (inKey != null && chord.pitchClasses().all { it in inKey }) score += DIATONIC_BONUS
            if (score > bestScore) {
                bestScore = score
                bestChord = chord
            }
        }
        if (bestChord == null || bestScore < MIN_SCORE) return null
        return bestChord.copy(score = bestScore)
    }

    private fun scalePitchClasses(key: MusicalKey): Set<Int> {
        val steps = if (key.mode == Mode.MAJOR) {
            intArrayOf(0, 2, 4, 5, 7, 9, 11)
        } else {
            intArrayOf(0, 2, 3, 5, 7, 8, 10)
        }
        return steps.map { Math.floorMod(key.tonic + it, 12) }.toSet()
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in 0 until 12) sum += a[i] * b[i]
        return sum
    }
}

/** Scales to unit length so the comparison is about shape, not loudness. */
private fun normalise(values: FloatArray) {
    var energy = 0f
    for (v in values) energy += v * v
    val norm = sqrt(energy)
    if (norm <= 0f) return
    for (i in values.indices) values[i] /= norm
}
