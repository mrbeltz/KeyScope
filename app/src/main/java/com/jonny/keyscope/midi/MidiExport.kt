package com.jonny.keyscope.midi

import com.jonny.keyscope.MusicalKey
import com.jonny.keyscope.Progression
import com.jonny.keyscope.audio.FileAnalyzer
import com.jonny.keyscope.dsp.MelodyExtractor

/**
 * Turns what the app has worked out into notes a DAW will accept.
 *
 * Everything is voiced in root position in the octave below middle C, which is where a chord
 * sketch wants to sit. Nothing here tries to be a performance — it is a starting point to drag in
 * and rewrite.
 */
object MidiExport {

    /** C3. Low enough for chords to sound like chords, high enough not to mud up. */
    private const val CHORD_BASE = 48

    /** C4 for single-line material like a scale. */
    private const val MELODY_BASE = 60

    /** The chord timeline from a file, at that file's own tempo. */
    fun fromChordTimeline(chords: List<FileAnalyzer.TimedChord>, bpm: Float): List<MidiWriter.Note> =
        chords.flatMap { timed ->
            val start = MidiWriter.ticksForSeconds(timed.startSeconds, bpm)
            val end = MidiWriter.ticksForSeconds(timed.endSeconds, bpm)
            timed.chord.quality.intervals.map { interval ->
                MidiWriter.Note(CHORD_BASE + timed.chord.root + interval, start, end)
            }
        }

    /**
     * A progression from the suggestions list, one chord per bar.
     *
     * Built by stacking scale positions rather than by parsing the chord name, which keeps the
     * quality correct for free and matches exactly what the audition plays.
     */
    fun fromProgression(key: MusicalKey, progression: Progression): List<MidiWriter.Note> {
        val notes = key.scaleNotes
        val barTicks = MidiWriter.TICKS_PER_QUARTER * 4
        return progression.degrees.flatMapIndexed { index, degree ->
            listOf(degree, degree + 2, degree + 4).map { position ->
                val pitchClass = MusicalKey.pitchClassOf(notes[position % 7])
                val octave = if (position >= 7) 1 else 0
                MidiWriter.Note(
                    CHORD_BASE + pitchClass + 12 * octave,
                    index * barTicks,
                    (index + 1) * barTicks
                )
            }
        }
    }

    /**
     * A tracked melody, kept at the pitches and times it was actually played. No quantising: the
     * timing is evidence of what happened, and a DAW can straighten it far better than this can.
     */
    fun fromMelody(notes: List<MelodyExtractor.Note>, bpm: Float): List<MidiWriter.Note> =
        notes.map { note ->
            MidiWriter.Note(
                note.midi,
                MidiWriter.ticksForSeconds(note.startSeconds, bpm),
                MidiWriter.ticksForSeconds(note.endSeconds, bpm)
            )
        }

    /** The scale as a single ascending line, a note per beat. */
    fun fromScale(key: MusicalKey): List<MidiWriter.Note> {
        val beat = MidiWriter.TICKS_PER_QUARTER
        val pitches = key.scaleNotes.map { MusicalKey.pitchClassOf(it) }
        var octave = 0
        var previous = -1
        val line = pitches.map { pitchClass ->
            if (previous >= 0 && pitchClass <= previous) octave++
            previous = pitchClass
            MELODY_BASE + pitchClass + 12 * octave
        } + (MELODY_BASE + key.tonic + 12)
        return line.mapIndexed { index, pitch ->
            MidiWriter.Note(pitch, index * beat, (index + 1) * beat)
        }
    }
}
