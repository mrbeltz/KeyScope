package com.jonny.r5monitor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jonny.r5monitor.MonitorState
import com.jonny.r5monitor.Phase
import com.jonny.r5monitor.WifiLink
import kotlinx.coroutines.launch

@Composable
fun ConnectScreen(
    state: MonitorState,
    lastHost: String,
    onConnect: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(lastHost) }
    var scanning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var found by remember { mutableStateOf<List<WifiLink.Found>?>(null) }
    var helpOpen by remember { mutableStateOf(lastHost.isEmpty()) }
    val connecting = state.phase == Phase.CONNECTING

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Videocam, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.size(10.dp))
                Column {
                    Text("R5 Monitor", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Wi-Fi live view for Canon EOS over CCAPI",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = address,
                onValueChange = { address = it.trim() },
                label = { Text("Camera IP address") },
                placeholder = { Text("192.168.1.2") },
                singleLine = true,
                enabled = !connecting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (address.isNotBlank()) onConnect(address) }),
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )

            if (connecting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    Text("Connecting to ${state.address}…", Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onConnect(address) },
                        enabled = address.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Connect") }
                    OutlinedButton(
                        onClick = {
                            scanning = true
                            found = null
                            progress = 0f
                            scope.launch {
                                found = WifiLink.scan(context) { p -> progress = p }
                                scanning = false
                            }
                        },
                        enabled = !scanning,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (scanning) "Searching…" else "Find camera") }
                }
            }

            if (state.phase == Phase.FAILED && state.error != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        state.error,
                        Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (scanning) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            found?.let { list ->
                if (list.isEmpty()) {
                    Text(
                        "No camera answered on this network. Make sure the R5 is connected and showing " +
                            "its IP address, then try again or type the address in.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                for (camera in list) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().clickable {
                            address = camera.address
                            onConnect(camera.address)
                        }
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(camera.product, style = MaterialTheme.typography.titleMedium)
                            Text(camera.address, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Setting up the camera", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { helpOpen = !helpOpen }) { Text(if (helpOpen) "Hide" else "Show") }
                    }
                    if (helpOpen) {
                        val steps = listOf(
                            "Activate CCAPI on the R5 once. Register (free) on the Canon Developer " +
                                "Community, download the CCAPI activation tool, and run it with the " +
                                "camera connected to your computer. The R5 needs firmware 1.1.0 or later.",
                            "On the camera, connect to a network through the Wi-Fi menu using the " +
                                "remote control / CCAPI connection. Either join the same Wi-Fi as the " +
                                "phone, or turn on the phone's hotspot and join that.",
                            "The camera shows its IP address once connected. Type it here, or tap " +
                                "Find camera to search the network.",
                            "Live view over Wi-Fi runs at roughly 10–25 fps with a short delay. It is " +
                                "for framing, exposure and focus checks, not for timing-critical pulls, " +
                                "and it carries no audio."
                        )
                        steps.forEachIndexed { i, text ->
                            Row(Modifier.padding(vertical = 4.dp)) {
                                Text("${i + 1}.", Modifier.widthIn(min = 22.dp), color = MaterialTheme.colorScheme.primary)
                                Text(text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
