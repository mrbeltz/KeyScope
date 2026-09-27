package com.jonny.r5monitor

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ScopeMode { OFF, WAVEFORM, HISTOGRAM, BOTH }

/** A frame line mask. [ratio] is width over height. */
enum class AspectGuide(val label: String, val ratio: Float) {
    SCOPE("2.39:1", 2.39f),
    FLAT("1.85:1", 1.85f),
    HD("16:9", 16f / 9f),
    SQUARE("1:1", 1f),
    PORTRAIT("4:5", 4f / 5f),
    VERTICAL("9:16", 9f / 16f)
}

enum class PeakingColor(val label: String, val argb: Long) {
    RED("Red", 0xFFFF3030),
    YELLOW("Yellow", 0xFFFFE600),
    CYAN("Cyan", 0xFF00E5FF),
    WHITE("White", 0xFFFFFFFF)
}

/** Everything about how the picture is shown. Survives restarts, since it is set up once per rig. */
data class ViewPrefs(
    val lastHost: String = "",
    val zebra: Boolean = false,
    val zebraLevel: Int = 95,
    val peaking: Boolean = false,
    val peakingSensitivity: Float = 0.5f,
    val peakingColor: PeakingColor = PeakingColor.RED,
    val falseColor: Boolean = false,
    val thirds: Boolean = false,
    val center: Boolean = false,
    val safeArea: Boolean = false,
    val aspect: AspectGuide? = null,
    val desqueeze: Float = 1f,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val scopes: ScopeMode = ScopeMode.WAVEFORM,
    val liveViewSize: String = "medium",
    val cameraDisplay: Boolean = true
)

object MonitorPrefs {

    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(ViewPrefs())
    val state: StateFlow<ViewPrefs> = _state.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("monitor", Context.MODE_PRIVATE)
        val d = ViewPrefs()
        _state.value = ViewPrefs(
            lastHost = prefs.getString("lastHost", d.lastHost) ?: "",
            zebra = prefs.getBoolean("zebra", d.zebra),
            zebraLevel = prefs.getInt("zebraLevel", d.zebraLevel),
            peaking = prefs.getBoolean("peaking", d.peaking),
            peakingSensitivity = prefs.getFloat("peakingSensitivity", d.peakingSensitivity),
            peakingColor = enumOr(prefs.getString("peakingColor", null), d.peakingColor),
            falseColor = prefs.getBoolean("falseColor", d.falseColor),
            thirds = prefs.getBoolean("thirds", d.thirds),
            center = prefs.getBoolean("center", d.center),
            safeArea = prefs.getBoolean("safeArea", d.safeArea),
            aspect = prefs.getString("aspect", null)?.let { name -> AspectGuide.entries.firstOrNull { it.name == name } },
            desqueeze = prefs.getFloat("desqueeze", d.desqueeze),
            flipH = prefs.getBoolean("flipH", d.flipH),
            flipV = prefs.getBoolean("flipV", d.flipV),
            scopes = enumOr(prefs.getString("scopes", null), d.scopes),
            liveViewSize = prefs.getString("liveViewSize", d.liveViewSize) ?: d.liveViewSize,
            cameraDisplay = prefs.getBoolean("cameraDisplay", d.cameraDisplay)
        )
    }

    fun update(change: (ViewPrefs) -> ViewPrefs) {
        val next = change(_state.value)
        _state.value = next
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putString("lastHost", next.lastHost)
            .putBoolean("zebra", next.zebra)
            .putInt("zebraLevel", next.zebraLevel)
            .putBoolean("peaking", next.peaking)
            .putFloat("peakingSensitivity", next.peakingSensitivity)
            .putString("peakingColor", next.peakingColor.name)
            .putBoolean("falseColor", next.falseColor)
            .putBoolean("thirds", next.thirds)
            .putBoolean("center", next.center)
            .putBoolean("safeArea", next.safeArea)
            .putString("aspect", next.aspect?.name)
            .putFloat("desqueeze", next.desqueeze)
            .putBoolean("flipH", next.flipH)
            .putBoolean("flipV", next.flipV)
            .putString("scopes", next.scopes.name)
            .putString("liveViewSize", next.liveViewSize)
            .putBoolean("cameraDisplay", next.cameraDisplay)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback
}
