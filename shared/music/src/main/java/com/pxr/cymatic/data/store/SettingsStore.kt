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
import com.pxr.cymatic.data.model.EqPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray

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
    private const val DEFAULT_FADE_ENABLED = true
    private const val DEFAULT_RESUME_ON_BLUETOOTH_RECONNECT = false
    const val DEFAULT_SYNC_URL = "https://audiostream.pxr.dpdns.org/"
    const val DEFAULT_SYNC_USERNAME = "pxr"
    private const val DEFAULT_SYNC_ADAPTER = "audiostream"
    private const val DEFAULT_SYNC_NETWORK = "never"
    private const val DEFAULT_SYNC_INTERVAL_HOURS = 24L
    private const val DEFAULT_SYNC_LAYOUT = "artist_album_tracks"
    private const val DEFAULT_SYNC_CONTENT_MODE = "all"
    private val LOCKED_KEY = booleanPreferencesKey("LOCKED")
    private val FADE_ENABLED_KEY = booleanPreferencesKey("FADE_ENABLED")
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
    private val RESUME_ON_BLUETOOTH_RECONNECT_KEY = booleanPreferencesKey("RESUME_ON_BLUETOOTH_RECONNECT")
    private val SYNC_URL_KEY = stringPreferencesKey("SYNC_URL")
    private val SYNC_USERNAME_KEY = stringPreferencesKey("SYNC_USERNAME")
    private val SYNC_ADAPTER_KEY = stringPreferencesKey("SYNC_ADAPTER")
    private val SYNC_NETWORK_KEY = stringPreferencesKey("SYNC_NETWORK")
    private val SYNC_INTERVAL_HOURS_KEY = longPreferencesKey("SYNC_INTERVAL_HOURS")
    private val SYNC_LAYOUT_KEY = stringPreferencesKey("SYNC_LAYOUT")
    private val SYNC_DIRECTORY_KEY = stringPreferencesKey("SYNC_DIRECTORY")
    private val SYNC_LAST_TIME_KEY = longPreferencesKey("SYNC_LAST_TIME")
    private val SYNC_LAST_RESULT_KEY = stringPreferencesKey("SYNC_LAST_RESULT")
    private val SYNC_PLAYLIST_IDS_KEY = stringPreferencesKey("SYNC_PLAYLIST_IDS")
    private val SYNC_CONTENT_MODE_KEY = stringPreferencesKey("SYNC_CONTENT_MODE")
    private val SYNC_SELECTED_PLAYLISTS_KEY = stringSetPreferencesKey("SYNC_SELECTED_PLAYLISTS")

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

    val resumeOnBluetoothReconnectFlow: Flow<Boolean>
        get() = store.data.map { prefs ->
            prefs[RESUME_ON_BLUETOOTH_RECONNECT_KEY] ?: DEFAULT_RESUME_ON_BLUETOOTH_RECONNECT
        }

    val fadeEnabledFlow: Flow<Boolean>
        get() = store.data.map { prefs ->
            prefs[FADE_ENABLED_KEY] ?: DEFAULT_FADE_ENABLED
        }

    val syncUrlFlow: Flow<String> get() = store.data.map { it[SYNC_URL_KEY] ?: DEFAULT_SYNC_URL }
    val syncUsernameFlow: Flow<String> get() = store.data.map { it[SYNC_USERNAME_KEY] ?: DEFAULT_SYNC_USERNAME }
    val syncAdapterFlow: Flow<String> get() = store.data.map { it[SYNC_ADAPTER_KEY] ?: DEFAULT_SYNC_ADAPTER }
    val syncNetworkFlow: Flow<String> get() = store.data.map { it[SYNC_NETWORK_KEY] ?: DEFAULT_SYNC_NETWORK }
    val syncIntervalHoursFlow: Flow<Long> get() = store.data.map { it[SYNC_INTERVAL_HOURS_KEY] ?: DEFAULT_SYNC_INTERVAL_HOURS }
    val syncLayoutFlow: Flow<String> get() = store.data.map { it[SYNC_LAYOUT_KEY] ?: DEFAULT_SYNC_LAYOUT }
    val syncDirectoryFlow: Flow<String> get() = store.data.map { it[SYNC_DIRECTORY_KEY].orEmpty() }
    val syncLastTimeFlow: Flow<Long> get() = store.data.map { it[SYNC_LAST_TIME_KEY] ?: 0L }
    val syncLastResultFlow: Flow<String> get() = store.data.map { it[SYNC_LAST_RESULT_KEY] ?: "Not synced yet" }
    val syncContentModeFlow: Flow<String> get() = store.data.map { it[SYNC_CONTENT_MODE_KEY] ?: DEFAULT_SYNC_CONTENT_MODE }
    val syncSelectedPlaylistsFlow: Flow<Set<String>> get() = store.data.map { it[SYNC_SELECTED_PLAYLISTS_KEY] ?: emptySet() }

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

    val currentScanDirectoriesNullable: List<String>?
        get() = _prefs.value?.let { prefs -> (prefs[SCAN_DIRECTORIES_KEY] ?: emptySet()).sorted() }

    val currentScanAllMedia: Boolean
        get() = _prefs.value?.get(SCAN_ALL_MEDIA_KEY) ?: DEFAULT_SCAN_ALL_MEDIA

    val currentResumeOnBluetoothReconnect: Boolean
        get() = _prefs.value?.get(RESUME_ON_BLUETOOTH_RECONNECT_KEY) ?: DEFAULT_RESUME_ON_BLUETOOTH_RECONNECT

    val currentFadeEnabled: Boolean
        get() = _prefs.value?.get(FADE_ENABLED_KEY) ?: DEFAULT_FADE_ENABLED

    val currentSyncUrl: String get() = _prefs.value?.get(SYNC_URL_KEY) ?: DEFAULT_SYNC_URL
    val currentSyncUsername: String get() = _prefs.value?.get(SYNC_USERNAME_KEY) ?: DEFAULT_SYNC_USERNAME
    val currentSyncAdapter: String get() = _prefs.value?.get(SYNC_ADAPTER_KEY) ?: DEFAULT_SYNC_ADAPTER
    val currentSyncNetwork: String get() = _prefs.value?.get(SYNC_NETWORK_KEY) ?: DEFAULT_SYNC_NETWORK
    val currentSyncIntervalHours: Long get() = _prefs.value?.get(SYNC_INTERVAL_HOURS_KEY) ?: DEFAULT_SYNC_INTERVAL_HOURS
    val currentSyncLayout: String get() = _prefs.value?.get(SYNC_LAYOUT_KEY) ?: DEFAULT_SYNC_LAYOUT
    val currentSyncDirectory: String get() = _prefs.value?.get(SYNC_DIRECTORY_KEY).orEmpty()
    val currentSyncContentMode: String get() = _prefs.value?.get(SYNC_CONTENT_MODE_KEY) ?: DEFAULT_SYNC_CONTENT_MODE
    val currentSyncSelectedPlaylists: Set<String> get() = _prefs.value?.get(SYNC_SELECTED_PLAYLISTS_KEY) ?: emptySet()

    private fun getEqPresetsList(prefs: Preferences): List<EqPreset> {
        val jsonStr = prefs[EQ_PRESETS_KEY] ?: "[]"
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<EqPreset>()
            for (i in 0 until array.length()) {
                EqPreset.fromJson(array.getJSONObject(i).toString())?.let { list.add(it) }
            }
            if (list.isEmpty()) listOf(EqPreset.defaultPreset()) else list
        } catch (e: Exception) {
            Log.e("SettingsStore", "Failed to parse EQ presets: ${e.message}")
            listOf(EqPreset.defaultPreset())
        }
    }

    val eqPresetsFlow: Flow<List<EqPreset>>
        get() = store.data.map { prefs -> getEqPresetsList(prefs) }

    val currentEqPresets: List<EqPreset>
        get() = _prefs.value?.let { getEqPresetsList(it) } ?: listOf(EqPreset.defaultPreset())

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

    suspend fun setResumeOnBluetoothReconnect(value: Boolean) {
        store.edit { prefs ->
            prefs[RESUME_ON_BLUETOOTH_RECONNECT_KEY] = value
        }
    }

    suspend fun setFadeEnabled(value: Boolean) {
        store.edit { prefs ->
            prefs[FADE_ENABLED_KEY] = value
        }
    }

    suspend fun setSyncUrl(value: String) = store.edit { it[SYNC_URL_KEY] = value.trim() }
    suspend fun setSyncUsername(value: String) = store.edit { it[SYNC_USERNAME_KEY] = value.trim() }
    suspend fun setSyncAdapter(value: String) = store.edit { it[SYNC_ADAPTER_KEY] = value }
    suspend fun setSyncNetwork(value: String) = store.edit { it[SYNC_NETWORK_KEY] = value }
    suspend fun setSyncIntervalHours(value: Long) = store.edit { it[SYNC_INTERVAL_HOURS_KEY] = value.coerceAtLeast(1L) }
    suspend fun setSyncLayout(value: String) = store.edit { it[SYNC_LAYOUT_KEY] = value }
    suspend fun setSyncDirectory(value: String) = store.edit { it[SYNC_DIRECTORY_KEY] = value }
    suspend fun setSyncContentMode(value: String) = store.edit { it[SYNC_CONTENT_MODE_KEY] = value }
    suspend fun setSyncSelectedPlaylists(value: Set<String>) = store.edit { it[SYNC_SELECTED_PLAYLISTS_KEY] = value }
    suspend fun setSyncResult(time: Long, result: String) = store.edit {
        it[SYNC_LAST_TIME_KEY] = time
        it[SYNC_LAST_RESULT_KEY] = result
    }

    suspend fun getSyncUrl(): String = syncUrlFlow.first()
    suspend fun getSyncUsername(): String = syncUsernameFlow.first()
    suspend fun getSyncAdapter(): String = syncAdapterFlow.first()
    suspend fun getSyncNetwork(): String = syncNetworkFlow.first()
    suspend fun getSyncIntervalHours(): Long = syncIntervalHoursFlow.first()
    suspend fun getSyncLayout(): String = syncLayoutFlow.first()
    suspend fun getSyncDirectory(): String = syncDirectoryFlow.first()
    suspend fun getSyncContentMode(): String = syncContentModeFlow.first()
    suspend fun getSyncSelectedPlaylists(): Set<String> = syncSelectedPlaylistsFlow.first()
    suspend fun getSyncPlaylistIds(): Map<String, Long> =
        parseLongMap(store.data.first()[SYNC_PLAYLIST_IDS_KEY])

    suspend fun setSyncPlaylistIds(value: Map<String, Long>) = store.edit { prefs ->
        val json = org.json.JSONObject()
        value.forEach { (remoteId, localId) -> json.put(remoteId, localId) }
        prefs[SYNC_PLAYLIST_IDS_KEY] = json.toString()
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

    suspend fun setEqPresets(presets: List<EqPreset>) {
        store.edit { prefs ->
            val array = JSONArray()
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

    private fun parseLongMap(jsonStr: String?): Map<String, Long> {
        if (jsonStr.isNullOrBlank()) return emptyMap()
        return runCatching {
            val json = org.json.JSONObject(jsonStr)
            json.keys().asSequence().associateWith(json::getLong)
        }.getOrDefault(emptyMap())
    }
}
