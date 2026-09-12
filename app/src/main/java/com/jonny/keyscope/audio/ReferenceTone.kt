package com.jonny.keyscope.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Sounds the detected tonic, or walks the scale, so a reading can be checked or sung against.
 *
 * Tones are built at the *detected* reference pitch rather than at a fixed A=440, so playing
 * along with a record cut slightly sharp or flat actually lines up.
 */
object ReferenceTone {

    private const val SAMPLE_RATE = 44100
    private const val CHUNK = 512
    private const val ATTACK_MS = 18
    private const val RELEASE_MS = 45

    /** A little second and third harmonic: a bare sine barely survives a phone speaker. */
    private val HARMONIC_GAINS = floatArrayOf(1f, 0.34f, 0.14f)

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private var thread: Thread? = null
    @Volatile private var running = false

    /** Holds a single note until stopped. */
    fun startDrone(frequencyHz: Float) = start(listOf(frequencyHz), stepMs = 0)

    /** Walks up the scale and stops, tonic to tonic. */
    fun playScale(frequencies: List<Float>) = start(frequencies, stepMs = 340)

    @Synchronized
    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        _playing.value = false
    }

    @Synchronized
    private fun start(frequencies: List<Float>, stepMs: Int) {
        stop()
        if (frequencies.isEmpty()) return
        running = true
        _playing.value = true
        thread = Thread({ render(frequencies, stepMs) }, "KeyScope-Tone").also { it.start() }
    }

    private fun render(frequencies: List<Float>, stepMs: Int) {
        var track: AudioTrack? = null
        try {
            val minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT
            )
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuffer, CHUNK * 8))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()

            val buffer = FloatArray(CHUNK)
            val attack = SAMPLE_RATE * ATTACK_MS / 1000
            val release = SAMPLE_RATE * RELEASE_MS / 1000

            // Phase carries across the note boundary and into the fade so nothing ever clicks.
            var phase = 0.0
            var step = 0.0

            var noteIndex = 0
            while (running && noteIndex < frequencies.size) {
                // stepMs == 0 means hold this note until someone stops us.
                val noteSamples = if (stepMs == 0) Int.MAX_VALUE else SAMPLE_RATE * stepMs / 1000
                var written = 0
                step = 2.0 * PI * frequencies[noteIndex] / SAMPLE_RATE

                while (running && written < noteSamples) {
                    val count = min(CHUNK, noteSamples - written)
                    for (i in 0 until count) {
                        var sample = 0f
                        for (h in HARMONIC_GAINS.indices) {
                            sample += HARMONIC_GAINS[h] * sin(phase * (h + 1)).toFloat()
                        }
                        val position = written + i
                        var envelope = min(1f, position.toFloat() / attack)
                        if (noteSamples != Int.MAX_VALUE) {
                            val remaining = noteSamples - position
                            if (remaining < release) envelope *= remaining.toFloat() / release
                        }
                        buffer[i] = sample * envelope * 0.17f
                        phase += step
                    }
                    track.write(buffer, 0, count, AudioTrack.WRITE_BLOCKING)
                    written += count
                }
                noteIndex++
            }

            // Fade out rather than cutting, so stopping a drone does not click.
            var faded = 0
            while (step > 0.0 && faded < release) {
                val count = min(CHUNK, release - faded)
                for (i in 0 until count) {
                    var sample = 0f
                    for (h in HARMONIC_GAINS.indices) {
                        sample += HARMONIC_GAINS[h] * sin(phase * (h + 1)).toFloat()
                    }
                    buffer[i] = sample * (1f - (faded + i).toFloat() / release) * 0.17f
                    phase += step
                }
                track.write(buffer, 0, count, AudioTrack.WRITE_BLOCKING)
                faded += count
            }
        } catch (_: Exception) {
            // A tone is a convenience; losing it should never take the app down.
        } finally {
            try {
                track?.stop()
            } catch (_: IllegalStateException) {
                // Already stopped.
            }
            track?.release()
            running = false
            _playing.value = false
        }
    }
}
