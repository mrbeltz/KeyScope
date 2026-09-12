package com.jonny.keyscope.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jonny.keyscope.Mode
import com.jonny.keyscope.Progression
import com.jonny.keyscope.Progressions
import com.jonny.keyscope.TapTempo
import com.jonny.keyscope.MusicalKey
import com.jonny.keyscope.audio.AnalysisWindow
import com.jonny.keyscope.audio.CaptureController
import com.jonny.keyscope.audio.EngineState
import com.jonny.keyscope.audio.Exports
import com.jonny.keyscope.audio.FileAnalyzer
import com.jonny.keyscope.audio.FileAnalysisController
import com.jonny.keyscope.audio.HistoryEntry
import com.jonny.keyscope.audio.Project
import com.jonny.keyscope.dsp.KeyProfile
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TWO_PANE_WIDTH_DP = 720

/** Everything the screen can ask the app to do, bundled so the signature stays readable. */
class KeyScopeActions(
    val start: () -> Unit,
    val stop: () -> Unit,
    val reset: () -> Unit,
    val setWindow: (AnalysisWindow) -> Unit,
    val setProfile: (KeyProfile) -> Unit,
    val setContinuous: (Boolean) -> Unit,
    val clearHistory: () -> Unit,
    val copy: () -> Unit,
    val share: () -> Unit,
    val toggleTone: () -> Unit,
    val playScale: () -> Unit,
    val playProgression: (Progression) -> Unit,
    val setProjectKey: (MusicalKey?) -> Unit,
    val setProjectBpm: (Float) -> Unit,
    val toggleMetronome: (Float) -> Unit,
    val pickFiles: () -> Unit,
    val copyFilesCsv: () -> Unit,
    val clearFiles: () -> Unit,
    val exportCsv: () -> Unit,
    val shareCsv: () -> Unit,
    val shareResult: (FileAnalyzer.Result) -> Unit,
    val shareMidi: (FileAnalyzer.Result) -> Unit,
    val saveMidi: (FileAnalyzer.Result) -> Unit,
    val shareProgressionMidi: (Progression) -> Unit,
    val pickFolder: () -> Unit,
    val previewRenames: () -> Unit,
    val applyRenames: () -> Unit,
    val cancelRenames: () -> Unit,
    val startRecording: () -> Unit,
    val stopRecording: () -> Unit,
    val discardRecording: () -> Unit,
    val shareCaptureChords: () -> Unit,
    val shareCaptureMelody: () -> Unit,
    val saveCaptureMelody: () -> Unit
)

@Composable
fun KeyScopeScreen(
    state: EngineState,
    level: Float,
    hasPermission: Boolean,
    project: Project,
    tonePlaying: Boolean,
    metronomeRunning: Boolean,
    files: FileAnalysisController.State,
    capture: CaptureController.State,
    captureSeconds: Float,
    renamePlan: List<FileAnalysisController.RenamePlan>,
    actions: KeyScopeActions
) {
    var transposeTarget by remember(state.key) { mutableStateOf<Int?>(null) }
    val resultActions: @Composable () -> Unit = {
        ResultActions(state, tonePlaying, actions.copy, actions.share, actions.toggleTone, actions.playScale)
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp)
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val twoPane = maxWidth.value >= TWO_PANE_WIDTH_DP

                Column(Modifier.fillMaxSize()) {
                    Header(state, level)
                    Spacer(Modifier.height(12.dp))

                    if (twoPane) {
                        // Unfolded: the readout stays put on the left while the meters and the
                        // log scroll independently on the right.
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                KeyHeroCard(state, hasPermission)
                                resultActions()
                                ProjectFitCard(state, project, actions)
                                ScaleCard(state.key)
                                ChordNowCard(state)
                                ChordsCard(state.key)
                                ProgressionsCard(state.key, actions.playProgression, actions.shareProgressionMidi)
                                CompatibleCard(state.key)
                                TransposeCard(state, transposeTarget) { transposeTarget = it }
                            }
                            Column(
                                Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ChromaCard(state)
                                TempoCard(state, project, metronomeRunning, actions)
                                CaptureCard(
                                    capture, captureSeconds, hasPermission, actions.startRecording,
                                    actions.stopRecording, actions.discardRecording, actions.shareCaptureChords,
                                    actions.shareCaptureMelody, actions.saveCaptureMelody
                                )
                                FilesCard(
                                files, actions.pickFiles, actions.copyFilesCsv, actions.exportCsv,
                                actions.shareCsv, actions.clearFiles, actions.setProjectKey,
                                actions.shareResult, actions.shareMidi, actions.saveMidi,
                                actions.pickFolder, renamePlan, actions.previewRenames,
                                actions.applyRenames, actions.cancelRenames
                            )
                                ControlsCard(state, actions)
                                HistoryCard(state.history, actions.clearHistory)
                                Spacer(Modifier.height(88.dp))
                            }
                        }
                    } else {
                        Column(
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            KeyHeroCard(state, hasPermission)
                            resultActions()
                            ProjectFitCard(state, project, actions)
                            ChromaCard(state)
                            ScaleCard(state.key)
                            ChordNowCard(state)
                            ChordsCard(state.key)
                                ProgressionsCard(state.key, actions.playProgression, actions.shareProgressionMidi)
                            CompatibleCard(state.key)
                            TransposeCard(state, transposeTarget) { transposeTarget = it }
                            TempoCard(state, project, metronomeRunning, actions)
                            CaptureCard(
                                capture, captureSeconds, hasPermission, actions.startRecording,
                                actions.stopRecording, actions.discardRecording, actions.shareCaptureChords,
                                actions.shareCaptureMelody, actions.saveCaptureMelody
                            )
                            FilesCard(
                                files, actions.pickFiles, actions.copyFilesCsv, actions.exportCsv,
                                actions.shareCsv, actions.clearFiles, actions.setProjectKey,
                                actions.shareResult, actions.shareMidi, actions.saveMidi,
                                actions.pickFolder, renamePlan, actions.previewRenames,
                                actions.applyRenames, actions.cancelRenames
                            )
                            ControlsCard(state, actions)
                            HistoryCard(state.history, actions.clearHistory)
                            Spacer(Modifier.height(96.dp))
                        }
                    }
                }

                Transport(
                    listening = state.listening,
                    level = level,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    onStart = actions.start,
                    onStop = actions.stop
                )
            }
        }
    }
}

// ------------------------------------------------------------------ header

@Composable
private fun Header(state: EngineState, level: Float) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Key Bro",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        if (state.listening) {
            Text(
                state.inputSource.uppercase(Locale.US),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
        }
        LevelMeter(level)
    }
}

@Composable
private fun LevelMeter(level: Float) {
    val animated by animateFloatAsState(level, tween(90), label = "level")
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.height(20.dp)
    ) {
        repeat(10) { index ->
            val threshold = (index + 1) / 10f
            val lit = animated >= threshold
            val color = when {
                !lit -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                index >= 8 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
            Box(
                Modifier
                    .width(4.dp)
                    .height((7 + index * 1.3f).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

// ------------------------------------------------------------------ hero

@Composable
private fun KeyHeroCard(state: EngineState, hasPermission: Boolean) {
    val key = state.key
    val confidence by animateFloatAsState(state.confidence, tween(250), label = "confidence")
    val accent by animateColorAsState(
        when {
            state.locked -> MaterialTheme.colorScheme.primary
            state.confidence > 0.35f -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        tween(400),
        label = "accent"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                statusLine(state, hasPermission),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))

            LockReveal(locked = state.locked, accent = accent) {
                Text(
                    key?.shortName ?: "--",
                    style = MaterialTheme.typography.displayLarge,
                    color = accent,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                key?.name ?: "waiting for audio",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(key?.camelot ?: "--", "CAMELOT", accent)
                Badge(key?.openKey ?: "--", "OPEN KEY", MaterialTheme.colorScheme.secondary)
                if (state.locked) {
                    Badge("LOCK", "STABLE", MaterialTheme.colorScheme.primary, Icons.Filled.Lock)
                }
            }

            Spacer(Modifier.height(18.dp))
            LabeledBar("Confidence", confidence, accent)
            Spacer(Modifier.height(8.dp))
            LabeledBar(
                "Window ${state.window.seconds}s",
                state.windowFill,
                MaterialTheme.colorScheme.outline
            )

            if (state.listening && abs(state.tuningCents) > 4f) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Tuning ${formatCents(state.tuningCents)} - reference A = " +
                        "${state.referenceHz.roundToInt()} Hz",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            if (state.alternates.isNotEmpty() && state.listening) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Runners-up: " + state.alternates.joinToString("   ") {
                        "${it.first.shortName} ${(it.second * 100).roundToInt()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * The moment the reading settles. Two rings bloom outward and fade while the key itself springs
 * up and settles back, with a haptic tick on the same frame so the answer registers even if you
 * are looking at the instrument rather than the phone.
 */
@Composable
private fun LockReveal(locked: Boolean, accent: Color, content: @Composable () -> Unit) {
    val bloom = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(locked) {
        if (!locked) {
            bloom.snapTo(0f)
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        launch {
            bloom.snapTo(0f)
            bloom.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
        scale.animateTo(1.14f, tween(150, easing = FastOutSlowInEasing))
        scale.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
    }

    Box(contentAlignment = Alignment.Center) {
        if (bloom.value > 0f && bloom.value < 1f) {
            Canvas(Modifier.matchParentSize()) {
                val maxRadius = size.minDimension * 1.15f
                // Two rings, the second trailing the first, so it reads as a pulse not a blip.
                for (index in 0 until 2) {
                    val offset = index * 0.22f
                    val progress = (bloom.value - offset) / (1f - offset)
                    if (progress <= 0f || progress >= 1f) continue
                    drawCircle(
                        color = accent.copy(alpha = 0.5f * (1f - progress) * (1f - progress)),
                        radius = maxRadius * (0.35f + 0.65f * progress),
                        style = Stroke(width = (7f * (1f - progress)).coerceAtLeast(1f))
                    )
                }
            }
        }
        Box(Modifier.scale(scale.value), contentAlignment = Alignment.Center) { content() }
    }
}

/** Copy, share, and sound the result -- the three things worth doing once a reading lands. */
@Composable
private fun ResultActions(
    state: EngineState,
    tonePlaying: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onToneToggle: () -> Unit,
    onPlayScale: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        ActionButton(Icons.Filled.ContentCopy, "Copy", Modifier.weight(1f), onClick = onCopy)
        ActionButton(Icons.Filled.Share, "Share", Modifier.weight(1f), onClick = onShare)
        ActionButton(
            if (tonePlaying) Icons.Filled.StopCircle else Icons.Filled.VolumeUp,
            if (tonePlaying) "Stop" else "Tonic",
            Modifier.weight(1f),
            highlighted = tonePlaying,
            onClick = onToneToggle
        )
        ActionButton(Icons.Filled.PlayArrow, "Scale", Modifier.weight(1f), onClick = onPlayScale)
    }
    if (state.listening) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Playing a tone while the mic is open will feed back into the reading.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (highlighted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/**
 * What it takes to get from the detected key to a target one: the semitone move, and the tempo
 * that a varispeed pitch drags along with it.
 */
@Composable
private fun TransposeCard(state: EngineState, target: Int?, onTargetChange: (Int?) -> Unit) {
    val key = state.key ?: return
    SectionCard("Transpose to") {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (pc in 0 until 12) {
                val selected = target == pc
                val inKey = pc == key.tonic
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            when {
                                selected -> MaterialTheme.colorScheme.primary
                                inKey -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .clickable { onTargetChange(if (selected) null else pc) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        MusicalKey.CHROMA_LABELS[pc],
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (target == null) {
            Text(
                "Pick a target tonic. Mode stays ${if (key.mode == Mode.MAJOR) "major" else "minor"} " +
                    "-- pitching cannot turn one into the other, so route through " +
                    "${key.relative.shortName} if you need to change it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }

        val targetKey = MusicalKey(target, key.mode)
        val semitones = key.semitonesTo(targetKey)
        val percent = key.varispeedPercentTo(targetKey)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (semitones == 0) "Same key" else {
                        (if (semitones > 0) "+$semitones" else "$semitones") +
                            " semitone" + (if (abs(semitones) == 1) "" else "s")
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "${key.shortName} ${key.camelot}  ->  ${targetKey.shortName} ${targetKey.camelot}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Varispeed: ${formatSigned(percent)}%" +
                if (state.bpm > 0f) {
                    "  ->  ${String.format(Locale.US, "%.1f", state.bpm * (1f + percent / 100f))} BPM"
                } else "",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "With key lock on, tempo is unchanged and only the pitch moves.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The seven chords of the key, for writing or jamming over the top. */
@Composable
private fun ChordsCard(key: MusicalKey?) {
    SectionCard("Chords in this key") {
        if (key == null) {
            Text(
                "The diatonic chords will appear here once a key is detected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            key.diatonicChords.forEachIndexed { degree, chord ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (degree == 0) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        chord.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontSize = 13.sp,
                        fontWeight = if (degree == 0) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        chord.numeral,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatSigned(value: Float): String =
    (if (value >= 0f) "+" else "") + String.format(Locale.US, "%.2f", value)

private fun statusLine(state: EngineState, hasPermission: Boolean): String = when {
    !hasPermission -> "MICROPHONE ACCESS NEEDED"
    state.autoStopped && !state.listening -> "LOCKED - MIC RELEASED"
    !state.listening -> "PRESS START"
    state.silent -> "LISTENING - NO AUDIO"
    state.windowFill < 0.4f -> "GATHERING"
    state.locked -> "LOCKED"
    else -> "ANALYSING"
}

@Composable
private fun Badge(value: String, caption: String, tint: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(value, style = MaterialTheme.typography.titleMedium, color = tint, fontWeight = FontWeight.Bold)
        }
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LabeledBar(label: String, value: Float, color: Color) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                "${(value.coerceIn(0f, 1f) * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { value.coerceIn(0f, 1f) },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

// ------------------------------------------------------------------ chroma

@Composable
private fun ChromaCard(state: EngineState) {
    val inScale = state.key?.let { scalePitchClasses(it) } ?: emptySet()
    SectionCard("Pitch class energy") {
        Row(
            Modifier
                .fillMaxWidth()
                .height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            state.chroma.forEachIndexed { pc, raw ->
                val value by animateFloatAsState(raw.coerceIn(0f, 1f), tween(160), label = "bar$pc")
                val isTonic = state.key?.tonic == pc
                val color = when {
                    isTonic -> MaterialTheme.colorScheme.primary
                    pc in inScale -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.outline
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(value.coerceAtLeast(0.02f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(color)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        MusicalKey.CHROMA_LABELS[pc],
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pc in inScale) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Highlighted bars are the notes of the detected scale; the brightest one is the tonic.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun scalePitchClasses(key: MusicalKey): Set<Int> {
    val steps = if (key.mode == Mode.MAJOR) {
        intArrayOf(0, 2, 4, 5, 7, 9, 11)
    } else {
        intArrayOf(0, 2, 3, 5, 7, 8, 10)
    }
    return steps.map { Math.floorMod(key.tonic + it, 12) }.toSet()
}

// ------------------------------------------------------------------ scale / compatible

@Composable
private fun ScaleCard(key: MusicalKey?) {
    SectionCard("Scale") {
        if (key == null) {
            Text(
                "The notes of the detected scale will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            key.scaleNotes.forEachIndexed { degree, note ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (degree == 0) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        note,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (degree == 0) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        "${degree + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Relative ${key.relative.name} - same notes, different centre of gravity.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompatibleCard(key: MusicalKey?) {
    SectionCard("Mixes cleanly with") {
        if (key == null) {
            Text(
                "Harmonically adjacent keys will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            key.compatible.forEach { other ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(vertical = 10.dp)
                ) {
                    Text(other.shortName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        other.camelot,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ tempo / controls / history

@Composable
private fun TempoCard(
    state: EngineState,
    project: Project,
    metronomeRunning: Boolean,
    actions: KeyScopeActions
) {
    val tapper = remember { TapTempo() }
    var tapped by remember { mutableStateOf(0f) }
    val effective = if (tapped > 0f) tapped else state.bpm

    SectionCard("Tempo") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.bpm > 0f) String.format(Locale.US, "%.1f", state.bpm) else "--",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(6.dp))
            Text("BPM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Column(Modifier.width(120.dp)) {
                LabeledBar("Certainty", state.bpmConfidence, MaterialTheme.colorScheme.tertiary)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionButton(Icons.Filled.TouchApp, "Tap", Modifier.weight(1f)) {
                tapper.tap()?.let { tapped = it }
            }
            ActionButton(
                if (metronomeRunning) Icons.Filled.StopCircle else Icons.Filled.Timer,
                if (metronomeRunning) "Stop" else "Click",
                Modifier.weight(1f),
                highlighted = metronomeRunning
            ) { actions.toggleMetronome(effective) }
            ActionButton(Icons.Filled.Album, "To project", Modifier.weight(1f)) {
                if (effective > 0f) actions.setProjectBpm(effective)
            }
        }

        if (tapped > 0f) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Tapped ${String.format(Locale.US, "%.1f", tapped)} BPM" +
                    when {
                        state.bpm <= 0f -> ""
                        TapTempo.isSameTempo(tapped, state.bpm) -> " — agrees with the detector."
                        TapTempo.isOctaveOf(tapped, state.bpm) ->
                            " — same tempo as the detector, an octave apart. Yours is the right one."
                        else -> " — the detector disagrees, so trust your taps."
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            TextButton(onClick = { tapper.reset(); tapped = 0f }) { Text("Clear taps") }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            if (project.bpm > 0f && effective > 0f) {
                val drift = (effective / project.bpm - 1f) * 100f
                "Project sits at ${String.format(Locale.US, "%.1f", project.bpm)} BPM — " +
                    "this is ${formatSigned(drift)}% off it."
            } else {
                "Onset autocorrelation over the last 12 seconds. Half or double time is the usual failure mode, which is what the tap button is for."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Whether the thing you just heard fits what you are working on, and what it takes to make it.
 * The transpose maths already existed; this just answers the question without being asked.
 */
@Composable
private fun ProjectFitCard(state: EngineState, project: Project, actions: KeyScopeActions) {
    var editing by remember { mutableStateOf(false) }

    SectionCard("Project") {
        if (!project.isSet && !editing) {
            Text(
                "Set the key and tempo you are working in, and every reading will tell you whether it fits.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { editing = true }) { Text("Set project key") }
            return@SectionCard
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    project.key?.name ?: "No key set",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (project.bpm > 0f) {
                        "${String.format(Locale.US, "%.1f", project.bpm)} BPM" +
                            (project.key?.let { "  ·  ${it.camelot}" } ?: "")
                    } else {
                        "No tempo set"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { editing = !editing }) { Text(if (editing) "Done" else "Change") }
        }

        if (editing) {
            Spacer(Modifier.height(10.dp))
            TonicPicker(
                selected = project.key?.tonic,
                onSelect = { pc ->
                    val mode = project.key?.mode ?: Mode.MINOR
                    actions.setProjectKey(if (project.key?.tonic == pc) null else MusicalKey(pc, mode))
                }
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mode.entries.forEach { mode ->
                    FilterChip(
                        selected = project.key?.mode == mode,
                        onClick = {
                            val tonic = project.key?.tonic ?: state.key?.tonic ?: 0
                            actions.setProjectKey(MusicalKey(tonic, mode))
                        },
                        label = { Text(if (mode == Mode.MAJOR) "Major" else "Minor") }
                    )
                }
                state.key?.let { detected ->
                    FilterChip(
                        selected = false,
                        onClick = { actions.setProjectKey(detected) },
                        label = { Text("Use ${detected.shortName}") }
                    )
                }
            }
            if (project.isSet) {
                TextButton(onClick = {
                    actions.setProjectKey(null)
                    actions.setProjectBpm(0f)
                    editing = false
                }) { Text("Clear project") }
            }
        }

        val detected = state.key
        val target = project.key
        if (detected != null && target != null) {
            Spacer(Modifier.height(14.dp))
            val semitones = detected.semitonesTo(target)
            val percent = detected.varispeedPercentTo(target)
            val fits = semitones == 0
            val close = detected.compatible.contains(target)

            Text(
                when {
                    fits -> "Already in your project key."
                    close -> "Not your key, but it mixes with it — ${detected.shortName} sits next to ${target.shortName} on the wheel."
                    else -> "Pitch ${if (semitones > 0) "+$semitones" else "$semitones"} to land in ${target.shortName}."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (fits || close) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (!fits) {
                Text(
                    "Varispeed would take it ${formatSigned(percent)}%" +
                        if (state.bpm > 0f) {
                            " to ${String.format(Locale.US, "%.1f", state.bpm * (1f + percent / 100f))} BPM."
                        } else ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TonicPicker(selected: Int?, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (pc in 0 until 12) {
            val on = selected == pc
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (on) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(pc) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    MusicalKey.CHROMA_LABELS[pc],
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (on) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Record a passage and turn it into notes.
 *
 * Two readings come out of one recording, and they are good at opposite things. Chords come from
 * the chroma, which hears several notes at once but has thrown the octave away. The melody comes
 * from pitch tracking, which knows exactly which octave but follows only one note at a time. Which
 * is useful depends on what you played, so both are offered.
 */
@Composable
private fun CaptureCard(
    capture: CaptureController.State,
    seconds: Float,
    hasPermission: Boolean,
    onRecord: () -> Unit,
    onStopRecording: () -> Unit,
    onDiscard: () -> Unit,
    onShareChords: () -> Unit,
    onShareMelody: () -> Unit,
    onSaveMelody: () -> Unit
) {
    SectionCard("Record") {
        val result = capture.result
        when {
            capture.recording -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        Exports.timecode(seconds),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.weight(1f))
                    ActionButton(
                        Icons.Filled.StopCircle, "Stop", Modifier.width(96.dp), highlighted = true
                    ) { onStopRecording() }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Play or hum a passage. Two minutes maximum.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            capture.analysing -> {
                Text("Working it out…", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            result != null -> {
                val analysis = result.analysis
                Text(
                    analysis.key?.name ?: "No clear key",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${Exports.timecode(result.seconds)} recorded" +
                        if (analysis.bpm > 0f) "  ·  ${Math.round(analysis.bpm)} BPM" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (result.hasChords) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        analysis.chordSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (result.hasMelody) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${result.melody.size} notes tracked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    ActionButton(
                        Icons.Filled.Piano, "Chords", Modifier.weight(1f),
                        highlighted = result.hasChords
                    ) { if (result.hasChords) onShareChords() }
                    ActionButton(
                        Icons.Filled.GraphicEq, "Melody", Modifier.weight(1f),
                        highlighted = result.hasMelody
                    ) { if (result.hasMelody) onShareMelody() }
                    ActionButton(Icons.Filled.Save, "Save", Modifier.weight(1f)) {
                        if (result.hasMelody) onSaveMelody()
                    }
                    ActionButton(Icons.Filled.Refresh, "Again", Modifier.weight(1f)) { onDiscard() }
                }
                if (!result.hasMelody) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "No single line came through. Melody tracking follows one note at a time, " +
                            "so it needs something played or hummed on its own.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                Text(
                    "Record a passage and take the chords or the melody out as MIDI.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                capture.message?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(Modifier.height(10.dp))
                ActionButton(
                    Icons.Filled.Mic, "Record", Modifier.width(112.dp), highlighted = hasPermission
                ) { if (hasPermission) onRecord() }
            }
        }
    }
}

/**
 * Batch analysis of files on the device. The same pipeline as the mic, minus the speaker and the
 * room, so this is the accurate way to read anything you already have as a file.
 */
@Composable
private fun FilesCard(
    files: FileAnalysisController.State,
    onPick: () -> Unit,
    onCopyCsv: () -> Unit,
    onExportCsv: () -> Unit,
    onShareCsv: () -> Unit,
    onClear: () -> Unit,
    onUseAsProject: (MusicalKey) -> Unit,
    onShareResult: (FileAnalyzer.Result) -> Unit,
    onShareMidi: (FileAnalyzer.Result) -> Unit,
    onSaveMidi: (FileAnalyzer.Result) -> Unit,
    onPickFolder: () -> Unit,
    renamePlan: List<FileAnalysisController.RenamePlan>,
    onPreviewRenames: () -> Unit,
    onApplyRenames: () -> Unit,
    onCancelRenames: () -> Unit
) {
    SectionCard("Files") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Analyse samples and bounces", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "No speaker, no room, no mic — a cleaner reading than playing it out loud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            ActionButton(Icons.Filled.LibraryMusic, "Files", Modifier.width(78.dp)) { onPick() }
            Spacer(Modifier.width(6.dp))
            ActionButton(Icons.Filled.FolderOpen, "Folder", Modifier.width(78.dp)) { onPickFolder() }
        }

        if (files.running) {
            Spacer(Modifier.height(12.dp))
            LabeledBar(
                "Analysing ${files.done + 1} of ${files.total}",
                files.progress,
                MaterialTheme.colorScheme.primary
            )
            if (files.current.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    files.current,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        if (files.results.isNotEmpty()) {
            var expanded by remember { mutableStateOf<String?>(null) }
            Spacer(Modifier.height(12.dp))

            files.results.forEach { result ->
                val open = expanded == result.name
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { expanded = if (open) null else result.name }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(result.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text(
                            result.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (result.error != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    result.key?.let { key ->
                        Text(
                            key.shortName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (open && result.key != null) {
                    Column(Modifier.padding(start = 4.dp, bottom = 10.dp)) {
                        if (result.chords.isNotEmpty()) {
                            Text(
                                result.chordSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        if (result.modulations.isNotEmpty()) {
                            Text(
                                "Key changes: " + result.modulations.joinToString("   ") {
                                    "${Exports.timecode(it.startSeconds)} ${it.key.shortName}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ActionButton(Icons.Filled.Share, "Share", Modifier.weight(1f)) {
                                onShareResult(result)
                            }
                            ActionButton(
                                Icons.Filled.Piano,
                                "MIDI",
                                Modifier.weight(1f),
                                highlighted = result.chords.isNotEmpty()
                            ) { onShareMidi(result) }
                            ActionButton(Icons.Filled.Save, "Save", Modifier.weight(1f)) {
                                onSaveMidi(result)
                            }
                            ActionButton(Icons.Filled.Album, "Project", Modifier.weight(1f)) {
                                result.key.let(onUseAsProject)
                            }
                        }
                        if (result.chords.isEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "No chords settled in this one, so the MIDI would be empty.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Tap a result for its chords and exports.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                TextButton(onClick = onExportCsv) { Text("Export CSV") }
                TextButton(onClick = onShareCsv) { Text("Share CSV") }
                TextButton(onClick = onCopyCsv) { Text("Copy") }
                TextButton(onClick = onClear) { Text("Clear") }
            }

            RenameSection(renamePlan, onPreviewRenames, onApplyRenames, onCancelRenames)
        }
    }
}

/**
 * Renaming is the one thing in the app that changes files you already had, so it never happens
 * on a single tap: the full list of before and after names is shown first and has to be confirmed.
 */
@Composable
private fun RenameSection(
    plan: List<FileAnalysisController.RenamePlan>,
    onPreview: () -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit
) {
    if (plan.isEmpty()) {
        TextButton(onClick = onPreview) { Text("Rename files with key and tempo…") }
        return
    }

    Spacer(Modifier.height(10.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text(
            "RENAMING ${plan.size} FILE${if (plan.size == 1) "" else "S"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary
        )
        Spacer(Modifier.height(8.dp))
        plan.take(12).forEach { entry ->
            Text(
                entry.from,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                "→  ${entry.to}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
        }
        if (plan.size > 12) {
            Text(
                "and ${plan.size - 12} more",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "This renames the files on your device. It cannot be undone from here.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary
        )
        Row {
            TextButton(onClick = onApply) { Text("Rename ${plan.size}") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

/**
 * What is being played right now, and what came before it.
 *
 * Held to a lower standard than the key readout by nature: chords move faster than the analysis
 * window, so the card says plainly when it is unsure rather than inventing something.
 */
@Composable
private fun ChordNowCard(state: EngineState) {
    SectionCard("Chord") {
        val chord = state.chord
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                chord?.name(state.key) ?: "--",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (chord != null) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            if (chord != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        chord.pitchClasses().joinToString(" ") { MusicalKey.CHROMA_LABELS[it] },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${(chord.score * 100).roundToInt()}% fit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.chordSpans.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                state.chordSpans.takeLast(8).joinToString("  →  ") { it.chord.name(state.key) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            when {
                !state.listening -> "Chords are read live, so press Start to follow a progression."
                state.silent -> "Nothing reaching the mic."
                chord == null -> "Nothing that fits a chord cleanly right now."
                else -> "A strong hint, not a transcription — inversions and sevenths are genuinely ambiguous in chroma."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Common progressions spelled into the detected key, auditionable through the tone engine. */
@Composable
private fun ProgressionsCard(
    key: MusicalKey?,
    onPlay: (Progression) -> Unit,
    onShareMidi: (Progression) -> Unit
) {
    SectionCard("Progressions in this key") {
        if (key == null) {
            Text(
                "Once a key is detected, common progressions will be spelled out here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }
        Progressions.forKey(key).forEach { progression ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { onPlay(progression) }
                    .padding(vertical = 9.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        progression.chordNames(key).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${progression.name}   ${progression.numerals(key)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Filled.Piano,
                    contentDescription = "Share as MIDI",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onShareMidi(progression) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap to hear it, or the keys icon to send it out as a MIDI file.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ControlsCard(state: EngineState, actions: KeyScopeActions) {
    SectionCard("Analysis") {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Keep listening after lock", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (state.continuousListening) {
                        "Runs until you stop it, and keeps re-reading as the music changes."
                    } else {
                        "Releases the mic the moment a key locks."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = state.continuousListening, onCheckedChange = actions.setContinuous)
        }

        Spacer(Modifier.height(16.dp))
        Text("Averaging window", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalysisWindow.entries.forEach { window ->
                FilterChip(
                    selected = state.window == window,
                    onClick = { actions.setWindow(window) },
                    label = { Text("${window.label} - ${window.seconds}s") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Key profile", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyProfile.entries.forEach { profile ->
                FilterChip(
                    selected = state.profile == profile,
                    onClick = { actions.setProfile(profile) },
                    label = { Text(profile.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            state.profile.blurb,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = actions.reset) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Clear the average and restart")
        }
    }
}

@Composable
private fun HistoryCard(history: List<HistoryEntry>, onClear: () -> Unit) {
    SectionCard("Log") {
        if (history.isEmpty()) {
            Text(
                "Every time the reading settles on a new key it gets logged here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)
        history.forEach { entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatter.format(Date(entry.atMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Text(entry.key.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text(
                    entry.key.camelot,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                if (entry.bpm > 0f) {
                    Text(
                        "${entry.bpm.roundToInt()} BPM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        TextButton(onClick = onClear) { Text("Clear log") }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title.uppercase(Locale.US),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

// ------------------------------------------------------------------ transport

/**
 * Explicit start and stop rather than one button that means two things.
 *
 * A toggle makes you read its icon to work out what it is about to do. Two buttons with only one
 * of them live never need interpreting, which is what you want when you are looking at an
 * instrument rather than at the phone.
 */
@Composable
private fun Transport(
    listening: Boolean,
    level: Float,
    modifier: Modifier,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val glow by animateFloatAsState(if (listening) level else 0f, tween(120), label = "glow")

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransportButton(
            icon = Icons.Filled.Mic,
            label = if (listening) "Listening" else "Start",
            enabled = !listening,
            // While live the button carries the input level, so the row itself shows it working.
            container = if (listening) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f + glow * 0.45f)
            } else {
                MaterialTheme.colorScheme.primary
            },
            content = if (listening) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.weight(1f),
            onClick = onStart
        )
        TransportButton(
            icon = Icons.Filled.StopCircle,
            label = "Stop",
            enabled = listening,
            container = if (listening) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.surfaceVariant,
            content = if (listening) MaterialTheme.colorScheme.onError
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.weight(1f),
            onClick = onStop
        )
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    container: Color,
    content: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = content
        )
    }
}

private fun formatCents(cents: Float): String {
    val rounded = cents.roundToInt()
    return if (rounded >= 0) "+$rounded cents" else "$rounded cents"
}
