package com.jonny.keyscope.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.jonny.keyscope.MusicalKey
import com.jonny.keyscope.Progression
import com.jonny.keyscope.midi.MidiExport
import com.jonny.keyscope.midi.MidiWriter
import java.io.File
import java.util.Locale

/**
 * Getting results out of the app: readable text, CSV, and MIDI.
 *
 * Files are written to the cache and handed out as content URIs through [FileProvider], which is
 * what puts Quick Share and every other target in the system share sheet. Nothing is written to
 * shared storage without you choosing where.
 */
object Exports {

    private const val AUTHORITY_SUFFIX = ".files"

    // ---------------------------------------------------------------- text

    /** The one-liner, for a filename or a message. */
    fun shortLine(state: EngineState): String = state.summaryLine

    /**
     * The full picture. Deliberately more than the one-liner, because a share that only repeats
     * what the clipboard already does is not worth a second button.
     */
    fun report(state: EngineState): String {
        val key = state.key ?: return "Key Bro — no key detected yet"
        return buildString {
            appendLine(key.name)
            appendLine("Camelot ${key.camelot}  ·  Open Key ${key.openKey}")
            appendLine()
            appendLine("Scale: ${key.scaleNotes.joinToString(" ")}")
            appendLine("Chords: ${key.diatonicChords.joinToString(" ") { it.name }}")
            if (state.bpm > 0f) {
                appendLine("Tempo: ${String.format(Locale.US, "%.1f", state.bpm)} BPM")
            }
            if (kotlin.math.abs(state.tuningCents) > 4f) {
                appendLine(
                    "Tuning: A=${Math.round(state.referenceHz)} Hz " +
                        "(${if (state.tuningCents >= 0) "+" else ""}${Math.round(state.tuningCents)} cents)"
                )
            }
            appendLine(
                "Mixes with: " + key.compatible.joinToString(", ") { "${it.shortName} (${it.camelot})" }
            )
            if (state.chordSpans.isNotEmpty()) {
                appendLine()
                appendLine(
                    "Heard: " + state.chordSpans.takeLast(8).joinToString(" → ") { it.chord.name(key) }
                )
            }
            append("— Key Bro")
        }
    }

    fun report(result: FileAnalyzer.Result): String {
        val key = result.key ?: return "${result.name} — ${result.summary}"
        return buildString {
            appendLine(result.name)
            appendLine(key.name)
            appendLine("Camelot ${key.camelot}  ·  Open Key ${key.openKey}")
            if (result.bpm > 0f) {
                appendLine("Tempo: ${String.format(Locale.US, "%.1f", result.bpm)} BPM")
            }
            appendLine("Scale: ${key.scaleNotes.joinToString(" ")}")
            if (result.chords.isNotEmpty()) {
                appendLine()
                appendLine("Chords: ${result.chordSummary}")
            }
            if (result.modulations.isNotEmpty()) {
                appendLine()
                appendLine("Key changes:")
                result.modulations.forEach {
                    appendLine("  ${timecode(it.startSeconds)}  ${it.key.name}")
                }
            }
            append("— Key Bro")
        }
    }

    fun timecode(seconds: Float): String {
        val total = seconds.toInt()
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
    }

    // ---------------------------------------------------------------- files

    /** Writes [bytes] into the cache and returns a URI the share sheet can hand to anyone. */
    fun stage(context: Context, fileName: String, bytes: ByteArray): Uri {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, sanitise(fileName))
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(
            context, context.packageName + AUTHORITY_SUFFIX, file
        )
    }

    fun shareFile(context: Context, uri: Uri, mimeType: String, subject: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    /** Writes bytes to wherever the document picker put them — Drive included. */
    fun writeTo(context: Context, target: Uri, bytes: ByteArray): Boolean = runCatching {
        context.contentResolver.openOutputStream(target, "wt")?.use { it.write(bytes) }
            ?: return false
        true
    }.getOrDefault(false)

    // ---------------------------------------------------------------- midi

    fun midiForChords(result: FileAnalyzer.Result): ByteArray =
        MidiWriter.write(MidiExport.fromChordTimeline(result.chords, result.bpm), result.bpm)

    fun midiForProgression(key: MusicalKey, progression: Progression, bpm: Float): ByteArray =
        MidiWriter.write(MidiExport.fromProgression(key, progression), bpm)

    fun midiForScale(key: MusicalKey, bpm: Float): ByteArray =
        MidiWriter.write(MidiExport.fromScale(key), bpm)

    /** Keeps a DAW-friendly, filesystem-safe name. */
    fun sanitise(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._ #()-]"), "_").take(80).ifBlank { "keybro" }

    fun baseName(name: String): String = name.substringBeforeLast('.', name)
}
