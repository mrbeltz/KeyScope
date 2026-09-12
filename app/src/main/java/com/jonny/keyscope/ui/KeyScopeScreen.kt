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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopCircle
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
import com.jonny.keyscope.MusicalKey
import com.jonny.keyscope.audio.AnalysisWindow
import com.jonny.keyscope.audio.EngineState
import com.jonny.keyscope.audio.HistoryEntry
import com.jonny.keyscope.dsp.KeyProfile
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TWO_PANE_WIDTH_DP = 720

@Composable
fun KeyScopeScreen(
    state: EngineState,
    level: Float,
    hasPermission: Boolean,
    onToggleListening: () -> Unit,
    onReset: () -> Unit,
    onWindowChange: (AnalysisWindow) -> Unit,
    onProfileChange: (KeyProfile) -> Unit,
    onContinuousChange: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    tonePlaying: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onToneToggle: () -> Unit,
    onPlayScale: () -> Unit
) {
    var transposeTarget by remember(state.key) { mutableStateOf<Int?>(null) }
    val actions: @Composable () -> Unit = {
        ResultActions(state, tonePlaying, onCopy, onShare, onToneToggle, onPlayScale)
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
                                actions()
                                ScaleCard(state.key)
                                ChordsCard(state.key)
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
                                TempoCard(state)
                                ControlsCard(state, onReset, onWindowChange, onProfileChange, onContinuousChange)
                                HistoryCard(state.history, onClearHistory)
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
                            actions()
                            ChromaCard(state)
                            ScaleCard(state.key)
                            ChordsCard(state.key)
                            CompatibleCard(state.key)
                            TransposeCard(state, transposeTarget) { transposeTarget = it }
                            TempoCard(state)
                            ControlsCard(state, onReset, onWindowChange, onProfileChange, onContinuousChange)
                            HistoryCard(state.history, onClearHistory)
                            Spacer(Modifier.height(96.dp))
                        }
                    }
                }

                MicButton(
                    listening = state.listening,
                    level = level,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 20.dp),
                    onClick = onToggleListening
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
            "KeyScope",
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
        ActionButton(Icons.Filled.ContentCopy, "Copy", Modifier.weight(1f), onCopy)
        ActionButton(Icons.Filled.Share, "Share", Modifier.weight(1f), onShare)
        ActionButton(
            if (tonePlaying) Icons.Filled.StopCircle else Icons.Filled.VolumeUp,
            if (tonePlaying) "Stop" else "Tonic",
            Modifier.weight(1f),
            onToneToggle,
            highlighted = tonePlaying
        )
        ActionButton(Icons.Filled.PlayArrow, "Scale", Modifier.weight(1f), onPlayScale)
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
    onClick: () -> Unit,
    highlighted: Boolean = false
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
    !state.listening -> "TAP THE MIC TO START"
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
private fun TempoCard(state: EngineState) {
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
        Spacer(Modifier.height(6.dp))
        Text(
            "Onset autocorrelation over the last 12 seconds. Half or double time is the usual failure mode.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ControlsCard(
    state: EngineState,
    onReset: () -> Unit,
    onWindowChange: (AnalysisWindow) -> Unit,
    onProfileChange: (KeyProfile) -> Unit,
    onContinuousChange: (Boolean) -> Unit
) {
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
            Switch(checked = state.continuousListening, onCheckedChange = onContinuousChange)
        }

        Spacer(Modifier.height(16.dp))
        Text("Averaging window", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalysisWindow.entries.forEach { window ->
                FilterChip(
                    selected = state.window == window,
                    onClick = { onWindowChange(window) },
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
                    onClick = { onProfileChange(profile) },
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
        TextButton(onClick = onReset) {
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

// ------------------------------------------------------------------ mic button

@Composable
private fun MicButton(listening: Boolean, level: Float, modifier: Modifier, onClick: () -> Unit) {
    val glow by animateFloatAsState(if (listening) level else 0f, tween(120), label = "glow")
    val container = if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        if (listening) {
            Box(
                Modifier
                    .size((68 + glow * 26f).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            )
        }
        Box(
            Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(container)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (listening) Icons.Filled.Mic else Icons.Filled.MicOff,
                contentDescription = if (listening) "Stop listening" else "Start listening",
                tint = if (listening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

private fun formatCents(cents: Float): String {
    val rounded = cents.roundToInt()
    return if (rounded >= 0) "+$rounded cents" else "$rounded cents"
}
