package com.jonny.keyscope

enum class Mode { MAJOR, MINOR }

/** One chord of a key: its roman numeral and its actual name, e.g. "vi" and "Dm". */
data class DiatonicChord(val numeral: String, val name: String)

/**
 * A key, plus every way a musician or DJ might want it written down.
 *
 * [tonic] is a pitch class, 0 = C.
 */
data class MusicalKey(val tonic: Int, val mode: Mode) {

    /** e.g. "F#" / "Eb" -- spelled the way that key is conventionally written. */
    val tonicName: String
        get() = if (mode == Mode.MAJOR) MAJOR_NAMES[tonic] else MINOR_NAMES[tonic]

    /** e.g. "F# minor". */
    val name: String get() = "$tonicName ${if (mode == Mode.MAJOR) "major" else "minor"}"

    /** Compact form for the big readout, e.g. "F#m". */
    val shortName: String get() = tonicName + if (mode == Mode.MAJOR) "" else "m"

    /** Camelot wheel code used by Mixed In Key, Rekordbox, Serato. */
    val camelot: String
        get() = (if (mode == Mode.MAJOR) MAJOR_CAMELOT[tonic] else MINOR_CAMELOT[tonic]).toString() +
                if (mode == Mode.MAJOR) "B" else "A"

    /** Open Key notation used by Traktor. */
    val openKey: String
        get() {
            val camelotNumber = if (mode == Mode.MAJOR) MAJOR_CAMELOT[tonic] else MINOR_CAMELOT[tonic]
            val n = ((camelotNumber - 8 + 12) % 12) + 1
            return "$n${if (mode == Mode.MAJOR) "d" else "m"}"
        }

    /** The seven notes of the scale, correctly spelled (one letter name per degree). */
    val scaleNotes: List<String> get() = spellScale(tonicName, mode)

    /** Relative major of a minor key, or relative minor of a major key. */
    val relative: MusicalKey
        get() = if (mode == Mode.MAJOR) MusicalKey(Math.floorMod(tonic - 3, 12), Mode.MINOR)
        else MusicalKey(Math.floorMod(tonic + 3, 12), Mode.MAJOR)

    /**
     * Keys that mix cleanly: the relative key, and the two neighbours on the wheel (a fifth up
     * and a fourth up). These are the same three moves the Camelot wheel encodes.
     */
    val compatible: List<MusicalKey>
        get() = listOf(
            relative,
            MusicalKey(Math.floorMod(tonic + 7, 12), mode),
            MusicalKey(Math.floorMod(tonic + 5, 12), mode)
        )

    /** The seven chords built on the scale, for playing or writing over the top. */
    val diatonicChords: List<DiatonicChord>
        get() {
            val notes = scaleNotes
            val pitches = notes.map { pitchClassOf(it) }
            return List(7) { degree ->
                val root = pitches[degree]
                val third = Math.floorMod(pitches[(degree + 2) % 7] - root, 12)
                val fifth = Math.floorMod(pitches[(degree + 4) % 7] - root, 12)
                val numeral = ROMAN[degree]
                when {
                    third == 3 && fifth == 6 ->
                        DiatonicChord(numeral.lowercase() + "°", notes[degree] + "dim")
                    third == 3 -> DiatonicChord(numeral.lowercase(), notes[degree] + "m")
                    third == 4 && fifth == 8 ->
                        DiatonicChord("$numeral+", notes[degree] + "aug")
                    else -> DiatonicChord(numeral, notes[degree])
                }
            }
        }

    /**
     * Shortest signed distance to [target] in semitones, -6..+5. Shortest rather than always
     * upward because pitching a deck down four is the same move as up eight, and the small
     * number is the one that will not wreck the track.
     */
    fun semitonesTo(target: MusicalKey): Int =
        Math.floorMod(target.tonic - tonic + 6, 12) - 6

    /** Same tonic distance, expressed as the tempo change a varispeed pitch would drag along. */
    fun varispeedPercentTo(target: MusicalKey): Float =
        ((Math.pow(2.0, semitonesTo(target) / 12.0) - 1.0) * 100.0).toFloat()

    companion object {
        private val MAJOR_NAMES = arrayOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
        private val MINOR_NAMES = arrayOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#", "A", "Bb", "B")

        private val MAJOR_CAMELOT = intArrayOf(8, 3, 10, 5, 12, 7, 2, 9, 4, 11, 6, 1)
        private val MINOR_CAMELOT = intArrayOf(5, 12, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10)

        private val ROMAN = arrayOf("I", "II", "III", "IV", "V", "VI", "VII")

        private val LETTERS = "CDEFGAB"
        private val LETTER_PC = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        private val MAJOR_STEPS = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        private val MINOR_STEPS = intArrayOf(0, 2, 3, 5, 7, 8, 10)

        /** Pitch-class names for the chroma display; sharps only, since there is no key context. */
        val CHROMA_LABELS = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        /** Pitch class of a spelled note name such as "Bb" or "F#". */
        fun pitchClassOf(name: String): Int {
            val letter = LETTERS.indexOf(name[0])
            if (letter < 0) return 0
            return Math.floorMod(LETTER_PC[letter] + accidentalValue(name), 12)
        }

        /**
         * Frequency of a pitch class in the octave above middle C, at the given reference pitch.
         * That octave because a phone speaker cannot usefully reproduce a tonic an octave down.
         */
        fun frequencyOf(pitchClass: Int, referenceHz: Float = 440f): Float {
            val midi = 60 + Math.floorMod(pitchClass, 12)
            return (referenceHz * Math.pow(2.0, (midi - 69) / 12.0)).toFloat()
        }

        private fun accidentalValue(name: String): Int {
            var v = 0
            for (i in 1 until name.length) {
                when (name[i]) {
                    '#' -> v++
                    'b' -> v--
                }
            }
            return v
        }

        private fun accidentalString(v: Int): String = when {
            v > 0 -> "#".repeat(v)
            v < 0 -> "b".repeat(-v)
            else -> ""
        }

        /**
         * Walks up the letter names one per degree and works out the accidental each one needs.
         * That is what makes F# minor come out as F# G# A B C# D E instead of a mix of sharps
         * and flats.
         */
        private fun spellScale(tonicName: String, mode: Mode): List<String> {
            val letterIndex = LETTERS.indexOf(tonicName[0])
            if (letterIndex < 0) return emptyList()
            val tonicPc = Math.floorMod(LETTER_PC[letterIndex] + accidentalValue(tonicName), 12)
            val steps = if (mode == Mode.MAJOR) MAJOR_STEPS else MINOR_STEPS
            return steps.mapIndexed { degree, semitones ->
                val li = (letterIndex + degree) % 7
                val natural = LETTER_PC[li]
                val target = Math.floorMod(tonicPc + semitones, 12)
                val accidental = Math.floorMod(target - natural + 6, 12) - 6
                "${LETTERS[li]}${accidentalString(accidental)}"
            }
        }
    }
}
