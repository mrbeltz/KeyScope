package com.jonny.keyscope.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * A click at the detected (or tapped, or project) tempo.
 *
 * Beats are placed by sample index rather than by scheduling, so the tempo never drifts no matter
 * what the rest of the app is doing.
 */
object Metronome {

    private const val SAMPLE_RATE = 44100
    private const val CHUNK = 512
    private const val CLICK_MS = 30
    private const val ACCENT_HZ = 1760f
    private const val BEAT_HZ = 1174f

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _bpm = MutableStateFlow(0f)
    val bpm: StateFlow<Float> = _bpm.asStateFlow()

    private var thread: Thread? = null
    @Volatile private var active = false

    @Synchronized
    fun start(beatsPerMinute: Float, beatsPerBar: Int = 4) {
        stop()
        if (beatsPerMinute < 20f || beatsPerMinute > 400f) return
        active = true
        _running.value = true
        _bpm.value = beatsPerMinute
        thread = Thread({ render(beatsPerMinute, beatsPerBar) }, "KeyBro-Metronome").also {
            it.start()
        }
    }

    @Synchronized
    fun stop() {
        active = false
        thread?.join(400)
        thread = null
        _running.value = false
        _bpm.value = 0f
    }

    private fun render(beatsPerMinute: Float, beatsPerBar: Int) {
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

            val samplesPerBeat = (SAMPLE_RATE * 60.0 / beatsPerMinute)
            val clickSamples = SAMPLE_RATE * CLICK_MS / 1000
            val buffer = FloatArray(CHUNK)
            var position = 0L

            while (active) {
                for (i in 0 until CHUNK) {
                    val absolute = position + i
                    val beatIndex = (absolute / samplesPerBeat).toInt()
                    val intoBeat = absolute - (beatIndex * samplesPerBeat).toLong()
                    buffer[i] = if (intoBeat < clickSamples) {
                        val frequency = if (beatIndex % beatsPerBar == 0) ACCENT_HZ else BEAT_HZ
                        val t = intoBeat.toDouble() / SAMPLE_RATE
                        // Exponential decay makes it read as a click rather than a beep.
                        val decay = exp(-t * 90.0).toFloat()
                        (sin(2.0 * PI * frequency * t).toFloat() * decay * 0.32f)
                    } else {
                        0f
                    }
                }
                track.write(buffer, 0, CHUNK, AudioTrack.WRITE_BLOCKING)
                position += CHUNK
            }
        } catch (_: Exception) {
            // A click track is a convenience; never let it take the app down.
        } finally {
            try {
                track?.stop()
            } catch (_: IllegalStateException) {
                // Already stopped.
            }
            track?.release()
            active = false
            _running.value = false
            _bpm.value = 0f
        }
    }
}
