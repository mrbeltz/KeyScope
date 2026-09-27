package com.jonny.r5monitor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import com.jonny.r5monitor.ccapi.Battery
import com.jonny.r5monitor.ccapi.CcapiClient
import com.jonny.r5monitor.ccapi.CcapiException
import com.jonny.r5monitor.ccapi.CcapiParse
import com.jonny.r5monitor.ccapi.DeviceInfo
import com.jonny.r5monitor.ccapi.LiveViewFrameReader
import com.jonny.r5monitor.ccapi.MalformedFrameException
import com.jonny.r5monitor.ccapi.Paths
import com.jonny.r5monitor.ccapi.Setting
import com.jonny.r5monitor.ccapi.Storage
import com.jonny.r5monitor.scopes.ScopeData
import com.jonny.r5monitor.scopes.ScopeMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.IOException
import java.net.HttpURLConnection
import kotlin.coroutines.coroutineContext

/** WAITING is connected to CCAPI but with no picture yet, which can last if live view is refused. */
enum class Phase { IDLE, CONNECTING, WAITING, LIVE, RECONNECTING, FAILED }

/** How frames are arriving: one long chunked stream, or one request per JPEG. */
enum class Transport { STREAM, SNAPSHOTS }

data class Notice(val id: Long, val text: String)

data class MonitorState(
    val phase: Phase = Phase.IDLE,
    val address: String = "",
    val device: DeviceInfo? = null,
    val settings: Map<String, Setting> = emptyMap(),
    val battery: Battery? = null,
    val storage: List<Storage> = emptyList(),
    val recording: Boolean = false,
    val recordingSince: Long? = null,
    val movieMode: Boolean? = null,
    val fps: Float = 0f,
    val transport: Transport? = null,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val error: String? = null,
    val notice: Notice? = null,
    val canRecord: Boolean = false,
    val canShutter: Boolean = false,
    val canFocus: Boolean = false,
    val endpointCount: Int = 0
)

/** A decoded frame. [seq] changes every frame so the view redraws even for identical pixels. */
class LiveFrame(val bitmap: Bitmap, val seq: Long)

class Scopes(val data: ScopeData, val waveform: Bitmap)

/**
 * The connection to the camera, kept as a process singleton rather than a ViewModel so unfolding
 * the phone (which recreates the activity on some launchers) does not drop the feed.
 *
 * Two loops run while connected: one pulls live view as fast as the camera will send it, the other
 * polls settings, battery and card once a second. They fail independently, so a busy camera that
 * refuses a settings read never costs a frame.
 */
object MonitorSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    private val _frame = MutableStateFlow<LiveFrame?>(null)
    val frame: StateFlow<LiveFrame?> = _frame.asStateFlow()

    private val _scopes = MutableStateFlow<Scopes?>(null)
    val scopes: StateFlow<Scopes?> = _scopes.asStateFlow()

    /** Scopes cost a pixel copy per frame, so they are only computed while something shows them. */
    @Volatile
    var scopesWanted = false

    @Volatile
    private var client: CcapiClient? = null

    @Volatile
    private var activeStream: HttpURLConnection? = null

    /** Set when the stream is dropped on purpose, so the drop is not reported as signal loss. */
    @Volatile
    private var restartRequested = false

    private var seq = 0L
    private var lastFrameAt = 0L
    private var lastScopeAt = 0L
    private var lastStatsAt = 0L
    private var fps = 0f
    private var noticeId = 0L
    private var pixelBuffer = IntArray(0)

    fun connect(context: Context, address: String) {
        val app = context.applicationContext
        disconnect()
        MonitorPrefs.update { it.copy(lastHost = address.trim()) }
        _state.value = MonitorState(phase = Phase.CONNECTING, address = address.trim())
        fps = 0f
        job = scope.launch { run(app, address.trim()) }
    }

    fun disconnect() {
        val running = job
        job = null
        running?.cancel()
        activeStream?.disconnect()
        val c = client
        client = null
        if (c != null && running != null) {
            // Hand the camera its screen back. Best effort: it may already be out of range.
            scope.launch {
                runCatching { c.delete(Paths.LIVEVIEW_SCROLL) }
                runCatching { c.post(Paths.LIVEVIEW, JSONObject().put("liveviewsize", "off").put("cameradisplay", "on")) }
            }
        }
        _frame.value = null
        _scopes.value = null
        _state.value = MonitorState(address = _state.value.address)
    }

    private suspend fun run(context: Context, address: String) {
        val c = CcapiClient(address, WifiLink.networkFor(context, address))
        try {
            val endpoints = c.discover()
            val info = runCatching { CcapiParse.deviceInfo(c.getJson(Paths.DEVICE_INFO)) }.getOrNull()
            client = c
            _state.update {
                it.copy(
                    phase = Phase.WAITING,
                    device = info,
                    endpointCount = endpoints.count,
                    canRecord = endpoints.has(Paths.REC_BUTTON, "POST"),
                    canShutter = endpoints.has(Paths.SHUTTER, "POST"),
                    canFocus = endpoints.has(Paths.SHUTTER_MANUAL, "POST")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(phase = Phase.FAILED, error = describeConnectFailure(address, e)) }
            return
        }

        coroutineScope {
            launch { videoLoop(c) }
            launch { statusLoop(c) }
        }
    }

    private fun describeConnectFailure(address: String, e: Exception): String = when (e) {
        is CcapiException -> "The camera at $address answered, but refused: ${e.message}"
        is java.net.SocketTimeoutException, is java.net.ConnectException, is java.net.NoRouteToHostException ->
            "Nothing answered at $address. Check the camera shows this address, that CCAPI is " +
                "activated, and that the phone is on the same Wi-Fi."
        else -> e.message ?: "Could not connect to $address"
    }

    // ---- Video ------------------------------------------------------------------------------

    private suspend fun videoLoop(c: CcapiClient) {
        var useStream = c.endpoints.has(Paths.LIVEVIEW_SCROLL, "GET")
        var failures = 0
        while (coroutineContext.isActive) {
            try {
                startLiveView(c)
                if (useStream) {
                    _state.update { it.copy(transport = Transport.STREAM) }
                    streamFrames(c)
                } else {
                    _state.update { it.copy(transport = Transport.SNAPSHOTS) }
                    snapshotFrames(c)
                }
                failures = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: MalformedFrameException) {
                useStream = false
            } catch (e: CcapiException) {
                // 404 or 405 on the stream means this firmware does not do it; fall back for good.
                if (useStream && e.status in 404..405) {
                    useStream = false
                } else {
                    failures++
                    signalLost(e.message)
                    delay(backoff(failures))
                }
            } catch (e: IOException) {
                if (restartRequested) {
                    restartRequested = false
                    continue
                }
                failures++
                signalLost(null)
                delay(backoff(failures))
            }
        }
    }

    private fun backoff(failures: Int) = (300L * failures).coerceAtMost(3_000L)

    private fun signalLost(reason: String?) {
        fps = 0f
        _state.update {
            if (it.phase == Phase.LIVE || it.phase == Phase.RECONNECTING) {
                it.copy(phase = Phase.RECONNECTING, fps = 0f, error = reason)
            } else {
                it.copy(error = reason)
            }
        }
    }

    private fun startLiveView(c: CcapiClient) {
        val prefs = MonitorPrefs.state.value
        val body = JSONObject()
            .put("liveviewsize", prefs.liveViewSize)
            .put("cameradisplay", if (prefs.cameraDisplay) "on" else "off")
        try {
            c.post(Paths.LIVEVIEW, body)
        } catch (e: CcapiException) {
            // Some bodies are refused while the camera is already in that live view state; the
            // frame request right after is the real test, so only surface this if frames fail too.
            if (e.status != 503 && e.status != 409) throw e
        }
    }

    private suspend fun streamFrames(c: CcapiClient) {
        val conn = c.open(Paths.LIVEVIEW_SCROLL, "GET", readTimeoutMs = 4_000)
        activeStream = conn
        try {
            val code = conn.responseCode
            if (code != 200) {
                val body = conn.errorStream?.use { String(it.readBytes()) }.orEmpty()
                throw CcapiException(code, CcapiParse.errorMessage(body) ?: "HTTP $code")
            }
            val reader = LiveViewFrameReader(BufferedInputStream(conn.inputStream, 64 * 1024))
            var images = 0
            while (true) {
                coroutineContext.ensureActive()
                val frame = reader.next() ?: return
                if (frame.type == LiveViewFrameReader.TYPE_IMAGE) {
                    if (!publish(c, frame.data) && images == 0) {
                        throw MalformedFrameException("Stream payload is not an image")
                    }
                    images++
                }
            }
        } finally {
            activeStream = null
            conn.disconnect()
        }
    }

    private suspend fun snapshotFrames(c: CcapiClient) {
        while (true) {
            coroutineContext.ensureActive()
            if (restartRequested) {
                restartRequested = false
                return
            }
            val bytes = try {
                c.getBytes(Paths.LIVEVIEW_FLIP)
            } catch (e: CcapiException) {
                // 503 is "no new frame yet", which the camera says often when polled quickly.
                if (e.status == 503) {
                    delay(40)
                    continue
                }
                throw e
            }
            publish(c, bytes)
        }
    }

    /** Decodes and hands a frame to the UI. False if the bytes were not a picture. */
    private fun publish(c: CcapiClient, jpeg: ByteArray): Boolean {
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return false
        // A request that was already in flight when the session ended must not bring it back.
        if (client !== c) return true
        val now = SystemClock.elapsedRealtime()
        val dt = now - lastFrameAt
        lastFrameAt = now
        _frame.value = LiveFrame(bitmap, ++seq)
        if (dt in 1..2_000) {
            val instant = 1000f / dt
            fps = if (fps == 0f) instant else fps * 0.9f + instant * 0.1f
        }
        // The frame itself goes through [frame]; state only changes when something the UI shows
        // does, so the whole screen is not recomposed at the frame rate.
        val s = _state.value
        if (s.phase != Phase.LIVE || s.frameWidth != bitmap.width || s.frameHeight != bitmap.height ||
            now - lastStatsAt >= 1_000
        ) {
            lastStatsAt = now
            _state.update {
                it.copy(phase = Phase.LIVE, fps = fps, error = null, frameWidth = bitmap.width, frameHeight = bitmap.height)
            }
        }
        if (scopesWanted && now - lastScopeAt >= SCOPE_INTERVAL_MS) {
            lastScopeAt = now
            computeScopes(bitmap)
        }
        return true
    }

    private fun computeScopes(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        if (pixelBuffer.size != w * h) pixelBuffer = IntArray(w * h)
        bitmap.getPixels(pixelBuffer, 0, w, 0, 0, w, h)
        val data = ScopeMath.analyze(pixelBuffer, w, h)
        val pixels = ScopeMath.waveformPixels(data)
        val wave = Bitmap.createBitmap(pixels, data.columns, data.levels, Bitmap.Config.ARGB_8888)
        _scopes.value = Scopes(data, wave)
    }

    // ---- Status -----------------------------------------------------------------------------

    private suspend fun statusLoop(c: CcapiClient) {
        runCatching { refreshSettings(c) }
        runCatching { refreshBatteryAndStorage(c) }
        runCatching { refreshRecording(c) }
        val events = c.endpoints.has(Paths.EVENTS, "GET")
        var tick = 0
        while (coroutineContext.isActive) {
            delay(1_000)
            tick++
            try {
                if (events) pollEvents(c) else if (tick % 3 == 0) refreshSettings(c)
                refreshRecording(c)
                if (tick % 15 == 0) refreshBatteryAndStorage(c)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The video loop owns reconnecting; a missed status poll is not worth a message.
            }
        }
    }

    private fun refreshSettings(c: CcapiClient) {
        val settings = CcapiParse.settings(c.getJson(Paths.SETTINGS))
        if (client !== c) return
        _state.update { it.copy(settings = settings) }
    }

    private fun refreshBatteryAndStorage(c: CcapiClient) {
        val batteryPath = if (c.endpoints.has(Paths.BATTERY_LIST, "GET") && c.endpoints.count > 0) Paths.BATTERY_LIST else Paths.BATTERY
        val battery = runCatching { CcapiParse.battery(c.getJson(batteryPath)) }.getOrNull()
        val storage = runCatching { CcapiParse.storage(c.getJson(Paths.STORAGE)) }.getOrNull()
        if (client !== c) return
        _state.update {
            it.copy(battery = battery ?: it.battery, storage = storage ?: it.storage)
        }
    }

    private fun refreshRecording(c: CcapiClient) {
        if (c.endpoints.count > 0 && !c.endpoints.has(Paths.REC_BUTTON, "GET")) {
            if (c.endpoints.has(Paths.MOVIE_MODE, "GET")) {
                CcapiParse.movieMode(c.getJson(Paths.MOVIE_MODE))?.let { mode -> _state.update { it.copy(movieMode = mode) } }
            }
            return
        }
        CcapiParse.recording(c.getJson(Paths.REC_BUTTON))?.let(::applyRecording)
    }

    private fun pollEvents(c: CcapiClient) {
        val body = c.getJson("${Paths.EVENTS}?timeout=immediately")
        if (client !== c) return
        val changed = CcapiParse.settings(body)
        val battery = CcapiParse.battery(body)
        body.opt("recbutton")?.let { CcapiParse.recording(JSONObject().put("recbutton", it)) }?.let(::applyRecording)
        val movie = body.opt("moviemode")?.let { CcapiParse.movieMode(JSONObject().put("moviemode", it)) }
        if (changed.isEmpty() && battery == null && movie == null) return
        _state.update {
            it.copy(
                settings = if (changed.isEmpty()) it.settings else it.settings + changed,
                battery = battery ?: it.battery,
                movieMode = movie ?: it.movieMode
            )
        }
    }

    private fun applyRecording(recording: Boolean) {
        _state.update {
            if (it.recording == recording) {
                it
            } else {
                it.copy(recording = recording, recordingSince = if (recording) SystemClock.elapsedRealtime() else null)
            }
        }
    }

    // ---- Controls ---------------------------------------------------------------------------

    fun toggleRecord() = command("Recording") { c ->
        val start = !_state.value.recording
        if (start && _state.value.movieMode != true && c.endpoints.has(Paths.MOVIE_MODE, "POST") && c.endpoints.count > 0) {
            // Recording from stills mode is refused; switch first, as the Photo/Movie switch would.
            runCatching { c.post(Paths.MOVIE_MODE, JSONObject().put("action", "on")) }
            delay(600)
        }
        c.post(Paths.REC_BUTTON, JSONObject().put("action", if (start) "start" else "stop"))
        applyRecording(start)
    }

    fun shutter() = command("Shutter") { c ->
        c.post(Paths.SHUTTER, JSONObject().put("af", true))
        notify("Shot taken")
    }

    fun autofocus() = command("Focus") { c ->
        c.post(Paths.SHUTTER_MANUAL, JSONObject().put("action", "half_press").put("af", true))
        delay(700)
        c.post(Paths.SHUTTER_MANUAL, JSONObject().put("action", "release").put("af", true))
    }

    fun setSetting(key: String, value: String) = command(key.uppercase()) { c ->
        c.put(Paths.setting(key), JSONObject().put("value", value))
        _state.update { s ->
            val current = s.settings[key] ?: return@update s
            s.copy(settings = s.settings + (key to current.copy(value = value)))
        }
    }

    /** Re-requests live view at the new size and drops the stream so the next one picks it up. */
    fun restartLiveView() {
        if (client == null) return
        restartRequested = true
        activeStream?.disconnect()
    }

    private fun command(what: String, block: suspend (CcapiClient) -> Unit) {
        val c = client ?: return
        scope.launch {
            try {
                block(c)
            } catch (e: CancellationException) {
                throw e
            } catch (e: CcapiException) {
                notify("$what: ${e.message}")
            } catch (e: Exception) {
                notify("$what failed: ${e.message ?: "no response"}")
            }
        }
    }

    private fun notify(text: String) {
        _state.update { it.copy(notice = Notice(++noticeId, text)) }
    }

    private const val SCOPE_INTERVAL_MS = 90L
}
