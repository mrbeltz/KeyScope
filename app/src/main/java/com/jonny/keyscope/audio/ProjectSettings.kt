package com.jonny.keyscope.audio

import android.content.Context
import android.content.SharedPreferences
import com.jonny.keyscope.Mode
import com.jonny.keyscope.MusicalKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What you are currently working in, so every reading can answer whether it fits. */
data class Project(val key: MusicalKey? = null, val bpm: Float = 0f) {
    val isSet: Boolean get() = key != null || bpm > 0f
}

/**
 * Survives restarts, because a project key is a thing you set once and work against for days.
 */
object ProjectSettings {

    private const val FILE = "keybro.project"
    private const val KEY_TONIC = "tonic"
    private const val KEY_MODE = "mode"
    private const val KEY_BPM = "bpm"

    private var prefs: SharedPreferences? = null

    private val _state = MutableStateFlow(Project())
    val state: StateFlow<Project> = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = store
        val tonic = store.getInt(KEY_TONIC, -1)
        val minor = store.getBoolean(KEY_MODE, true)
        _state.value = Project(
            key = if (tonic in 0..11) {
                MusicalKey(tonic, if (minor) Mode.MINOR else Mode.MAJOR)
            } else {
                null
            },
            bpm = store.getFloat(KEY_BPM, 0f)
        )
    }

    fun setKey(key: MusicalKey?) {
        _state.value = _state.value.copy(key = key)
        prefs?.edit()?.apply {
            if (key == null) {
                putInt(KEY_TONIC, -1)
            } else {
                putInt(KEY_TONIC, key.tonic)
                putBoolean(KEY_MODE, key.mode == Mode.MINOR)
            }
            apply()
        }
    }

    fun setBpm(bpm: Float) {
        val clean = if (bpm.isFinite() && bpm > 0f) bpm else 0f
        _state.value = _state.value.copy(bpm = clean)
        prefs?.edit()?.putFloat(KEY_BPM, clean)?.apply()
    }
}
