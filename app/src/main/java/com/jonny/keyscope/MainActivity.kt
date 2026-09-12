package com.jonny.keyscope

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jonny.keyscope.audio.Exports
import com.jonny.keyscope.audio.FileAnalysisController
import com.jonny.keyscope.audio.KeyScopeEngine
import com.jonny.keyscope.audio.ListeningService
import com.jonny.keyscope.audio.Metronome
import com.jonny.keyscope.audio.ProjectSettings
import com.jonny.keyscope.audio.ReferenceTone
import com.jonny.keyscope.ui.KeyScopeActions
import com.jonny.keyscope.ui.KeyScopeScreen
import com.jonny.keyscope.ui.KeyScopeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ProjectSettings.init(this)

        setContent {
            KeyScopeTheme {
                val context = LocalContext.current
                val state by KeyScopeEngine.state.collectAsStateWithLifecycle()
                val level by KeyScopeEngine.level.collectAsStateWithLifecycle()
                val tonePlaying by ReferenceTone.playing.collectAsStateWithLifecycle()
                val metronomeRunning by Metronome.running.collectAsStateWithLifecycle()
                val project by ProjectSettings.state.collectAsStateWithLifecycle()
                val files by FileAnalysisController.state.collectAsStateWithLifecycle()

                var hasPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val micLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    hasPermission = granted
                    if (granted) ListeningService.start(context)
                }

                val notificationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* The service runs either way; this only affects the ongoing notification. */ }

                // Storage access framework, so no storage permission is needed at all.
                val filePicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenMultipleDocuments()
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        FileAnalysisController.analyze(context, uris, state.profile)
                    }
                }

                // Saving also goes through the picker, which is what makes Drive a destination
                // without the app needing a Google sign-in or any Drive API at all.
                var pendingSave by remember { mutableStateOf<ByteArray?>(null) }
                var linkVideoId by remember { mutableStateOf<String?>(null) }
                val writeResult: (android.net.Uri?) -> Unit = { uri ->
                    val bytes = pendingSave
                    pendingSave = null
                    val written = uri != null && bytes != null && Exports.writeTo(context, uri, bytes)
                    if (uri != null) {
                        Toast.makeText(
                            context,
                            if (written) "Saved" else "Could not write there",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                val csvSaver = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("text/csv"), writeResult
                )
                val midiSaver = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("audio/midi"), writeResult
                )

                // Nobody wants the screen to sleep mid-set while the reading is still settling.
                DisposableEffect(state.listening) {
                    if (state.listening) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                }

                val actions = KeyScopeActions(
                    toggleListening = {
                        when {
                            state.listening -> ListeningService.stop(context)
                            !hasPermission -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            else -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                // Anything coming out of the speaker feeds straight back into an
                                // open mic and poisons its own reading.
                                ReferenceTone.stop()
                                Metronome.stop()
                                // Starting by hand always means "read this fresh", never "resume
                                // the average from whatever was in the room a minute ago".
                                KeyScopeEngine.resetAnalysis()
                                ListeningService.start(context)
                            }
                        }
                    },
                    reset = KeyScopeEngine::resetAnalysis,
                    setWindow = KeyScopeEngine::setWindow,
                    setProfile = KeyScopeEngine::setProfile,
                    setContinuous = KeyScopeEngine::setContinuous,
                    clearHistory = KeyScopeEngine::clearHistory,
                    setProjectKey = ProjectSettings::setKey,
                    setProjectBpm = ProjectSettings::setBpm,
                    toggleMetronome = { bpm ->
                        if (metronomeRunning) Metronome.stop() else if (bpm > 0f) {
                            ReferenceTone.stop()
                            Metronome.start(bpm)
                        }
                    },
                    playProgression = { progression ->
                        val key = state.key
                        if (key != null) {
                            Metronome.stop()
                            ReferenceTone.playChords(
                                Progressions.voicing(key, progression, state.referenceHz)
                            )
                        }
                    },
                    copy = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard.setPrimaryClip(ClipData.newPlainText("Key", state.summaryLine))
                        // Android 13 and up shows its own copy confirmation, so do not double up.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }
                    },
                    // Copy stays the one-liner; share is worth more than a repeat of it.
                    share = { Exports.shareText(context, Exports.report(state)) },
                    toggleTone = {
                        val key = state.key
                        if (tonePlaying || key == null) {
                            ReferenceTone.stop()
                        } else {
                            Metronome.stop()
                            ReferenceTone.startDrone(
                                MusicalKey.frequencyOf(key.tonic, state.referenceHz)
                            )
                        }
                    },
                    pickFiles = {
                        filePicker.launch(arrayOf("audio/*"))
                    },
                    exportCsv = {
                        val csv = FileAnalysisController.resultsAsCsv()
                        if (csv.isNotEmpty()) {
                            pendingSave = csv.toByteArray()
                            csvSaver.launch("keybro-analysis.csv")
                        }
                    },
                    shareCsv = {
                        val csv = FileAnalysisController.resultsAsCsv()
                        if (csv.isNotEmpty()) {
                            val uri = Exports.stage(context, "keybro-analysis.csv", csv.toByteArray())
                            Exports.shareFile(context, uri, "text/csv", "Key Bro analysis")
                        }
                    },
                    shareResult = { result -> Exports.shareText(context, Exports.report(result)) },
                    shareMidi = { result ->
                        if (result.chords.isEmpty()) {
                            Toast.makeText(context, "No chords in that one", Toast.LENGTH_SHORT).show()
                        } else {
                            val name = Exports.baseName(result.name) + " chords.mid"
                            val uri = Exports.stage(context, name, Exports.midiForChords(result))
                            // A content URI is what puts Quick Share in the sheet.
                            Exports.shareFile(context, uri, "audio/midi", name)
                        }
                    },
                    saveMidi = { result ->
                        if (result.chords.isEmpty()) {
                            Toast.makeText(context, "No chords in that one", Toast.LENGTH_SHORT).show()
                        } else {
                            pendingSave = Exports.midiForChords(result)
                            midiSaver.launch(Exports.baseName(result.name) + " chords.mid")
                        }
                    },
                    playLink = { id ->
                        // The embed plays out loud and the mic reads it back, so anything else
                        // coming out of the speaker has to stop first.
                        ReferenceTone.stop()
                        Metronome.stop()
                        linkVideoId = id
                        KeyScopeEngine.resetAnalysis()
                        ListeningService.start(context)
                    },
                    clearLink = {
                        linkVideoId = null
                        ListeningService.stop(context)
                    },
                    shareProgressionMidi = { progression ->
                        val key = state.key
                        if (key != null) {
                            val name = "${key.shortName} ${progression.name}.mid"
                            val bytes = Exports.midiForProgression(
                                key, progression, if (state.bpm > 0f) state.bpm else project.bpm
                            )
                            Exports.shareFile(
                                context, Exports.stage(context, name, bytes), "audio/midi", name
                            )
                        }
                    },
                    copyFilesCsv = {
                        val csv = FileAnalysisController.resultsAsCsv()
                        if (csv.isNotEmpty()) {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard.setPrimaryClip(ClipData.newPlainText("Key Bro analysis", csv))
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    clearFiles = FileAnalysisController::clearResults,
                    playScale = {
                        val key = state.key
                        if (key != null) {
                            // Tonic up to tonic, so the octave closes the phrase.
                            val degrees = key.scaleNotes.map { MusicalKey.pitchClassOf(it) }
                            var previous = -1
                            var octave = 0
                            val frequencies = degrees.map { pc ->
                                if (previous >= 0 && pc <= previous) octave++
                                previous = pc
                                MusicalKey.frequencyOf(pc, state.referenceHz) *
                                    Math.pow(2.0, octave.toDouble()).toFloat()
                            } + MusicalKey.frequencyOf(key.tonic, state.referenceHz) * 2f
                            Metronome.stop()
                            ReferenceTone.playScale(frequencies)
                        }
                    }
                )

                KeyScopeScreen(
                    state = state,
                    level = level,
                    hasPermission = hasPermission,
                    project = project,
                    tonePlaying = tonePlaying,
                    metronomeRunning = metronomeRunning,
                    files = files,
                    linkVideoId = linkVideoId,
                    actions = actions
                )
            }
        }
    }
}
