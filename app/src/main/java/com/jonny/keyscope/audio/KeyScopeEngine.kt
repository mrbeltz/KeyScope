package com.jonny.keyscope.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import android.util.Log
import com.jonny.keyscope.MusicalKey
import com.jonny.keyscope.dsp.AnalysisConfig
import com.jonny.keyscope.dsp.ChordDetector
import com.jonny.keyscope.dsp.ChordSpan
import com.jonny.keyscope.dsp.ChromaAccumulator
import com.jonny.keyscope.dsp.DetectedChord
import com.jonny.keyscope.dsp.ChromaExtractor
import com.jonny.keyscope.dsp.Decimator
import com.jonny.keyscope.dsp.KeyDetector
import com.jonny.keyscope.dsp.KeyProfile
import com.jonny.keyscope.dsp.TempoTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Owns the microphone and the analysis thread.
 *
 * Deliberately a singleton rather than a ViewModel: on a foldable the activity is recreated every
 * time the device opens or closes, and the listening session has to survive that untouched.
 */
object KeyScopeEngine {

    private const val TAG = "KeyScopeEngine"

    private const val INPUT_RATE = 44100

    // Shared with the file path, so a sample and the room it is played in are measured identically.
    private const val WORK_RATE = AnalysisConfig.WORK_RATE
    private const val DECIMATION = INPUT_RATE / WORK_RATE
    private const val FFT_SIZE = AnalysisConfig.FFT_SIZE
    private const val HOP = AnalysisConfig.HOP
    private const val READ_FRAMES = 2048

    /** Frames below this RMS are treated as silence and never enter the average. */
    private const val SILENCE_RMS = 0.0015f

    /** Hops the winner must survive before the readout is called locked. */
    private const val LOCK_HOPS = 11                        // about two seconds

    /** Two minutes of captured audio is about five megabytes at the analysis rate. */
    private const val MAX_CAPTURE_SECONDS = 120

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    /** Split out from [state] because the meter updates far more often than anything else. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private var thread: Thread? = null

    @Volatile private var running = false
    @Volatile private var resetRequested = false
    @Volatile private var pendingWindow: AnalysisWindow? = null
    @Volatile private var continuousListening = false
    @Volatile private var capturing = false

    private var captureBuffer = FloatArray(0)
    private var captureLength = 0

    private val _captureSeconds = MutableStateFlow(0f)
    val captureSeconds: StateFlow<Float> = _captureSeconds.asStateFlow()

    private val detector = KeyDetector()
    private val chordDetector = ChordDetector()

    fun isRunning(): Boolean = running

    @Synchronized
    fun start(context: Context) {
        if (running) return
        running = true
        val appContext = context.applicationContext
        _state.update { it.copy(listening = true, error = null, autoStopped = false) }
        thread = Thread({ runLoop(appContext) }, "KeyScope-Audio").also {
            it.priority = Thread.MAX_PRIORITY
            it.start()
        }
    }

    @Synchronized
    fun stop() {
        running = false
        thread?.join(1500)
        thread = null
        _level.value = 0f
        _state.update { it.copy(listening = false) }
    }

    /** Throws away the accumulated average and starts listening fresh. */
    fun resetAnalysis() {
        resetRequested = true
        _state.update {
            it.copy(
                key = null, confidence = 0f, locked = false, windowFill = 0f,
                autoStopped = false, chord = null, chordSpans = emptyList()
            )
        }
    }

    /**
     * Starts keeping the audio as well as analysing it, so a passage can be turned into notes
     * afterwards. Capped at two minutes, which at the analysis rate is about five megabytes.
     */
    @Synchronized
    fun startCapture() {
        captureBuffer = FloatArray(MAX_CAPTURE_SECONDS * WORK_RATE)
        captureLength = 0
        capturing = true
        _captureSeconds.value = 0f
    }

    /** Stops keeping audio and hands back exactly what was recorded. */
    @Synchronized
    fun stopCapture(): FloatArray {
        capturing = false
        val taken = captureBuffer.copyOf(captureLength)
        captureBuffer = FloatArray(0)
        captureLength = 0
        _captureSeconds.value = 0f
        return taken
    }

    fun isCapturing(): Boolean = capturing

    /** When off (the default), the mic releases itself as soon as a lock lands. */
    fun setContinuous(enabled: Boolean) {
        continuousListening = enabled
        _state.update { it.copy(continuousListening = enabled) }
    }

    fun setWindow(window: AnalysisWindow) {
        pendingWindow = window
        _state.update { it.copy(window = window) }
    }

    fun setProfile(profile: KeyProfile) {
        detector.profile = profile
        resetRequested = true
        _state.update { it.copy(profile = profile) }
    }

    fun clearHistory() = _state.update { it.copy(history = emptyList()) }

    // ---------------------------------------------------------------- audio thread

    private fun runLoop(context: Context) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        var record: AudioRecord? = null
        try {
            val opened = openRecorder(context)
            record = opened.first
            _state.update { it.copy(inputSource = opened.second) }
            record.startRecording()

            val chroma = ChromaExtractor(WORK_RATE, FFT_SIZE)
            val tempo = TempoTracker(WORK_RATE)
            var accumulator = ChromaAccumulator(framesFor(_state.value.window))
            val decimator = Decimator(INPUT_RATE, DECIMATION)

            val readBuffer = FloatArray(READ_FRAMES)
            val decimated = FloatArray(READ_FRAMES / DECIMATION + 8)
            val analysis = FloatArray(FFT_SIZE)
            val hopBuffer = FloatArray(HOP)
            var hopFill = 0

            var lastWinner: MusicalKey? = null
            var stableHops = 0
            var smoothedConfidence = 0f

            while (running) {
                val read = record.read(readBuffer, 0, READ_FRAMES, AudioRecord.READ_BLOCKING)
                if (read <= 0) continue

                var sumSquares = 0f
                for (i in 0 until read) sumSquares += readBuffer[i] * readBuffer[i]
                _level.value = (sqrt(sumSquares / read) * 6f).coerceIn(0f, 1f)

                val requested = pendingWindow
                if (requested != null) {
                    pendingWindow = null
                    accumulator = ChromaAccumulator(framesFor(requested))
                    stableHops = 0
                }
                if (resetRequested) {
                    resetRequested = false
                    accumulator.reset()
                    tempo.reset()
                    chordDetector.reset()
                    lastWinner = null
                    stableHops = 0
                    smoothedConfidence = 0f
                }

                val decimatedCount = decimator.process(readBuffer, read, decimated)
                tempo.feed(decimated, decimatedCount)

                if (capturing) {
                    val room = captureBuffer.size - captureLength
                    val take = minOf(room, decimatedCount)
                    if (take > 0) {
                        System.arraycopy(decimated, 0, captureBuffer, captureLength, take)
                        captureLength += take
                        _captureSeconds.value = captureLength.toFloat() / WORK_RATE
                    }
                    if (take < decimatedCount) capturing = false  // hit the cap
                }

                var offset = 0
                while (offset < decimatedCount) {
                    val take = minOf(HOP - hopFill, decimatedCount - offset)
                    System.arraycopy(decimated, offset, hopBuffer, hopFill, take)
                    hopFill += take
                    offset += take
                    if (hopFill < HOP) continue
                    hopFill = 0

                    System.arraycopy(analysis, HOP, analysis, 0, FFT_SIZE - HOP)
                    System.arraycopy(hopBuffer, 0, analysis, FFT_SIZE - HOP, HOP)

                    val silent = rmsOf(analysis) < SILENCE_RMS
                    if (!silent && chroma.process(analysis)) {
                        accumulator.add(chroma.profile)
                    }

                    val profile36 = accumulator.snapshot()
                    val tuning = ChromaAccumulator.estimateTuningCents(profile36)
                    val chroma12 = ChromaAccumulator.fold(profile36, tuning)

                    // Chords come from this frame alone, folded with the tuning the long average
                    // has already worked out. Free: the chroma was computed either way.
                    val chordNow = if (silent) {
                        null
                    } else {
                        chordDetector.track(
                            ChromaAccumulator.fold(chroma.profile, tuning),
                            _state.value.key
                        )
                    }

                    val ranked = detector.rank(chroma12)
                    val rawConfidence = detector.confidence(ranked)
                    smoothedConfidence += (rawConfidence - smoothedConfidence) * 0.25f

                    val winner = ranked.firstOrNull()?.key
                    if (winner != null && winner == lastWinner) {
                        stableHops++
                    } else {
                        lastWinner = winner
                        stableHops = 0
                    }

                    val fill = accumulator.fill
                    val locked = winner != null && stableHops >= LOCK_HOPS &&
                        smoothedConfidence > 0.35f && fill > 0.55f

                    val peak = chroma12.maxOrNull() ?: 0f
                    val bars = if (peak > 0f) chroma12.map { it / peak } else chroma12.toList()
                    val confidenceNow = smoothedConfidence
                    val bpmNow = tempo.bpm

                    // A lock is the answer, so unless asked to keep going, let the mic go. The
                    // loop exits through its finally block, which drops listening to false and
                    // lets the service tear its notification down.
                    // Never cut a recording short just because the key settled.
                    val releaseMic = locked && !continuousListening && !capturing

                    _state.update { previous ->
                        val history = if (locked && winner != null &&
                            previous.history.firstOrNull()?.key != winner
                        ) {
                            val entry = HistoryEntry(
                                winner, System.currentTimeMillis(), bpmNow, confidenceNow
                            )
                            (listOf(entry) + previous.history).take(20)
                        } else {
                            previous.history
                        }

                        previous.copy(
                            key = winner,
                            confidence = confidenceNow,
                            locked = locked,
                            alternates = ranked.drop(1).take(3).map { it.key to it.score },
                            chroma = bars,
                            tuningCents = tuning,
                            bpm = bpmNow,
                            bpmConfidence = tempo.confidence,
                            windowFill = fill,
                            silent = silent,
                            autoStopped = releaseMic,
                            chord = chordNow,
                            chordSpans = updateSpans(previous.chordSpans, chordNow),
                            history = history
                        )
                    }

                    if (releaseMic) {
                        running = false
                        break
                    }
                }
            }
        } catch (e: SecurityException) {
            fail("Microphone permission was revoked.", e)
        } catch (e: IllegalStateException) {
            fail("Could not open the microphone. Another app may be using it.", e)
        } catch (e: Exception) {
            fail(e.message ?: "Audio capture failed.", e)
        } finally {
            try {
                record?.stop()
            } catch (_: IllegalStateException) {
                // Already stopped; nothing to unwind.
            }
            record?.release()
            running = false
            _level.value = 0f
            _state.update { it.copy(listening = false) }
        }
    }

    private fun fail(message: String, e: Throwable) {
        Log.e(TAG, message, e)
        _state.update { it.copy(error = message) }
    }

    /**
     * Extends the current span while the chord holds, and opens a new one when it changes. Only
     * the last sixteen are kept; past that it stops being a progression and starts being a log.
     */
    private fun updateSpans(spans: List<ChordSpan>, chord: DetectedChord?): List<ChordSpan> {
        if (chord == null) return spans
        val now = System.currentTimeMillis()
        val last = spans.lastOrNull()
        return if (last != null && last.chord.root == chord.root &&
            last.chord.quality == chord.quality
        ) {
            spans.dropLast(1) + last.copy(endMillis = now)
        } else {
            (spans + ChordSpan(chord, now, now)).takeLast(16)
        }
    }

    private fun rmsOf(buffer: FloatArray): Float {
        var acc = 0f
        for (v in buffer) acc += v * v
        return sqrt(acc / buffer.size)
    }

    private fun framesFor(window: AnalysisWindow): Int = max(4, window.seconds * WORK_RATE / HOP)

    /**
     * Prefers UNPROCESSED so the phone voice pipeline (AGC, noise suppression, beamforming) does
     * not reshape the spectrum before analysis. Those are exactly the wrong processors for music.
     */
    @SuppressLint("MissingPermission")
    private fun openRecorder(context: Context): Pair<AudioRecord, String> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val unprocessedSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

        val candidates = ArrayList<Pair<Int, String>>()
        if (unprocessedSupported) candidates.add(MediaRecorder.AudioSource.UNPROCESSED to "Unprocessed")
        candidates.add(MediaRecorder.AudioSource.VOICE_RECOGNITION to "Voice recognition")
        candidates.add(MediaRecorder.AudioSource.MIC to "Microphone")

        val minBuffer = AudioRecord.getMinBufferSize(
            INPUT_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT
        )
        val bufferBytes = max(minBuffer * 4, INPUT_RATE * 2)

        var lastError: Exception? = null
        for ((source, label) in candidates) {
            try {
                val created = AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(INPUT_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferBytes)
                    .build()
                if (created.state == AudioRecord.STATE_INITIALIZED) return created to label
                created.release()
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IllegalStateException("No usable audio source", lastError)
    }
}
