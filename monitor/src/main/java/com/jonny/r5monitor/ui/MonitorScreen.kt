@file:OptIn(ExperimentalMaterial3Api::class)

package com.jonny.r5monitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jonny.r5monitor.LiveFrame
import com.jonny.r5monitor.MonitorState
import com.jonny.r5monitor.ScopeMode
import com.jonny.r5monitor.Scopes
import com.jonny.r5monitor.ViewPrefs

/**
 * Three arrangements, picked from the space available rather than from which screen is in use:
 *
 * - **Stacked** — picture across the top, controls scrolling underneath. The cover screen held
 *   upright, and the inner screen, which is close to square and has room for both.
 * - **Side** — picture on the left, a panel on the right. Wide and short, such as the inner screen
 *   turned sideways.
 * - **Full screen** — the cover screen turned sideways. Nothing is room for a panel, so the picture
 *   takes everything and the controls float over the right edge.
 */
private enum class Arrangement3 { STACKED, SIDE, FULL }

@Composable
fun MonitorScreen(
    state: MonitorState,
    frame: () -> LiveFrame?,
    scopes: () -> Scopes?,
    prefs: ViewPrefs,
    actions: MonitorActions,
    onImmersive: (Boolean) -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice?.id) {
        state.notice?.let { snackbar.showSnackbar(it.text) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val aspect = if (state.frameWidth > 0 && state.frameHeight > 0) {
            state.frameWidth * prefs.desqueeze / state.frameHeight
        } else {
            3f / 2f
        }
        val arrangement = when {
            maxHeight - maxWidth / aspect >= 240.dp -> Arrangement3.STACKED
            maxWidth >= 600.dp -> Arrangement3.SIDE
            else -> Arrangement3.FULL
        }
        LaunchedEffect(arrangement) { onImmersive(arrangement == Arrangement3.FULL) }

        when (arrangement) {
            Arrangement3.STACKED -> Stacked(state, frame, scopes, prefs, actions, aspect, wide = maxWidth >= 600.dp)
            Arrangement3.SIDE -> Side(state, frame, scopes, prefs, actions, panelWidth = if (maxWidth >= 840.dp) 360.dp else 300.dp)
            Arrangement3.FULL -> FullScreen(state, frame, scopes, prefs, actions)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun Picture(
    state: MonitorState,
    frame: () -> LiveFrame?,
    prefs: ViewPrefs,
    modifier: Modifier,
    showExposure: Boolean = false
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    Box(modifier) {
        LiveView(frame, prefs, Modifier.fillMaxSize(), onZoomChange = { zoom = it })
        TopHud(state, zoom, Modifier.align(Alignment.TopCenter))
        if (showExposure) BottomHud(state, Modifier.align(Alignment.BottomCenter))
        // Derived so a new frame does not recompose this; only the first one arriving does.
        val hasFrame by remember { derivedStateOf { frame() != null } }
        SignalMessage(state, hasFrame = hasFrame, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun Stacked(
    state: MonitorState,
    frame: () -> LiveFrame?,
    scopes: () -> Scopes?,
    prefs: ViewPrefs,
    actions: MonitorActions,
    aspect: Float,
    wide: Boolean
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Picture(state, frame, prefs, Modifier.fillMaxWidth().aspectRatio(aspect))
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF050507))
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
        ) {
            TransportRow(state, actions)
            ExposureStrip(state, actions)
            Spacer(Modifier.height(12.dp))
            if (wide) {
                // The unfolded screen: two columns, so the scopes and the aids sit side by side.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ScopesCard(prefs, scopes, actions)
                        FramingCard(prefs, actions)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ExposureToolsCard(prefs, actions)
                        CameraCard(state, prefs, actions)
                        AllSettings(state, actions)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScopesCard(prefs, scopes, actions)
                    ExposureToolsCard(prefs, actions)
                    FramingCard(prefs, actions)
                    CameraCard(state, prefs, actions)
                    AllSettings(state, actions)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Side(
    state: MonitorState,
    frame: () -> LiveFrame?,
    scopes: () -> Scopes?,
    prefs: ViewPrefs,
    actions: MonitorActions,
    panelWidth: Dp
) {
    Row(Modifier.fillMaxSize().safeDrawingPadding()) {
        Picture(state, frame, prefs, Modifier.weight(1f).fillMaxHeight())
        Column(
            Modifier
                .width(panelWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TransportRow(state, actions)
            ExposureStrip(state, actions)
            ScopesCard(prefs, scopes, actions)
            ExposureToolsCard(prefs, actions)
            FramingCard(prefs, actions)
            CameraCard(state, prefs, actions)
            AllSettings(state, actions)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FullScreen(
    state: MonitorState,
    frame: () -> LiveFrame?,
    scopes: () -> Scopes?,
    prefs: ViewPrefs,
    actions: MonitorActions
) {
    var toolsOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Picture(state, frame, prefs, Modifier.fillMaxSize(), showExposure = true)

        // A small waveform in the corner rather than a panel; the full set is one tap away.
        if (prefs.scopes != ScopeMode.OFF) {
            val small = Modifier.width(170.dp).height(84.dp)
            Column(
                Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 44.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (prefs.scopes != ScopeMode.HISTOGRAM) Waveform(scopes, small)
                if (prefs.scopes == ScopeMode.HISTOGRAM || prefs.scopes == ScopeMode.BOTH) Histogram(scopes, small)
            }
        }

        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RoundTool("Tools", Icons.Filled.Tune) { toolsOpen = true }
            if (state.canFocus) RoundTool("AF", Icons.Filled.CenterFocusStrong, actions.focus)
            if (state.canRecord) RecordButton(state.recording, actions.record, size = 60)
            if (state.canShutter) RoundTool("Photo", Icons.Filled.PhotoCamera, actions.shutter)
            RoundTool("Leave", Icons.Filled.Close, actions.disconnect)
        }
    }

    if (toolsOpen) {
        ModalBottomSheet(
            onDismissRequest = { toolsOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExposureStrip(state, actions)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ScopesCard(prefs, scopes, actions)
                        ExposureToolsCard(prefs, actions)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FramingCard(prefs, actions)
                        CameraCard(state, prefs, actions)
                    }
                }
            }
        }
    }
}
