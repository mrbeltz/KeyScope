package com.jonny.keyscope.audio

import com.jonny.keyscope.MusicalKey
import com.jonny.keyscope.dsp.ChordSpan
import com.jonny.keyscope.dsp.DetectedChord
import com.jonny.keyscope.dsp.KeyProfile

/** How much audio the key estimate is averaged over. */
enum class AnalysisWindow(val label: String, val seconds: Int) {
    FAST("Fast", 6),
    NORMAL("Normal", 14),
    DEEP("Deep", 30)
}

data class HistoryEntry(
    val key: MusicalKey,
    val atMillis: Long,
    val bpm: Float,
    val confidence: Float
)

data class EngineState(
    val listening: Boolean = false,
    val key: MusicalKey? = null,
    /** 0..1 -- how much the winning key beats everything else. */
    val confidence: Float = 0f,
    /** True once the same key has held steady long enough to be worth acting on. */
    val locked: Boolean = false,
    val alternates: List<Pair<MusicalKey, Float>> = emptyList(),
    val chroma: List<Float> = List(12) { 0f },
    /** Reference-tuning offset from A=440, in cents. */
    val tuningCents: Float = 0f,
    val bpm: Float = 0f,
    val bpmConfidence: Float = 0f,
    /** 0..1 -- how full the analysis window is. */
    val windowFill: Float = 0f,
    val silent: Boolean = true,
    val window: AnalysisWindow = AnalysisWindow.NORMAL,
    val profile: KeyProfile = KeyProfile.SHAATH,
    val inputSource: String = "",
    /** The chord being held right now, from short-window chroma rather than the long average. */
    val chord: DetectedChord? = null,
    /** Recent chords in the order they were played, oldest first. */
    val chordSpans: List<ChordSpan> = emptyList(),
    /** When false, the mic shuts off the moment a lock lands. */
    val continuousListening: Boolean = false,
    /** True when the mic released itself on a lock rather than being stopped by hand. */
    val autoStopped: Boolean = false,
    val history: List<HistoryEntry> = emptyList(),
    val error: String? = null
) {
    val referenceHz: Float get() = (440.0 * Math.pow(2.0, tuningCents / 1200.0)).toFloat()

    /** The reading as one line, for the clipboard or a share sheet. */
    val summaryLine: String
        get() {
            val key = key ?: return "No key detected"
            return buildString {
                append(key.name)
                append(" · ")
                append(key.camelot)
                append(" · Open Key ")
                append(key.openKey)
                if (bpm > 0f) append(" · ${Math.round(bpm)} BPM")
                if (Math.abs(tuningCents) > 4f) append(" · A=${Math.round(referenceHz)} Hz")
            }
        }
}
