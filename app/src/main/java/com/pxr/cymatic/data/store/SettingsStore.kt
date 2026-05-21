package com.pxr.cymatic.data.store

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore("settings")

object SettingsStore {
    private lateinit var dataStore: DataStore<Preferences>

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _prefs = MutableStateFlow<Preferences?>(null)

    private const val DEFAULT_LOCKED = false
    private const val DEFAULT_LAST_SCAN_TIME_MS = 0L
    private const val DEFAULT_LAST_SCAN_COUNT = 0L
    private const val DEFAULT_LAST_SCAN_DURATION_MS = 0L
    private const val DEFAULT_SCAN_ALL_MEDIA = true
    private const val DEFAULT_HIDE_ARTWORK = false
    private val LOCKED_KEY = booleanPreferencesKey("LOCKED")
    private val LAST_SCAN_TIME_MS_KEY = longPreferencesKey("LAST_SCAN_TIME_MS")
    private val LAST_SCAN_COUNT_KEY = longPreferencesKey("LAST_SCAN_COUNT")
    private val LAST_SCAN_DURATION_MS_KEY = longPreferencesKey("LAST_SCAN_DURATION_MS")
    private val SCAN_DIRECTORIES_KEY = stringSetPreferencesKey("SCAN_DIRECTORIES")
    private val SCAN_ALL_MEDIA_KEY = booleanPreferencesKey("SCAN_ALL_MEDIA")
    private val HIDE_ARTWORK_KEY = booleanPreferencesKey("HIDE_ARTWORK")
    private val EQ_PRESETS_KEY = stringPreferencesKey("EQ_PRESETS")
    private val EQ_SELECTED_PRESET_KEY = stringPreferencesKey("EQ_SELECTED_PRESET")
    private val EQ_GLOBAL_ENABLED_KEY = booleanPreferencesKey("EQ_GLOBAL_ENABLED")
    private val EQ_ACTIVE_AUDIO_DEVICE_KEY = stringPreferencesKey("EQ_ACTIVE_AUDIO_DEVICE")
    private val EQ_DEVICE_PRESETS_KEY = stringPreferencesKey("EQ_DEVICE_PRESETS")

    fun init(context: Context) {
        dataStore = context.applicationContext.dataStore
        scope.launch {
            dataStore.data.collect {
                _prefs.value = it
            }
        }
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

    val lockedFlow: Flow<Boolean>
        get() = store.data.map { prefs ->
            prefs[LOCKED_KEY] ?: DEFAULT_LOCKED
        }

    val hideArtworkFlow: Flow<Boolean>
        get() = store.data.map { prefs ->
            prefs[HIDE_ARTWORK_KEY] ?: DEFAULT_HIDE_ARTWORK
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

    val currentLocked: Boolean
        get() = _prefs.value?.get(LOCKED_KEY) ?: DEFAULT_LOCKED

    val currentHideArtwork: Boolean
        get() = _prefs.value?.get(HIDE_ARTWORK_KEY) ?: DEFAULT_HIDE_ARTWORK

    val currentLastScanTimeMs: Long
        get() = _prefs.value?.get(LAST_SCAN_TIME_MS_KEY) ?: DEFAULT_LAST_SCAN_TIME_MS

    val currentLastScanCount: Long
        get() = _prefs.value?.get(LAST_SCAN_COUNT_KEY) ?: DEFAULT_LAST_SCAN_COUNT

    val currentLastScanDurationMs: Long
        get() = _prefs.value?.get(LAST_SCAN_DURATION_MS_KEY) ?: DEFAULT_LAST_SCAN_DURATION_MS

    val currentScanDirectories: List<String>
        get() = (_prefs.value?.get(SCAN_DIRECTORIES_KEY) ?: emptySet()).sorted()

    val currentScanAllMedia: Boolean
        get() = _prefs.value?.get(SCAN_ALL_MEDIA_KEY) ?: DEFAULT_SCAN_ALL_MEDIA

    private fun getEqPresetsList(prefs: Preferences): List<com.pxr.cymatic.data.model.EqPreset> {
        val jsonStr = prefs[EQ_PRESETS_KEY] ?: "[]"
        return try {
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<com.pxr.cymatic.data.model.EqPreset>()
            for (i in 0 until array.length()) {
                com.pxr.cymatic.data.model.EqPreset.fromJson(array.getJSONObject(i).toString())?.let { list.add(it) }
            }
            if (list.isEmpty()) listOf(com.pxr.cymatic.data.model.EqPreset.defaultPreset()) else list
        } catch (e: Exception) {
            Log.e("SettingsStore", "Failed to parse EQ presets: ${e.message}")
            listOf(com.pxr.cymatic.data.model.EqPreset.defaultPreset())
        }
    }

    val eqPresetsFlow: Flow<List<com.pxr.cymatic.data.model.EqPreset>>
        get() = store.data.map { prefs -> getEqPresetsList(prefs) }

    val currentEqPresets: List<com.pxr.cymatic.data.model.EqPreset>
        get() = _prefs.value?.let { getEqPresetsList(it) } ?: listOf(com.pxr.cymatic.data.model.EqPreset.defaultPreset())

    val eqSelectedPresetFlow: Flow<String>
        get() = store.data.map { prefs ->
            prefs[EQ_SELECTED_PRESET_KEY] ?: "Flat"
        }

    val currentEqSelectedPreset: String
        get() = _prefs.value?.get(EQ_SELECTED_PRESET_KEY) ?: "Flat"

    val eqGlobalEnabledFlow: Flow<Boolean>
        get() = store.data.map { prefs ->
            prefs[EQ_GLOBAL_ENABLED_KEY] ?: true
        }

    val currentEqGlobalEnabled: Boolean
        get() = _prefs.value?.get(EQ_GLOBAL_ENABLED_KEY) ?: true

    val activeAudioDeviceFlow: Flow<String>
        get() = store.data.map { prefs ->
            prefs[EQ_ACTIVE_AUDIO_DEVICE_KEY] ?: ""
        }

    val currentActiveAudioDevice: String
        get() = _prefs.value?.get(EQ_ACTIVE_AUDIO_DEVICE_KEY) ?: ""

    val eqDevicePresetsFlow: Flow<Map<String, String>>
        get() = store.data.map { prefs ->
            parseStringMap(prefs[EQ_DEVICE_PRESETS_KEY])
        }

    val currentEqDevicePresets: Map<String, String>
        get() = parseStringMap(_prefs.value?.get(EQ_DEVICE_PRESETS_KEY))

    val effectiveEqSelectedPresetFlow: Flow<String>
        get() = store.data.map { prefs ->
            val globalPreset = prefs[EQ_SELECTED_PRESET_KEY] ?: "Flat"
            val activeDevice = prefs[EQ_ACTIVE_AUDIO_DEVICE_KEY].orEmpty()
            val devicePresets = parseStringMap(prefs[EQ_DEVICE_PRESETS_KEY])
            devicePresets[activeDevice] ?: globalPreset
        }

    suspend fun isLocked(): Boolean = lockedFlow.first()

    suspend fun getHideArtwork(): Boolean = hideArtworkFlow.first()

    suspend fun getLastScanTimeMs(): Long = lastScanTimeMsFlow.first()

    suspend fun getLastScanCount(): Long = lastScanCountFlow.first()

    suspend fun getLastScanDurationMs(): Long = lastScanDurationMsFlow.first()

    suspend fun getScanDirectories(): List<String> = scanDirectoriesFlow.first()

    suspend fun getScanAllMedia(): Boolean = scanAllMediaFlow.first()

    suspend fun setLocked(value: Boolean) {
        store.edit { prefs ->
            prefs[LOCKED_KEY] = value
        }
    }

    suspend fun setHideArtwork(value: Boolean) {
        store.edit { prefs ->
            prefs[HIDE_ARTWORK_KEY] = value
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

    suspend fun setEqSelectedPresetForActiveDevice(name: String) {
        store.edit { prefs ->
            val activeDevice = prefs[EQ_ACTIVE_AUDIO_DEVICE_KEY].orEmpty()
            if (activeDevice.isBlank()) {
                prefs[EQ_SELECTED_PRESET_KEY] = name
                return@edit
            }
            val devicePresets = parseStringMap(prefs[EQ_DEVICE_PRESETS_KEY]).toMutableMap()
            devicePresets[activeDevice] = name
            prefs[EQ_DEVICE_PRESETS_KEY] = encodeStringMap(devicePresets)
        }
    }

    suspend fun setEqDevicePreset(deviceKey: String, name: String) {
        if (deviceKey.isBlank()) return
        store.edit { prefs ->
            val devicePresets = parseStringMap(prefs[EQ_DEVICE_PRESETS_KEY]).toMutableMap()
            devicePresets[deviceKey] = name
            prefs[EQ_DEVICE_PRESETS_KEY] = encodeStringMap(devicePresets)
        }
    }

    suspend fun setActiveAudioDevice(deviceKey: String) {
        store.edit { prefs ->
            prefs[EQ_ACTIVE_AUDIO_DEVICE_KEY] = deviceKey
        }
    }

    suspend fun removeEqDevicePreset(deviceKey: String) {
        store.edit { prefs ->
            val devicePresets = parseStringMap(prefs[EQ_DEVICE_PRESETS_KEY]).toMutableMap()
            devicePresets.remove(deviceKey)
            prefs[EQ_DEVICE_PRESETS_KEY] = encodeStringMap(devicePresets)
        }
    }

    suspend fun setEqGlobalEnabled(enabled: Boolean) {
        store.edit { prefs ->
            prefs[EQ_GLOBAL_ENABLED_KEY] = enabled
        }
    }

    private fun parseStringMap(jsonStr: String?): Map<String, String> {
        if (jsonStr.isNullOrBlank()) return emptyMap()
        return try {
            val json = org.json.JSONObject(jsonStr)
            json.keys().asSequence().associateWith { key -> json.optString(key) }
                .filterValues { it.isNotBlank() }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun encodeStringMap(values: Map<String, String>): String {
        val json = org.json.JSONObject()
        values.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) {
                json.put(key, value)
            }
        }
        return json.toString()
    }
}
