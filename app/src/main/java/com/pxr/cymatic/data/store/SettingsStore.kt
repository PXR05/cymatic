package com.pxr.cymatic.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

object SettingsStore {
    private lateinit var dataStore: DataStore<Preferences>

    private const val DEFAULT_THEME = "system"
    private const val DEFAULT_TIMEOUT_MS = 30_000L
    private const val DEFAULT_CONTROLS_SELECT = ""
    private const val DEFAULT_CONTROLS_FORWARD = ""
    private const val DEFAULT_CONTROLS_BACKWARD = ""
    private const val DEFAULT_LOCKED = false
    private const val DEFAULT_LAST_SCAN_TIME_MS = 0L
    private const val DEFAULT_LAST_SCAN_COUNT = 0L
    private const val DEFAULT_LAST_SCAN_DURATION_MS = 0L
    private const val DEFAULT_SCAN_ALL_MEDIA = true

    private val THEME_KEY = stringPreferencesKey("THEME")
    private val TIMEOUT_MS_KEY = longPreferencesKey("TIMEOUT_MS")
    private val CONTROLS_SELECT_KEY = stringPreferencesKey("CONTROLS_SELECT")
    private val CONTROLS_FORWARD_KEY = stringPreferencesKey("CONTROLS_FORWARD")
    private val CONTROLS_BACKWARD_KEY = stringPreferencesKey("CONTROLS_BACKWARD")
    private val LOCKED_KEY = booleanPreferencesKey("LOCKED")
    private val LAST_SCAN_TIME_MS_KEY = longPreferencesKey("LAST_SCAN_TIME_MS")
    private val LAST_SCAN_COUNT_KEY = longPreferencesKey("LAST_SCAN_COUNT")
    private val LAST_SCAN_DURATION_MS_KEY = longPreferencesKey("LAST_SCAN_DURATION_MS")
    private val SCAN_DIRECTORIES_KEY = stringSetPreferencesKey("SCAN_DIRECTORIES")
    private val SCAN_ALL_MEDIA_KEY = booleanPreferencesKey("SCAN_ALL_MEDIA")
    private val EQ_PRESETS_KEY = stringPreferencesKey("EQ_PRESETS")
    private val EQ_SELECTED_PRESET_KEY = stringPreferencesKey("EQ_SELECTED_PRESET")
    private val EQ_GLOBAL_ENABLED_KEY = booleanPreferencesKey("EQ_GLOBAL_ENABLED")

    fun init(context: Context) {
        dataStore = context.applicationContext.dataStore
    }

    private fun requireInit() {
        check(::dataStore.isInitialized) {
            "SettingsStore.init(context) must be called before use."
        }
    }

    private val store: DataStore<Preferences>
        get() {
            requireInit()
            return dataStore
        }

    val themeFlow: Flow<String>
        get() = store.data.map { prefs ->
            prefs[THEME_KEY] ?: DEFAULT_THEME
        }

    val timeoutMsFlow: Flow<Long>
        get() = store.data.map { prefs ->
            prefs[TIMEOUT_MS_KEY] ?: DEFAULT_TIMEOUT_MS
        }

    val controlsSelectFlow: Flow<String>
        get() = store.data.map { prefs ->
            prefs[CONTROLS_SELECT_KEY] ?: DEFAULT_CONTROLS_SELECT
        }

    val controlsForwardFlow: Flow<String>
        get() = store.data.map { prefs ->
            prefs[CONTROLS_FORWARD_KEY] ?: DEFAULT_CONTROLS_FORWARD
        }

    val controlsBackwardFlow: Flow<String>
        get() = store.data.map { prefs ->
            prefs[CONTROLS_BACKWARD_KEY] ?: DEFAULT_CONTROLS_BACKWARD
        }

    val lockedFlow: Flow<Boolean>
        get() = store.data.map { prefs ->
            prefs[LOCKED_KEY] ?: DEFAULT_LOCKED
        }

    val lastScanTimeMsFlow: Flow<Long>
        get() = store.data.map { prefs ->
            prefs[LAST_SCAN_TIME_MS_KEY] ?: DEFAULT_LAST_SCAN_TIME_MS
        }

    val lastScanCountFlow: Flow<Long>
        get() = store.data.map { prefs ->
            prefs[LAST_SCAN_COUNT_KEY] ?: DEFAULT_LAST_SCAN_COUNT
        }

    val lastScanDurationMsFlow: Flow<Long>
        get() = store.data.map { prefs ->
            prefs[LAST_SCAN_DURATION_MS_KEY] ?: DEFAULT_LAST_SCAN_DURATION_MS
        }

    val scanDirectoriesFlow: Flow<List<String>>
        get() = store.data.map { prefs ->
            (prefs[SCAN_DIRECTORIES_KEY] ?: emptySet()).sorted()
        }

    val scanAllMediaFlow: Flow<Boolean>
        get() = store.data.map { prefs ->
            prefs[SCAN_ALL_MEDIA_KEY] ?: DEFAULT_SCAN_ALL_MEDIA
        }

    val eqPresetsFlow: Flow<List<com.pxr.cymatic.data.model.EqPreset>>
        get() = store.data.map { prefs ->
            val jsonStr = prefs[EQ_PRESETS_KEY] ?: "[]"
            try {
                val array = org.json.JSONArray(jsonStr)
                val list = mutableListOf<com.pxr.cymatic.data.model.EqPreset>()
                for (i in 0 until array.length()) {
                    com.pxr.cymatic.data.model.EqPreset.fromJson(array.getJSONObject(i).toString())?.let { list.add(it) }
                }
                if (list.isEmpty()) listOf(com.pxr.cymatic.data.model.EqPreset.defaultPreset()) else list
            } catch (e: Exception) {
                listOf(com.pxr.cymatic.data.model.EqPreset.defaultPreset())
            }
        }

    val eqSelectedPresetFlow: Flow<String>
        get() = store.data.map { prefs ->
            prefs[EQ_SELECTED_PRESET_KEY] ?: "Flat"
        }

    val eqGlobalEnabledFlow: Flow<Boolean>
        get() = store.data.map { prefs ->
            prefs[EQ_GLOBAL_ENABLED_KEY] ?: true
        }

    suspend fun getTheme(): String = themeFlow.first()

    suspend fun getTimeoutMs(): Long = timeoutMsFlow.first()

    suspend fun getControlsSelect(): String = controlsSelectFlow.first()

    suspend fun getControlsForward(): String = controlsForwardFlow.first()

    suspend fun getControlsBackward(): String = controlsBackwardFlow.first()

    suspend fun isLocked(): Boolean = lockedFlow.first()

    suspend fun getLastScanTimeMs(): Long = lastScanTimeMsFlow.first()

    suspend fun getLastScanCount(): Long = lastScanCountFlow.first()

    suspend fun getLastScanDurationMs(): Long = lastScanDurationMsFlow.first()

    suspend fun getScanDirectories(): List<String> = scanDirectoriesFlow.first()

    suspend fun getScanAllMedia(): Boolean = scanAllMediaFlow.first()

    suspend fun setTheme(value: String) {
        store.edit { prefs ->
            prefs[THEME_KEY] = value
        }
    }

    suspend fun setTimeoutMs(value: Long) {
        store.edit { prefs ->
            prefs[TIMEOUT_MS_KEY] = value
        }
    }

    suspend fun setControlsSelect(value: String) {
        store.edit { prefs ->
            prefs[CONTROLS_SELECT_KEY] = value
        }
    }

    suspend fun setControlsForward(value: String) {
        store.edit { prefs ->
            prefs[CONTROLS_FORWARD_KEY] = value
        }
    }

    suspend fun setControlsBackward(value: String) {
        store.edit { prefs ->
            prefs[CONTROLS_BACKWARD_KEY] = value
        }
    }

    suspend fun setLocked(value: Boolean) {
        store.edit { prefs ->
            prefs[LOCKED_KEY] = value
        }
    }

    suspend fun setLastScanTimeMs(value: Long) {
        store.edit { prefs ->
            prefs[LAST_SCAN_TIME_MS_KEY] = value
        }
    }

    suspend fun setLastScanCount(value: Long) {
        store.edit { prefs ->
            prefs[LAST_SCAN_COUNT_KEY] = value
        }
    }

    suspend fun setLastScanDurationMs(value: Long) {
        store.edit { prefs ->
            prefs[LAST_SCAN_DURATION_MS_KEY] = value
        }
    }

    suspend fun setScanDirectories(value: Set<String>) {
        store.edit { prefs ->
            prefs[SCAN_DIRECTORIES_KEY] = value
        }
    }

    suspend fun setScanAllMedia(value: Boolean) {
        store.edit { prefs ->
            prefs[SCAN_ALL_MEDIA_KEY] = value
        }
    }

    suspend fun addScanDirectory(value: String) {
        store.edit { prefs ->
            val current = prefs[SCAN_DIRECTORIES_KEY] ?: emptySet()
            prefs[SCAN_DIRECTORIES_KEY] = current + value
        }
    }

    suspend fun removeScanDirectory(value: String) {
        store.edit { prefs ->
            val current = prefs[SCAN_DIRECTORIES_KEY] ?: emptySet()
            prefs[SCAN_DIRECTORIES_KEY] = current - value
        }
    }

    suspend fun setEqPresets(presets: List<com.pxr.cymatic.data.model.EqPreset>) {
        store.edit { prefs ->
            val array = org.json.JSONArray()
            presets.forEach { array.put(it.toJson()) }
            prefs[EQ_PRESETS_KEY] = array.toString()
        }
    }

    suspend fun setEqSelectedPreset(name: String) {
        store.edit { prefs ->
            prefs[EQ_SELECTED_PRESET_KEY] = name
        }
    }

    suspend fun setEqGlobalEnabled(enabled: Boolean) {
        store.edit { prefs ->
            prefs[EQ_GLOBAL_ENABLED_KEY] = enabled
        }
    }
}

