package com.jonny.r5monitor.ccapi

import org.json.JSONArray
import org.json.JSONObject

/** Paths used by the app. Each is checked against what the camera advertises before it is used. */
object Paths {
    const val ROOT = "/ccapi"
    const val DEVICE_INFO = "/ccapi/ver100/deviceinformation"
    const val LIVEVIEW = "/ccapi/ver100/shooting/liveview"
    const val LIVEVIEW_FLIP = "/ccapi/ver100/shooting/liveview/flip"
    const val LIVEVIEW_SCROLL = "/ccapi/ver100/shooting/liveview/scroll"
    const val SETTINGS = "/ccapi/ver100/shooting/settings"
    const val BATTERY = "/ccapi/ver100/devicestatus/battery"
    const val BATTERY_LIST = "/ccapi/ver110/devicestatus/batterylist"
    const val STORAGE = "/ccapi/ver100/devicestatus/storage"
    const val EVENTS = "/ccapi/ver100/event/polling"
    const val SHUTTER = "/ccapi/ver100/shooting/control/shutterbutton"
    const val SHUTTER_MANUAL = "/ccapi/ver100/shooting/control/shutterbutton/manual"
    const val MOVIE_MODE = "/ccapi/ver100/shooting/control/moviemode"
    const val REC_BUTTON = "/ccapi/ver100/shooting/control/recbutton"

    fun setting(key: String) = "$SETTINGS/$key"
}

/**
 * The API list `GET /ccapi` returns: every path the camera implements and the methods on each,
 * grouped by version. Firmware differs in what it exposes, so controls are shown only for what
 * this particular camera says it has.
 */
class Endpoints(private val methods: Map<String, Set<String>>) {

    /** True when the camera lists [path] with [method]. An empty list means we could not tell, so allow it. */
    fun has(path: String, method: String): Boolean =
        methods.isEmpty() || methods[path]?.contains(method.uppercase()) == true

    val count: Int get() = methods.size

    companion object {
        val UNKNOWN = Endpoints(emptyMap())

        fun parse(body: String): Endpoints {
            val root = JSONObject(body)
            val result = HashMap<String, Set<String>>()
            for (version in root.keys()) {
                val list = root.optJSONArray(version) ?: continue
                for (i in 0 until list.length()) {
                    val entry = list.optJSONObject(i) ?: continue
                    val path = normalisePath(entry.optString("path"))
                    if (path.isEmpty()) continue
                    val allowed = buildSet {
                        for (m in listOf("get", "put", "post", "delete")) {
                            if (entry.optBoolean(m)) add(m.uppercase())
                        }
                    }
                    result[path] = allowed
                }
            }
            return Endpoints(result)
        }

        /** The camera reports absolute URLs; only the part from `/ccapi` on is stable. */
        fun normalisePath(raw: String): String {
            val at = raw.indexOf("/ccapi")
            return if (at < 0) "" else raw.substring(at).trimEnd('/')
        }
    }
}

/** One shooting setting: its current value and, when the camera gives a list, the values it will accept. */
data class Setting(val key: String, val value: String, val options: List<String>)

data class DeviceInfo(val product: String, val firmware: String, val serial: String)

/** [percent] is null when the camera only reports a coarse level such as "half". */
data class Battery(val label: String, val percent: Int?)

data class Storage(val name: String, val freeBytes: Long?, val totalBytes: Long?)

object CcapiParse {

    fun deviceInfo(json: JSONObject) = DeviceInfo(
        product = json.optString("productname", "Canon camera"),
        firmware = json.optString("firmwareversion", ""),
        serial = json.optString("serialnumber", "")
    )

    /**
     * Pulls every `{ "value": ..., "ability": [...] }` object out of a settings or event body.
     * Anything else (battery, storage, recording state) is ignored here and read by its own parser.
     */
    fun settings(json: JSONObject): Map<String, Setting> {
        val out = LinkedHashMap<String, Setting>()
        for (key in json.keys()) {
            val obj = json.optJSONObject(key) ?: continue
            if (!obj.has("value")) continue
            val raw = obj.opt("value")
            // Nested values (AF frame positions, white balance shift) are not something a chip can edit.
            if (raw is JSONObject || raw is JSONArray || raw == null || raw == JSONObject.NULL) continue
            out[key] = Setting(key, raw.toString(), options(obj.opt("ability")))
        }
        return out
    }

    private fun options(ability: Any?): List<String> {
        if (ability !is JSONArray) return emptyList()
        return (0 until ability.length()).mapNotNull { i ->
            when (val v = ability.opt(i)) {
                is String -> v
                is Number -> v.toString()
                else -> null
            }
        }
    }

    /** Handles both the ver100 single battery and the ver110 `batterylist`, which covers a grip. */
    fun battery(json: JSONObject): Battery? {
        val list = json.optJSONArray("batterylist")
        val entry = when {
            list != null && list.length() > 0 -> list.optJSONObject(0)
            json.has("level") -> json
            json.optJSONObject("battery") != null -> json.optJSONObject("battery")
            else -> null
        } ?: return null
        val level = entry.optString("level", "").trim()
        if (level.isEmpty() || level == "unknown") return null
        level.toIntOrNull()?.let { return Battery("$it%", it) }
        val percent = when (level) {
            "full" -> 100
            "high" -> 75
            "half" -> 50
            "quarter" -> 25
            "low" -> 10
            else -> null
        }
        return Battery(level.replaceFirstChar { it.uppercase() }, percent)
    }

    fun storage(json: JSONObject): List<Storage> {
        val list = json.optJSONArray("storagelist") ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val s = list.optJSONObject(i) ?: return@mapNotNull null
            Storage(
                name = s.optString("name", "card"),
                freeBytes = s.optLong("spacesize", -1).takeIf { it >= 0 },
                totalBytes = s.optLong("maxsize", -1).takeIf { it > 0 }
            )
        }
    }

    /**
     * Recording state as the camera reports it, or null if this body does not say. Firmware has
     * used both `{"status": "start"}` and a bare string, so both are accepted.
     */
    fun recording(json: JSONObject): Boolean? {
        val raw = json.opt("recbutton") ?: json.opt("status") ?: return null
        val status = when (raw) {
            is JSONObject -> raw.optString("status", raw.optString("value", ""))
            else -> raw.toString()
        }.lowercase()
        return when (status) {
            "start", "rec", "recording" -> true
            "stop", "idle", "standby" -> false
            else -> null
        }
    }

    /** `{"status": "on"}` from moviemode, or the same key inside an event body. */
    fun movieMode(json: JSONObject): Boolean? {
        val raw = json.opt("moviemode") ?: json.opt("status") ?: return null
        val status = (if (raw is JSONObject) raw.optString("status", raw.optString("value", "")) else raw.toString())
        return when (status.lowercase()) {
            "on" -> true
            "off" -> false
            else -> null
        }
    }

    /** The human-readable reason from an error body, which CCAPI puts in `message`. */
    fun errorMessage(body: String): String? =
        runCatching { JSONObject(body).optString("message").takeIf { it.isNotBlank() } }.getOrNull()
}

/** Turns raw CCAPI values into what you would read on the camera's own screen. */
object SettingFormat {

    private val labels = mapOf(
        "shootingmodedial" to "MODE",
        "shootingmode" to "MODE",
        "tv" to "SHUTTER",
        "av" to "APERTURE",
        "iso" to "ISO",
        "exposure" to "EXP COMP",
        "wb" to "WB",
        "colortemperature" to "KELVIN",
        "picturestyle" to "PICTURE STYLE",
        "afoperation" to "AF",
        "afmethod" to "AF METHOD",
        "metering" to "METERING",
        "drive" to "DRIVE",
        "aeb" to "AEB",
        "stillimagequality" to "QUALITY",
        "moviequality" to "MOVIE QUALITY",
        "flash" to "FLASH"
    )

    /** The exposure strip, in the order a camera operator reads it. */
    val exposureKeys = listOf("shootingmodedial", "tv", "av", "iso", "exposure", "wb", "colortemperature")

    fun label(key: String): String = labels[key] ?: key.uppercase()

    fun value(key: String, raw: String): String {
        if (raw.isEmpty()) return "—"
        return when (key) {
            "av" -> if (raw.startsWith("f")) "F" + raw.substring(1) else raw
            "iso" -> if (raw.equals("auto", true)) "AUTO" else raw
            "exposure" -> exposure(raw)
            "wb" -> when (raw.lowercase()) {
                "auto" -> "AWB"
                "autowhite" -> "AWB-W"
                "colortemp" -> "K"
                else -> words(raw)
            }
            "colortemperature" -> "${raw}K"
            "shootingmodedial", "shootingmode" -> raw.uppercase()
            else -> words(raw)
        }
    }

    /** `+1_1/3` → `+1 1/3`, `-0_2/3` → `-2/3`, `+0_0` → `±0`. */
    fun exposure(raw: String): String {
        val sign = raw.firstOrNull()?.takeIf { it == '+' || it == '-' }?.toString() ?: ""
        val body = raw.removePrefix(sign)
        val parts = body.split('_')
        val whole = parts.getOrNull(0)?.toIntOrNull() ?: return raw
        val fraction = parts.getOrNull(1)?.takeIf { it != "0" && it.isNotEmpty() }
        if (whole == 0 && fraction == null) return "±0"
        return buildString {
            append(sign)
            if (whole != 0 || fraction == null) append(whole)
            if (fraction != null) {
                if (whole != 0) append(' ')
                append(fraction)
            }
        }
    }

    private fun words(raw: String) = raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
