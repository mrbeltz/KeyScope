package com.jonny.keyscope

/**
 * A progression as scale degrees, 0-based. The chord qualities come from the key itself, so the
 * same degrees spell correctly whichever key they land in.
 */
data class Progression(val name: String, val degrees: List<Int>) {

    /** e.g. "Am - F - C - G" in the given key. */
    fun chordNames(key: MusicalKey): List<String> {
        val chords = key.diatonicChords
        return degrees.map { chords[it].name }
    }

    fun numerals(key: MusicalKey): String {
        val chords = key.diatonicChords
        return degrees.joinToString(" - ") { chords[it].numeral }
    }
}

object Progressions {

    private val MAJOR = listOf(
        Progression("Pop", listOf(0, 4, 5, 3)),
        Progression("Sensitive", listOf(5, 3, 0, 4)),
        Progression("Doo-wop", listOf(0, 5, 3, 4)),
        Progression("Turnaround", listOf(1, 4, 0)),
        Progression("Three chord", listOf(0, 3, 4)),
        Progression("Descending", listOf(0, 4, 5, 2))
    )

    private val MINOR = listOf(
        Progression("Descending", listOf(0, 6, 5, 4)),
        Progression("Epic", listOf(0, 5, 2, 6)),
        Progression("Lift", listOf(0, 2, 5, 3)),
        Progression("Straight minor", listOf(0, 3, 4)),
        Progression("Vamp", listOf(0, 6, 5, 6)),
        Progression("Turnaround", listOf(1, 4, 0))
    )

    fun forKey(key: MusicalKey): List<Progression> =
        if (key.mode == Mode.MAJOR) MAJOR else MINOR

    /**
     * Triad frequencies for each chord of the progression, ready to hand to the tone engine.
     * Voiced by stacking the scale within one octave rather than transposing literally, which
     * keeps a progression from marching off the top of the phone speaker.
     */
    fun voicing(key: MusicalKey, progression: Progression, referenceHz: Float): List<List<Float>> {
        val notes = key.scaleNotes
        return progression.degrees.map { degree ->
            listOf(degree, degree + 2, degree + 4).map { position ->
                val pitchClass = MusicalKey.pitchClassOf(notes[position % 7])
                val octave = if (position >= 7) 1 else 0
                MusicalKey.frequencyOf(pitchClass, referenceHz) *
                    Math.pow(2.0, octave.toDouble()).toFloat()
            }
        }
    }
}
