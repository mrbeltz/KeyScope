package com.jonny.keyscope.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    onClearHistory: () -> Unit
) {
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
                                ScaleCard(state.key)
                                CompatibleCard(state.key)
                            }
                            Column(
                                Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ChromaCard(state)
                                TempoCard(state)
                                ControlsCard(state, onReset, onWindowChange, onProfileChange)
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
                            ChromaCard(state)
                            ScaleCard(state.key)
                            CompatibleCard(state.key)
                            TempoCard(state)
                            ControlsCard(state, onReset, onWindowChange, onProfileChange)
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

            Text(
                key?.shortName ?: "--",
                style = MaterialTheme.typography.displayLarge,
                color = accent,
                textAlign = TextAlign.Center
            )
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

private fun statusLine(state: EngineState, hasPermission: Boolean): String = when {
    !hasPermission -> "MICROPHONE ACCESS NEEDED"
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
    onProfileChange: (KeyProfile) -> Unit
) {
    SectionCard("Analysis") {
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
