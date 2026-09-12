package com.jonny.keyscope.audio

import android.content.Context
import android.net.Uri
import com.jonny.keyscope.MusicalKey
import com.jonny.keyscope.dsp.AnalysisConfig
import com.jonny.keyscope.dsp.ChromaAccumulator
import com.jonny.keyscope.dsp.ChromaExtractor
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

    data class Result(
        val name: String,
        val key: MusicalKey? = null,
        val confidence: Float = 0f,
        val tuningCents: Float = 0f,
        val bpm: Float = 0f,
        val durationSeconds: Float = 0f,
        val error: String? = null
    ) {
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
                    }
                }
                true
            }

            val profile36 = accumulator.snapshot()
            val tuning = ChromaAccumulator.estimateTuningCents(profile36)
            val folded = ChromaAccumulator.fold(profile36, tuning)
            val ranked = detector.rank(folded)

            if (ranked.isEmpty() || accumulator.fill <= 0f) {
                Result(name, durationSeconds = seconds, error = "Nothing tonal to measure")
            } else {
                Result(
                    name = name,
                    key = ranked.first().key,
                    confidence = detector.confidence(ranked),
                    tuningCents = tuning,
                    bpm = tempo.bpm,
                    durationSeconds = seconds
                )
            }
        } catch (e: AudioFileDecoder.DecodeFailure) {
            Result(name, error = e.message)
        } catch (e: Exception) {
            Result(name, error = e.message ?: "Could not decode this file")
        }
    }

    private fun rmsOf(buffer: FloatArray): Float {
        var acc = 0f
        for (v in buffer) acc += v * v
        return sqrt(acc / buffer.size)
    }
}
