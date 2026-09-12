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

    /** Source document for each result, kept so a rename knows what to act on. */
    private val sources = HashMap<String, Uri>()

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
                sources[result.name] = uri

                _state.update {
                    it.copy(done = it.done + 1, results = listOf(result) + it.results)
                }
            }
            _state.update { it.copy(running = false, current = "") }
        }
    }

    /** Everything audio inside a folder you granted, analysed in name order. */
    fun analyzeFolder(context: Context, treeUri: Uri, profile: KeyProfile) {
        cancel()
        val appContext = context.applicationContext
        _state.update { it.copy(running = true, done = 0, total = 0, current = "Reading folder…") }

        job = scope.launch {
            val entries = FolderScanner.listAudio(appContext, treeUri)
            if (entries.isEmpty()) {
                _state.update {
                    it.copy(running = false, current = "", total = 0)
                }
                return@launch
            }
            _state.update { it.copy(total = entries.size, current = "") }

            for (entry in entries) {
                ensureActive()
                _state.update { it.copy(current = entry.name) }
                val result = FileAnalyzer.analyze(appContext, entry.uri, profile)
                sources[result.name] = entry.uri
                _state.update {
                    it.copy(done = it.done + 1, results = listOf(result) + it.results)
                }
            }
            _state.update { it.copy(running = false, current = "") }
        }
    }

    data class RenamePlan(val uri: Uri, val from: String, val to: String)

    /**
     * What renaming would do. Never applied without being shown first — this is the one operation
     * in the app that changes something you already had.
     */
    fun renamePlan(): List<RenamePlan> = _state.value.results.mapNotNull { result ->
        val uri = sources[result.name] ?: return@mapNotNull null
        val proposed = FolderScanner.proposedName(result.name, result) ?: return@mapNotNull null
        RenamePlan(uri, result.name, proposed)
    }

    /** @return how many were actually renamed. */
    fun applyRenames(context: Context, plans: List<RenamePlan>): Int {
        var renamed = 0
        val updated = _state.value.results.toMutableList()
        for (plan in plans) {
            val newName = FolderScanner.rename(context, plan.uri, plan.to) ?: continue
            renamed++
            sources.remove(plan.from)
            sources[newName] = plan.uri
            val index = updated.indexOfFirst { it.name == plan.from }
            if (index >= 0) updated[index] = updated[index].copy(name = newName)
        }
        _state.update { it.copy(results = updated) }
        return renamed
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.update { it.copy(running = false, current = "") }
    }

    fun clearResults() {
        cancel()
        sources.clear()
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
