package com.jonny.keyscope

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
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
import com.jonny.keyscope.audio.KeyScopeEngine
import com.jonny.keyscope.audio.ListeningService
import com.jonny.keyscope.audio.ReferenceTone
import com.jonny.keyscope.ui.KeyScopeScreen
import com.jonny.keyscope.ui.KeyScopeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            KeyScopeTheme {
                val context = LocalContext.current
                val state by KeyScopeEngine.state.collectAsStateWithLifecycle()
                val level by KeyScopeEngine.level.collectAsStateWithLifecycle()
                val tonePlaying by ReferenceTone.playing.collectAsStateWithLifecycle()

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

                // Nobody wants the screen to sleep mid-set while the reading is still settling.
                DisposableEffect(state.listening) {
                    if (state.listening) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                }

                KeyScopeScreen(
                    state = state,
                    level = level,
                    hasPermission = hasPermission,
                    onToggleListening = {
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
                                // A tone playing into an open mic is a feedback loop that would
                                // poison its own reading.
                                ReferenceTone.stop()
                                // Starting by hand always means "read this fresh", never "resume
                                // the average from whatever was in the room a minute ago".
                                KeyScopeEngine.resetAnalysis()
                                ListeningService.start(context)
                            }
                        }
                    },
                    onReset = KeyScopeEngine::resetAnalysis,
                    onWindowChange = KeyScopeEngine::setWindow,
                    onProfileChange = KeyScopeEngine::setProfile,
                    onContinuousChange = KeyScopeEngine::setContinuous,
                    onClearHistory = KeyScopeEngine::clearHistory,
                    tonePlaying = tonePlaying,
                    onCopy = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard.setPrimaryClip(ClipData.newPlainText("Key", state.summaryLine))
                        // Android 13 and up shows its own copy confirmation, so do not double up.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShare = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, state.summaryLine)
                        }
                        context.startActivity(Intent.createChooser(share, null))
                    },
                    onToneToggle = {
                        val key = state.key
                        if (tonePlaying || key == null) {
                            ReferenceTone.stop()
                        } else {
                            ReferenceTone.startDrone(
                                MusicalKey.frequencyOf(key.tonic, state.referenceHz)
                            )
                        }
                    },
                    onPlayScale = {
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
                            ReferenceTone.playScale(frequencies)
                        }
                    }
                )
            }
        }
    }
}
