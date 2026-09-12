package com.jonny.keyscope.midi

import java.io.ByteArrayOutputStream

/**
 * Writes a type-0 MIDI file by hand.
 *
 * The format is small enough that a dependency would cost more than it saves: a header chunk, one
 * track of delta-timed events, and a tempo meta event so the file lands at the right speed when
 * it is dragged into a DAW.
 */
object MidiWriter {

    const val TICKS_PER_QUARTER = 480

    data class Note(
        val pitch: Int,
        val startTick: Int,
        val endTick: Int,
        val velocity: Int = 88
    )

    fun ticksForSeconds(seconds: Float, bpm: Float): Int {
        val safeBpm = if (bpm > 0f) bpm else 120f
        return (seconds * (safeBpm / 60f) * TICKS_PER_QUARTER).toInt()
    }

    fun write(notes: List<Note>, bpm: Float): ByteArray {
        val safeBpm = if (bpm > 0f) bpm else 120f
        val track = ByteArrayOutputStream()

        // Tempo, so the bars line up rather than defaulting to 120.
        val microsPerQuarter = (60_000_000.0 / safeBpm).toInt()
        writeVarLen(track, 0)
        track.write(0xFF)
        track.write(0x51)
        track.write(0x03)
        track.write((microsPerQuarter shr 16) and 0xFF)
        track.write((microsPerQuarter shr 8) and 0xFF)
        track.write(microsPerQuarter and 0xFF)

        // Note on and note off as one flat list, ordered in time. Offs sort before ons at the same
        // tick so a repeated note retriggers instead of being cut short by its own predecessor.
        data class Event(val tick: Int, val isOn: Boolean, val pitch: Int, val velocity: Int)

        val events = ArrayList<Event>(notes.size * 2)
        for (note in notes) {
            val pitch = note.pitch.coerceIn(0, 127)
            val end = maxOf(note.endTick, note.startTick + 1)
            events.add(Event(note.startTick, true, pitch, note.velocity.coerceIn(1, 127)))
            events.add(Event(end, false, pitch, 0))
        }
        events.sortWith(compareBy({ it.tick }, { it.isOn }))

        var previousTick = 0
        for (event in events) {
            writeVarLen(track, (event.tick - previousTick).coerceAtLeast(0))
            previousTick = event.tick
            track.write(if (event.isOn) 0x90 else 0x80)
            track.write(event.pitch)
            track.write(event.velocity)
        }

        writeVarLen(track, 0)
        track.write(0xFF)
        track.write(0x2F)
        track.write(0x00)

        val trackBytes = track.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("MThd".toByteArray(Charsets.US_ASCII))
        writeInt(out, 6)
        writeShort(out, 0)                      // format 0, one track
        writeShort(out, 1)
        writeShort(out, TICKS_PER_QUARTER)
        out.write("MTrk".toByteArray(Charsets.US_ASCII))
        writeInt(out, trackBytes.size)
        out.write(trackBytes)
        return out.toByteArray()
    }

    private fun writeInt(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 24) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeShort(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    /** MIDI delta times are base-128, high bit set on every byte but the last. */
    private fun writeVarLen(out: ByteArrayOutputStream, value: Int) {
        var buffer = value and 0x7F
        var remaining = value ushr 7
        while (remaining > 0) {
            buffer = buffer shl 8
            buffer = buffer or ((remaining and 0x7F) or 0x80)
            remaining = remaining ushr 7
        }
        while (true) {
            out.write(buffer and 0xFF)
            if (buffer and 0x80 != 0) buffer = buffer ushr 8 else break
        }
    }
}
