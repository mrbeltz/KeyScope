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
    fun startDrone(frequencyHz: Float) = start(listOf(listOf(frequencyHz)), stepMs = 0)

    /** Walks up the scale and stops, tonic to tonic. */
    fun playScale(frequencies: List<Float>) = start(frequencies.map { listOf(it) }, stepMs = 340)

    /** Sounds a progression, one chord per step. */
    fun playChords(chords: List<List<Float>>, stepMs: Int = 700) = start(chords, stepMs)

    @Synchronized
    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        _playing.value = false
    }

    @Synchronized
    private fun start(steps: List<List<Float>>, stepMs: Int) {
        stop()
        if (steps.isEmpty() || steps.all { it.isEmpty() }) return
        running = true
        _playing.value = true
        thread = Thread({ render(steps, stepMs) }, "KeyBro-Tone").also { it.start() }
    }

    private fun render(steps: List<List<Float>>, stepMs: Int) {
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

            // One phase accumulator per voice, carried into the fade so nothing ever clicks.
            var phases = DoubleArray(0)
            var increments = DoubleArray(0)
            // Chords need headroom: three voices at full level would clip on their own.
            var gain = 0.17f

            var stepIndex = 0
            while (running && stepIndex < steps.size) {
                val voices = steps[stepIndex].filter { it > 0f }
                if (voices.isEmpty()) {
                    stepIndex++
                    continue
                }
                // stepMs == 0 means hold until someone stops us.
                val stepSamples = if (stepMs == 0) Int.MAX_VALUE else SAMPLE_RATE * stepMs / 1000
                var written = 0

                if (phases.size != voices.size) phases = DoubleArray(voices.size)
                increments = DoubleArray(voices.size) { 2.0 * PI * voices[it] / SAMPLE_RATE }
                gain = 0.17f / (1f + 0.55f * (voices.size - 1))

                while (running && written < stepSamples) {
                    val count = min(CHUNK, stepSamples - written)
                    for (i in 0 until count) {
                        var sample = 0f
                        for (v in voices.indices) {
                            for (h in HARMONIC_GAINS.indices) {
                                sample += HARMONIC_GAINS[h] * sin(phases[v] * (h + 1)).toFloat()
                            }
                            phases[v] += increments[v]
                        }
                        val position = written + i
                        var envelope = min(1f, position.toFloat() / attack)
                        if (stepSamples != Int.MAX_VALUE) {
                            val remaining = stepSamples - position
                            if (remaining < release) envelope *= remaining.toFloat() / release
                        }
                        buffer[i] = sample * envelope * gain
                    }
                    track.write(buffer, 0, count, AudioTrack.WRITE_BLOCKING)
                    written += count
                }
                stepIndex++
            }

            // Fade out rather than cutting, so stopping a drone does not click.
            var faded = 0
            while (increments.isNotEmpty() && faded < release) {
                val count = min(CHUNK, release - faded)
                for (i in 0 until count) {
                    var sample = 0f
                    for (v in increments.indices) {
                        for (h in HARMONIC_GAINS.indices) {
                            sample += HARMONIC_GAINS[h] * sin(phases[v] * (h + 1)).toFloat()
                        }
                        phases[v] += increments[v]
                    }
                    buffer[i] = sample * (1f - (faded + i).toFloat() / release) * gain
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
