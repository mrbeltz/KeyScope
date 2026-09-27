@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.jonny.r5monitor.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.SystemClock
import com.jonny.r5monitor.AspectGuide
import com.jonny.r5monitor.MonitorState
import com.jonny.r5monitor.PeakingColor
import com.jonny.r5monitor.Phase
import com.jonny.r5monitor.ScopeMode
import com.jonny.r5monitor.Scopes
import com.jonny.r5monitor.Transport
import com.jonny.r5monitor.ViewPrefs
import com.jonny.r5monitor.ccapi.Setting
import com.jonny.r5monitor.ccapi.SettingFormat
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class MonitorActions(
    val disconnect: () -> Unit,
    val record: () -> Unit,
    val shutter: () -> Unit,
    val focus: () -> Unit,
    val setSetting: (String, String) -> Unit,
    val prefs: ((ViewPrefs) -> ViewPrefs) -> Unit,
    /** Live view size or camera display changed; the camera has to be asked again. */
    val liveViewChanged: () -> Unit
)

// ---- HUD --------------------------------------------------------------------------------------

/** The strip across the top of the picture: record state, timer, frame rate, battery. */
@Composable
fun TopHud(state: MonitorState, zoom: Float, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RecordPill(state)
        Spacer(Modifier.weight(1f))
        if (zoom > 1.01f) HudText("%.1fx".format(zoom), Color(0xFFFFE08A))
        if (state.phase == Phase.LIVE) HudText("%.0f fps".format(state.fps))
        state.battery?.let { HudText(it.label, if ((it.percent ?: 100) <= 15) RecordRed else Color.White) }
    }
}

@Composable
private fun RecordPill(state: MonitorState) {
    val recording = state.recording
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(recording) {
        while (recording) {
            now = SystemClock.elapsedRealtime()
            delay(250)
        }
    }
    val blink = rememberInfiniteTransition(label = "rec")
    val dotAlpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "dot"
    )
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (recording) RecordRed.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .alpha(if (recording) dotAlpha else 1f)
                .clip(CircleShape)
                .background(if (recording) Color.White else Color(0xFF7A7A85))
        )
        Spacer(Modifier.width(6.dp))
        val since = state.recordingSince
        val text = if (recording && since != null) timecode(now - since) else "STBY"
        Text(text, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

private fun timecode(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60)
}

@Composable
fun HudText(text: String, color: Color = Color.White) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

/** Exposure readout along the bottom edge, for the full-screen layout where there is no panel. */
@Composable
fun BottomHud(state: MonitorState, modifier: Modifier = Modifier) {
    val items = exposureSettings(state.settings)
    if (items.isEmpty()) return
    Row(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        for (s in items) HudText(SettingFormat.value(s.key, s.value))
    }
}

/** Centred message over the picture while there is no picture to show. */
@Composable
fun SignalMessage(state: MonitorState, hasFrame: Boolean, modifier: Modifier = Modifier) {
    val text = when {
        state.phase == Phase.RECONNECTING -> "Signal lost · reconnecting" + (state.error?.let { "\n$it" } ?: "")
        !hasFrame -> "Waiting for live view" + (state.error?.let { "\n$it" } ?: "")
        else -> return
    }
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium
    )
}

// ---- Record / shutter / focus -----------------------------------------------------------------

@Composable
fun RecordButton(recording: Boolean, onClick: () -> Unit, size: Int = 64) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape)
            .clickable(onClickLabel = if (recording) "Stop recording" else "Start recording", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (recording) {
            Box(Modifier.size((size * 0.36f).dp).clip(RoundedCornerShape(4.dp)).background(RecordRed))
        } else {
            Box(Modifier.size((size * 0.72f).dp).clip(CircleShape).background(RecordRed))
        }
    }
}

@Composable
fun RoundTool(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
fun TransportRow(state: MonitorState, actions: MonitorActions, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.canFocus) RoundTool("AF", Icons.Filled.CenterFocusStrong, actions.focus)
        if (state.canRecord) RecordButton(state.recording, actions.record)
        if (state.canShutter) RoundTool("Photo", Icons.Filled.PhotoCamera, actions.shutter)
    }
}

// ---- Exposure ---------------------------------------------------------------------------------

private fun exposureSettings(settings: Map<String, Setting>): List<Setting> {
    val keys = SettingFormat.exposureKeys.toMutableList()
    if ("shootingmodedial" !in settings) keys[0] = "shootingmode"
    // Kelvin only matters when white balance is set to it.
    if (settings["wb"]?.value != "colortemp") keys.remove("colortemperature")
    return keys.mapNotNull { settings[it] }
}

/** Mode, shutter, aperture, ISO, compensation and white balance. Tap one to change it. */
@Composable
fun ExposureStrip(state: MonitorState, actions: MonitorActions, modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf<String?>(null) }
    val items = exposureSettings(state.settings)
    if (items.isEmpty()) {
        Text(
            if (state.phase == Phase.LIVE) "The camera is not reporting exposure settings." else "Reading settings…",
            modifier = modifier.padding(8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        FlowRow(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (s in items) {
                SettingTile(s, onClick = { if (s.options.isNotEmpty()) editing = s.key })
            }
        }
    }
    editing?.let { key ->
        state.settings[key]?.let { setting ->
            SettingSheet(setting, onPick = { actions.setSetting(key, it); editing = null }, onDismiss = { editing = null })
        }
    }
}

@Composable
private fun SettingTile(setting: Setting, onClick: () -> Unit) {
    val editable = setting.options.isNotEmpty()
    Column(
        Modifier
            .widthIn(min = 64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = editable, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            SettingFormat.label(setting.key),
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            SettingFormat.value(setting.key, setting.value),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = if (editable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingSheet(setting: Setting, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(SettingFormat.label(setting.key), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (option in setting.options) {
                    FilterChip(
                        selected = option == setting.value,
                        onClick = { onPick(option) },
                        label = { Text(SettingFormat.value(setting.key, option), fontFamily = FontFamily.Monospace) }
                    )
                }
            }
        }
    }
}

/** Every other setting the camera reports, collapsed by default. */
@Composable
fun AllSettings(state: MonitorState, actions: MonitorActions) {
    var open by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    val shown = exposureSettings(state.settings).map { it.key }.toSet()
    val rest = state.settings.values.filter { it.key !in shown }
    if (rest.isEmpty()) return
    PanelCard("More settings", trailing = {
        TextButton(onClick = { open = !open }) { Text(if (open) "Hide" else "Show ${rest.size}") }
    }) {
        if (open) {
            for (s in rest) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = s.options.isNotEmpty()) { editing = s.key }
                        .padding(vertical = 6.dp)
                ) {
                    Text(SettingFormat.label(s.key), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(SettingFormat.value(s.key, s.value), style = MaterialTheme.typography.bodySmall,
                        color = if (s.options.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
    editing?.let { key ->
        state.settings[key]?.let { setting ->
            SettingSheet(setting, onPick = { actions.setSetting(key, it); editing = null }, onDismiss = { editing = null })
        }
    }
}

// ---- Cards ------------------------------------------------------------------------------------

@Composable
fun PanelCard(title: String, trailing: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title.uppercase(),
                    Modifier.weight(1f),
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                trailing()
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
fun ScopesCard(prefs: ViewPrefs, scopes: () -> Scopes?, actions: MonitorActions) {
    PanelCard("Scopes") {
        ChipRow {
            for (mode in ScopeMode.entries) {
                FilterChip(
                    selected = prefs.scopes == mode,
                    onClick = { actions.prefs { it.copy(scopes = mode) } },
                    label = {
                        Text(
                            when (mode) {
                                ScopeMode.OFF -> "Off"
                                ScopeMode.WAVEFORM -> "Waveform"
                                ScopeMode.HISTOGRAM -> "Histogram"
                                ScopeMode.BOTH -> "Both"
                            }
                        )
                    }
                )
            }
        }
        if (prefs.scopes == ScopeMode.WAVEFORM || prefs.scopes == ScopeMode.BOTH) {
            Spacer(Modifier.height(8.dp))
            Waveform(scopes, Modifier.fillMaxWidth().height(130.dp))
        }
        if (prefs.scopes == ScopeMode.HISTOGRAM || prefs.scopes == ScopeMode.BOTH) {
            Spacer(Modifier.height(8.dp))
            Histogram(scopes, Modifier.fillMaxWidth().height(90.dp))
        }
        if (prefs.scopes != ScopeMode.OFF) {
            val data = scopes()?.data
            if (data != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Clipped %.1f%% · crushed %.1f%%".format(data.clippedHigh * 100, data.clippedLow * 100),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (data.clippedHigh > 0.01f) RecordRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ExposureToolsCard(prefs: ViewPrefs, actions: MonitorActions) {
    PanelCard("Exposure & focus aids") {
        if (!MonitorShader.supported) {
            Text(
                "Zebras, peaking and false colour need Android 13 or newer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@PanelCard
        }
        ToggleRow("Zebras", prefs.zebra) { on -> actions.prefs { it.copy(zebra = on) } }
        if (prefs.zebra) {
            LabeledSlider("${prefs.zebraLevel}%", prefs.zebraLevel.toFloat(), 70f..100f, steps = 29) { v ->
                actions.prefs { it.copy(zebraLevel = v.roundToInt()) }
            }
        }
        ToggleRow("Focus peaking", prefs.peaking) { on -> actions.prefs { it.copy(peaking = on) } }
        if (prefs.peaking) {
            LabeledSlider(
                when {
                    prefs.peakingSensitivity < 0.34f -> "Low"
                    prefs.peakingSensitivity < 0.67f -> "Mid"
                    else -> "High"
                },
                prefs.peakingSensitivity, 0f..1f
            ) { v -> actions.prefs { it.copy(peakingSensitivity = v) } }
            ChipRow {
                for (c in PeakingColor.entries) {
                    FilterChip(
                        selected = prefs.peakingColor == c,
                        onClick = { actions.prefs { it.copy(peakingColor = c) } },
                        label = { Text(c.label) },
                        leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(c.argb))) }
                    )
                }
            }
        }
        ToggleRow("False colour", prefs.falseColor) { on -> actions.prefs { it.copy(falseColor = on) } }
        if (prefs.falseColor) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((label, color) in falseColorLegend) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
                        Spacer(Modifier.width(4.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun FramingCard(prefs: ViewPrefs, actions: MonitorActions) {
    PanelCard("Framing") {
        ChipRow {
            FilterChip(prefs.thirds, { actions.prefs { it.copy(thirds = !it.thirds) } }, { Text("Thirds") })
            FilterChip(prefs.center, { actions.prefs { it.copy(center = !it.center) } }, { Text("Centre") })
            FilterChip(prefs.safeArea, { actions.prefs { it.copy(safeArea = !it.safeArea) } }, { Text("Safe 90%") })
        }
        SubLabel("Frame lines")
        ChipRow {
            FilterChip(prefs.aspect == null, { actions.prefs { it.copy(aspect = null) } }, { Text("Off") })
            for (a in AspectGuide.entries) {
                FilterChip(prefs.aspect == a, { actions.prefs { it.copy(aspect = a) } }, { Text(a.label) })
            }
        }
        SubLabel("Anamorphic desqueeze")
        ChipRow {
            for (d in listOf(1f, 1.33f, 1.5f, 1.8f, 2f)) {
                FilterChip(
                    prefs.desqueeze == d,
                    { actions.prefs { it.copy(desqueeze = d) } },
                    { Text(if (d == 1f) "Off" else "${d}x") }
                )
            }
        }
        SubLabel("Flip")
        ChipRow {
            FilterChip(prefs.flipH, { actions.prefs { it.copy(flipH = !it.flipH) } }, { Text("Mirror") })
            FilterChip(prefs.flipV, { actions.prefs { it.copy(flipV = !it.flipV) } }, { Text("Upside down") })
        }
    }
}

@Composable
fun CameraCard(state: MonitorState, prefs: ViewPrefs, actions: MonitorActions) {
    PanelCard("Camera") {
        val device = state.device
        Text(
            device?.product ?: "Canon camera",
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            listOfNotNull(
                state.address,
                device?.firmware?.takeIf { it.isNotBlank() }?.let { "firmware $it" }
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            state.battery?.let {
                IconStat(Icons.Filled.BatteryFull, it.label)
            }
            state.storage.firstOrNull()?.freeBytes?.let { free ->
                IconStat(Icons.Filled.SdCard, "%.1f GB free".format(free / 1e9))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            buildString {
                append(
                    when (state.transport) {
                        Transport.STREAM -> "Streaming"
                        Transport.SNAPSHOTS -> "Polling frames"
                        null -> "Starting"
                    }
                )
                if (state.frameWidth > 0) append(" · ${state.frameWidth}×${state.frameHeight}")
                if (state.fps > 0f) append(" · %.0f fps".format(state.fps))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SubLabel("Live view size")
        ChipRow {
            for ((value, label) in listOf("small" to "Small · faster", "medium" to "Medium · sharper")) {
                FilterChip(prefs.liveViewSize == value, {
                    actions.prefs { it.copy(liveViewSize = value) }
                    actions.liveViewChanged()
                }, { Text(label) })
            }
        }
        ToggleRow("Keep camera screen on", prefs.cameraDisplay) { on ->
            actions.prefs { it.copy(cameraDisplay = on) }
            actions.liveViewChanged()
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = actions.disconnect, modifier = Modifier.fillMaxWidth()) { Text("Disconnect") }
    }
}

@Composable
private fun IconStat(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---- Small pieces -----------------------------------------------------------------------------

@Composable
fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun SubLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(label, Modifier.width(44.dp), style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
    }
}
