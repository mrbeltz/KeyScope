package com.jonny.r5monitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jonny.r5monitor.ui.ConnectScreen
import com.jonny.r5monitor.ui.MonitorActions
import com.jonny.r5monitor.ui.MonitorScreen
import com.jonny.r5monitor.ui.MonitorTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        MonitorPrefs.init(this)

        val actions = MonitorActions(
            disconnect = MonitorSession::disconnect,
            record = MonitorSession::toggleRecord,
            shutter = MonitorSession::shutter,
            focus = MonitorSession::autofocus,
            setSetting = MonitorSession::setSetting,
            prefs = MonitorPrefs::update,
            liveViewChanged = MonitorSession::restartLiveView
        )

        setContent {
            MonitorTheme {
                val state by MonitorSession.state.collectAsStateWithLifecycle()
                val prefs by MonitorPrefs.state.collectAsStateWithLifecycle()
                val frameState = MonitorSession.frame.collectAsStateWithLifecycle()
                val scopeState = MonitorSession.scopes.collectAsStateWithLifecycle()

                val monitoring = state.phase == Phase.WAITING || state.phase == Phase.LIVE || state.phase == Phase.RECONNECTING

                // A monitor that dims mid-take is no monitor.
                val view = LocalView.current
                DisposableEffect(monitoring) {
                    view.keepScreenOn = monitoring
                    onDispose { view.keepScreenOn = false }
                }

                // Scopes cost CPU per frame; only pay for them while they are on screen.
                LaunchedEffect(monitoring, prefs.scopes) {
                    MonitorSession.scopesWanted = monitoring && prefs.scopes != ScopeMode.OFF
                }

                if (monitoring) {
                    BackHandler { MonitorSession.disconnect() }
                    MonitorScreen(
                        state = state,
                        frame = { frameState.value },
                        scopes = { scopeState.value },
                        prefs = prefs,
                        actions = actions,
                        onImmersive = ::setImmersive
                    )
                } else {
                    LaunchedEffect(Unit) { setImmersive(false) }
                    ConnectScreen(
                        state = state,
                        lastHost = prefs.lastHost,
                        onConnect = { MonitorSession.connect(this, it) },
                        onCancel = MonitorSession::disconnect
                    )
                }
            }
        }
    }

    private fun setImmersive(on: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (on) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
