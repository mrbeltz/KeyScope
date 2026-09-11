package com.jonny.keyscope

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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
                                ListeningService.start(context)
                            }
                        }
                    },
                    onReset = KeyScopeEngine::resetAnalysis,
                    onWindowChange = KeyScopeEngine::setWindow,
                    onProfileChange = KeyScopeEngine::setProfile,
                    onClearHistory = KeyScopeEngine::clearHistory
                )
            }
        }
    }
}
