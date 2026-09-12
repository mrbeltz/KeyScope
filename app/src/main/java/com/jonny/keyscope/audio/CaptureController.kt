package com.jonny.keyscope.audio

import com.jonny.keyscope.dsp.AnalysisConfig
import com.jonny.keyscope.dsp.KeyProfile
import com.jonny.keyscope.dsp.MelodyExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Record a passage, then turn it into notes.
 *
 * Two readings come out of the same recording. Chords come from the chroma path, which handles
 * several notes at once but cannot say which octave anything was in. The melody comes from pitch
 * tracking, which gives real octaves but only works on one note at a time. Neither is a substitute
 * for the other, so both are offered and you pick whichever matches what you played.
 */
object CaptureController {

    data class Result(
        val analysis: FileAnalyzer.Result,
        val melody: List<MelodyExtractor.Note>,
        val seconds: Float
    ) {
        val hasChords: Boolean get() = analysis.chords.isNotEmpty()
        val hasMelody: Boolean get() = melody.isNotEmpty()
    }

    data class State(
        val recording: Boolean = false,
        val analysing: Boolean = false,
        val result: Result? = null,
        val message: String? = null
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun start() {
        job?.cancel()
        KeyScopeEngine.startCapture()
        _state.value = State(recording = true)
    }

    fun stop(profile: KeyProfile, referenceHz: Float) {
        val samples = KeyScopeEngine.stopCapture()
        _state.value = State(recording = false, analysing = true)

        job?.cancel()
        job = scope.launch {
            // Under a couple of seconds there is not enough for the key window, let alone chords.
            if (samples.size < AnalysisConfig.WORK_RATE * 2) {
                _state.value = State(message = "Too short — give it a few seconds of playing.")
                return@launch
            }

            val analysis = FileAnalyzer.analyzeSamples("Recording", samples, profile)
            val melody = MelodyExtractor.extract(samples, AnalysisConfig.WORK_RATE, referenceHz)
            val seconds = samples.size.toFloat() / AnalysisConfig.WORK_RATE

            _state.value = State(
                result = Result(analysis, melody, seconds),
                message = if (analysis.chords.isEmpty() && melody.isEmpty()) {
                    "Nothing came through clearly enough to turn into notes."
                } else {
                    null
                }
            )
        }
    }

    fun discard() {
        job?.cancel()
        if (KeyScopeEngine.isCapturing()) KeyScopeEngine.stopCapture()
        _state.value = State()
    }
}
