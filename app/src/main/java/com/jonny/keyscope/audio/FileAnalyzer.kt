package com.jonny.keyscope.audio

import android.content.Context
import android.net.Uri
import com.jonny.keyscope.MusicalKey
import com.jonny.keyscope.dsp.AnalysisConfig
import com.jonny.keyscope.dsp.ChordDetector
import com.jonny.keyscope.dsp.ChromaAccumulator
import com.jonny.keyscope.dsp.ChromaExtractor
import com.jonny.keyscope.dsp.DetectedChord
import com.jonny.keyscope.dsp.KeyDetector
import com.jonny.keyscope.dsp.KeyProfile
import com.jonny.keyscope.dsp.TempoTracker
import java.util.Locale
import kotlin.math.sqrt

/**
 * Runs the live pipeline over a file instead of the microphone.
 *
 * Two differences from listening in a room. The whole file feeds one average rather than a rolling
 * window, because there is no reason to forget the first verse. And nothing is lost to a speaker
 * and a microphone in between, so the reading is simply better than the same track played out
 * loud — this is the way to analyse anything you already have as a file.
 */
object FileAnalyzer {

    /** Roughly thirteen minutes of frames; longer files start forgetting the beginning. */
    private const val MAX_FRAMES = 4096

    private const val SILENCE_RMS = 0.0015f

    /**
     * Chords get their own, shorter frame. Offline there is no reason to make them share the key
     * path's 743 ms window, which is the single biggest thing holding their accuracy back live.
     */
    private const val CHORD_FFT = 4096      // 372 ms
    private const val CHORD_HOP = 1024      // 93 ms

    /** Chords shorter than this are almost always a frame straddling a change, not a chord. */
    private const val MIN_CHORD_SECONDS = 0.25f

    /** How much audio each key segment covers when looking for modulations. */
    private const val SEGMENT_SECONDS = 20

    data class TimedChord(
        val chord: DetectedChord,
        val startSeconds: Float,
        val endSeconds: Float
    )

    data class KeySegment(
        val key: MusicalKey,
        val startSeconds: Float,
        val endSeconds: Float
    )

    data class Result(
        val name: String,
        val key: MusicalKey? = null,
        val confidence: Float = 0f,
        val tuningCents: Float = 0f,
        val bpm: Float = 0f,
        val durationSeconds: Float = 0f,
        val chords: List<TimedChord> = emptyList(),
        val segments: List<KeySegment> = emptyList(),
        val error: String? = null
    ) {
        /** Segments that disagree with the overall reading — where a modulation would show up. */
        val modulations: List<KeySegment>
            get() = segments.filter { it.key != key }

        val chordSummary: String
            get() = chords.joinToString("  →  ") { it.chord.name(key) }

        val summary: String
            get() = when {
                error != null -> error
                key == null -> "No key found"
                else -> buildString {
                    append(key.name)
                    append("  ·  ")
                    append(key.camelot)
                    if (bpm > 0f) append("  ·  ${Math.round(bpm)} BPM")
                }
            }

        fun toCsvRow(): String {
            val safeName = name.replace("\"", "\"\"")
            return listOf(
                "\"$safeName\"",
                key?.name ?: "",
                key?.camelot ?: "",
                key?.openKey ?: "",
                if (bpm > 0f) String.format(Locale.US, "%.1f", bpm) else "",
                String.format(Locale.US, "%.0f", tuningCents),
                String.format(Locale.US, "%.2f", confidence),
                String.format(Locale.US, "%.1f", durationSeconds)
            ).joinToString(",")
        }

        companion object {
            const val CSV_HEADER = "file,key,camelot,openkey,bpm,tuning_cents,confidence,seconds"
        }
    }

    fun analyze(context: Context, uri: Uri, profile: KeyProfile): Result {
        val name = AudioFileDecoder.displayName(context, uri)
        return try {
            val chroma = ChromaExtractor(AnalysisConfig.WORK_RATE, AnalysisConfig.FFT_SIZE)
            val accumulator = ChromaAccumulator(MAX_FRAMES)
            val tempo = TempoTracker(AnalysisConfig.WORK_RATE)
            val detector = KeyDetector(profile)

            val frame = FloatArray(AnalysisConfig.FFT_SIZE)
            val hopBuffer = FloatArray(AnalysisConfig.HOP)
            var hopFill = 0
            var keyFrames = 0

            // Segment profiles are kept raw and folded at the end, so every segment is corrected
            // with the tuning the whole file agreed on rather than its own noisy guess.
            val segmentFrames = SEGMENT_SECONDS * AnalysisConfig.WORK_RATE / AnalysisConfig.HOP
            var segmentAccumulator = ChromaAccumulator(segmentFrames)
            val segmentProfiles = ArrayList<Pair<FloatArray, Float>>()

            val chordChroma = ChromaExtractor(AnalysisConfig.WORK_RATE, CHORD_FFT)
            val chordDetector = ChordDetector(commitFrames = 2, smoothing = 0.45f)
            val chordFrame = FloatArray(CHORD_FFT)
            val chordHopBuffer = FloatArray(CHORD_HOP)
            var chordHopFill = 0
            var chordFrames = 0
            val rawChords = ArrayList<TimedChord>()

            val seconds = AudioFileDecoder.decode(
                context, uri, AnalysisConfig.WORK_RATE
            ) { block, count ->
                tempo.feed(block, count)

                var offset = 0
                while (offset < count) {
                    val take = minOf(AnalysisConfig.HOP - hopFill, count - offset)
                    System.arraycopy(block, offset, hopBuffer, hopFill, take)
                    hopFill += take
                    offset += take
                    if (hopFill < AnalysisConfig.HOP) continue
                    hopFill = 0

                    System.arraycopy(
                        frame, AnalysisConfig.HOP, frame, 0,
                        AnalysisConfig.FFT_SIZE - AnalysisConfig.HOP
                    )
                    System.arraycopy(
                        hopBuffer, 0, frame,
                        AnalysisConfig.FFT_SIZE - AnalysisConfig.HOP, AnalysisConfig.HOP
                    )

                    if (rmsOf(frame) >= SILENCE_RMS && chroma.process(frame)) {
                        accumulator.add(chroma.profile)
                        segmentAccumulator.add(chroma.profile)
                    }
                    keyFrames++

                    if (keyFrames % segmentFrames == 0) {
                        val startSeconds = secondsAt(keyFrames - segmentFrames, AnalysisConfig.HOP)
                        segmentProfiles.add(segmentAccumulator.snapshot() to startSeconds)
                        segmentAccumulator = ChromaAccumulator(segmentFrames)
                    }
                }

                var chordOffset = 0
                while (chordOffset < count) {
                    val take = minOf(CHORD_HOP - chordHopFill, count - chordOffset)
                    System.arraycopy(block, chordOffset, chordHopBuffer, chordHopFill, take)
                    chordHopFill += take
                    chordOffset += take
                    if (chordHopFill < CHORD_HOP) continue
                    chordHopFill = 0

                    System.arraycopy(chordFrame, CHORD_HOP, chordFrame, 0, CHORD_FFT - CHORD_HOP)
                    System.arraycopy(chordHopBuffer, 0, chordFrame, CHORD_FFT - CHORD_HOP, CHORD_HOP)

                    val at = secondsAt(chordFrames, CHORD_HOP)
                    chordFrames++
                    if (rmsOf(chordFrame) < SILENCE_RMS || !chordChroma.process(chordFrame)) continue

                    // No key to bias with yet on the first pass; the chords are re-read below
                    // once the file has told us what key it is in.
                    val held = chordDetector.track(
                        ChromaAccumulator.fold(chordChroma.profile, 0f), null
                    ) ?: continue

                    val last = rawChords.lastOrNull()
                    if (last != null && last.chord.root == held.root &&
                        last.chord.quality == held.quality
                    ) {
                        rawChords[rawChords.lastIndex] = last.copy(endSeconds = at)
                    } else {
                        rawChords.add(TimedChord(held, at, at))
                    }
                }
                true
            }

            if (hopFill > 0 || segmentAccumulator.fill > 0f) {
                segmentProfiles.add(
                    segmentAccumulator.snapshot() to
                        secondsAt(keyFrames - keyFrames % segmentFrames, AnalysisConfig.HOP)
                )
            }

            val profile36 = accumulator.snapshot()
            val tuning = ChromaAccumulator.estimateTuningCents(profile36)
            val folded = ChromaAccumulator.fold(profile36, tuning)
            val ranked = detector.rank(folded)

            if (ranked.isEmpty() || accumulator.fill <= 0f) {
                Result(name, durationSeconds = seconds, error = "Nothing tonal to measure")
            } else {
                val key = ranked.first().key
                val segments = segmentProfiles.mapIndexedNotNull { index, (raw, start) ->
                    val segmentRanked = detector.rank(ChromaAccumulator.fold(raw, tuning))
                    val segmentKey = segmentRanked.firstOrNull()?.key ?: return@mapIndexedNotNull null
                    val end = segmentProfiles.getOrNull(index + 1)?.second ?: seconds
                    KeySegment(segmentKey, start, end)
                }
                Result(
                    name = name,
                    key = key,
                    confidence = detector.confidence(ranked),
                    tuningCents = tuning,
                    bpm = tempo.bpm,
                    durationSeconds = seconds,
                    chords = rawChords.filter { it.endSeconds - it.startSeconds >= MIN_CHORD_SECONDS },
                    segments = segments
                )
            }
        } catch (e: AudioFileDecoder.DecodeFailure) {
            Result(name, error = e.message)
        } catch (e: Exception) {
            Result(name, error = e.message ?: "Could not decode this file")
        }
    }

    private fun secondsAt(frameIndex: Int, hop: Int): Float =
        frameIndex.toFloat() * hop / AnalysisConfig.WORK_RATE

    private fun rmsOf(buffer: FloatArray): Float {
        var acc = 0f
        for (v in buffer) acc += v * v
        return sqrt(acc / buffer.size)
    }
}
