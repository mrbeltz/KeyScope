package com.jonny.keyscope.audio

import android.content.Context
import android.net.Uri
import com.jonny.keyscope.dsp.KeyProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Queues a batch of files through [FileAnalyzer] off the main thread.
 *
 * A singleton for the same reason the engine is one: analysing an album should not restart every
 * time the phone is folded or unfolded.
 */
object FileAnalysisController {

    data class State(
        val running: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val current: String = "",
        val results: List<FileAnalyzer.Result> = emptyList()
    ) {
        val progress: Float get() = if (total <= 0) 0f else done.toFloat() / total
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun analyze(context: Context, uris: List<Uri>, profile: KeyProfile) {
        if (uris.isEmpty()) return
        cancel()
        val appContext = context.applicationContext

        _state.update {
            it.copy(running = true, done = 0, total = uris.size, current = "")
        }

        job = scope.launch {
            for (uri in uris) {
                ensureActive()
                val name = AudioFileDecoder.displayName(appContext, uri)
                _state.update { it.copy(current = name) }

                val result = FileAnalyzer.analyze(appContext, uri, profile)

                _state.update {
                    it.copy(done = it.done + 1, results = listOf(result) + it.results)
                }
            }
            _state.update { it.copy(running = false, current = "") }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.update { it.copy(running = false, current = "") }
    }

    fun clearResults() {
        cancel()
        _state.value = State()
    }

    /** The whole batch as CSV, for pasting into a sample-library spreadsheet. */
    fun resultsAsCsv(): String {
        val rows = _state.value.results
        if (rows.isEmpty()) return ""
        return buildString {
            appendLine(FileAnalyzer.Result.CSV_HEADER)
            rows.asReversed().forEach { appendLine(it.toCsvRow()) }
        }
    }
}
