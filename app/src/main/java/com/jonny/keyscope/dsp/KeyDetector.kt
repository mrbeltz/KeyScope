package com.jonny.keyscope.dsp

import com.jonny.keyscope.MusicalKey
import com.jonny.keyscope.Mode
import kotlin.math.sqrt

/**
 * Published key profiles. Each is the average relative weight of the twelve pitch classes in that
 * mode, starting on the tonic.
 */
enum class KeyProfile(val label: String, val blurb: String, val major: FloatArray, val minor: FloatArray) {
    SHAATH(
        "Shaath",
        "Tuned on electronic and pop records. Best default.",
        floatArrayOf(6.6f, 2.0f, 3.5f, 2.3f, 4.6f, 4.0f, 2.5f, 5.2f, 2.4f, 3.7f, 2.3f, 3.4f),
        floatArrayOf(6.5f, 2.7f, 3.5f, 5.4f, 2.6f, 3.5f, 2.5f, 5.2f, 4.0f, 2.7f, 4.3f, 3.2f)
    ),
    KRUMHANSL(
        "Krumhansl",
        "The original probe-tone experiments. Leans classical.",
        floatArrayOf(6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f),
        floatArrayOf(6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f)
    ),
    TEMPERLEY(
        "Temperley",
        "Derived from notated scores. Sharper on diatonic material.",
        floatArrayOf(0.748f, 0.060f, 0.488f, 0.082f, 0.670f, 0.460f, 0.096f, 0.715f, 0.104f, 0.366f, 0.057f, 0.400f),
        floatArrayOf(0.712f, 0.084f, 0.474f, 0.618f, 0.049f, 0.460f, 0.105f, 0.747f, 0.404f, 0.067f, 0.133f, 0.330f)
    );
}

data class KeyCandidate(val key: MusicalKey, val score: Float)

/**
 * Correlates a 12-bin chroma vector against all 24 rotated key profiles and ranks them.
 */
class KeyDetector(@Volatile var profile: KeyProfile = KeyProfile.SHAATH) {

    private val rotated = FloatArray(12)

    fun rank(chroma: FloatArray): List<KeyCandidate> {
        val out = ArrayList<KeyCandidate>(24)
        for (mode in Mode.entries) {
            val template = if (mode == Mode.MAJOR) profile.major else profile.minor
            for (tonic in 0 until 12) {
                for (i in 0 until 12) rotated[i] = chroma[(tonic + i) % 12]
                out.add(KeyCandidate(MusicalKey(tonic, mode), correlation(rotated, template)))
            }
        }
        out.sortByDescending { it.score }
        return out
    }

    /**
     * How much to trust the winner: the absolute fit has to be decent *and* it has to beat the
     * runner-up. A chroma that matches C major and A minor equally well tells you nothing.
     */
    fun confidence(ranked: List<KeyCandidate>): Float {
        if (ranked.size < 2) return 0f
        val best = ranked[0].score
        val second = ranked[1].score
        if (best <= 0f) return 0f
        val margin = ((best - second) / 0.18f).coerceIn(0f, 1f)
        val fit = (best / 0.85f).coerceIn(0f, 1f)
        return fit * (0.25f + 0.75f * margin)
    }

    private fun correlation(a: FloatArray, b: FloatArray): Float {
        var meanA = 0f
        var meanB = 0f
        for (i in 0 until 12) { meanA += a[i]; meanB += b[i] }
        meanA /= 12f; meanB /= 12f
        var num = 0f
        var da = 0f
        var db = 0f
        for (i in 0 until 12) {
            val x = a[i] - meanA
            val y = b[i] - meanB
            num += x * y
            da += x * x
            db += y * y
        }
        val den = sqrt(da * db)
        return if (den <= 1e-9f) 0f else num / den
    }
}
