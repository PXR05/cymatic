package com.pxr.cymatic.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
    private const val DEFAULT_FADE_ENABLED = true
    private const val DEFAULT_RESUME_ON_BLUETOOTH_RECONNECT = false
    private const val DEFAULT_WHEEL_SENSITIVITY = 1f
    private val LOCKED_KEY = booleanPreferencesKey("LOCKED")
    private val FADE_ENABLED_KEY = booleanPreferencesKey("FADE_ENABLED")
    private val WHEEL_SENSITIVITY_KEY = floatPreferencesKey("WHEEL_SENSITIVITY")
    private val LAST_SCAN_TIME_MS_KEY = longPreferencesKey("LAST_SCAN_TIME_MS")
    private val LAST_SCAN_COUNT_KEY = longPreferencesKey("LAST_SCAN_COUNT")
    private val LAST_SCAN_DURATION_MS_KEY = longPreferencesKey("LAST_SCAN_DURATION_MS")
    private val SCAN_DIRECTORIES_KEY = stringSetPreferencesKey("SCAN_DIRECTORIES")
    private val SCAN_ALL_MEDIA_KEY = booleanPreferencesKey("SCAN_ALL_MEDIA")
    private val HIDE_ARTWORK_KEY = booleanPreferencesKey("HIDE_ARTWORK")
    private val RESUME_ON_BLUETOOTH_RECONNECT_KEY =
        booleanPreferencesKey("RESUME_ON_BLUETOOTH_RECONNECT")
    private val LIBRARY_FILTER_KEY = stringPreferencesKey("LIBRARY_FILTER")
    val libraryFilterFlow: Flow<String>
        get() = store.data.map { it[LIBRARY_FILTER_KEY] ?: "all" }

    suspend fun setLibraryFilter(value: String) = store.edit { it[LIBRARY_FILTER_KEY] = value }

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

    internal val currentPreferences: Preferences?
        get() = _prefs.value

    internal val store: DataStore<Preferences>
        get() {
            requireInit()
            return dataStore
        }

    val lockedFlow: Flow<Boolean>
        get() =
            store.data.map { prefs ->
                prefs[LOCKED_KEY] ?: DEFAULT_LOCKED
            }

    val hideArtworkFlow: Flow<Boolean>
        get() =
            store.data.map { prefs ->
                prefs[HIDE_ARTWORK_KEY] ?: DEFAULT_HIDE_ARTWORK
            }

    val lastScanTimeMsFlow: Flow<Long>
        get() =
            store.data.map { prefs ->
                prefs[LAST_SCAN_TIME_MS_KEY] ?: DEFAULT_LAST_SCAN_TIME_MS
            }

    val lastScanCountFlow: Flow<Long>
        get() =
            store.data.map { prefs ->
                prefs[LAST_SCAN_COUNT_KEY] ?: DEFAULT_LAST_SCAN_COUNT
            }

    val lastScanDurationMsFlow: Flow<Long>
        get() =
            store.data.map { prefs ->
                prefs[LAST_SCAN_DURATION_MS_KEY] ?: DEFAULT_LAST_SCAN_DURATION_MS
            }

    val scanDirectoriesFlow: Flow<List<String>>
        get() =
            store.data.map { prefs ->
                (prefs[SCAN_DIRECTORIES_KEY] ?: emptySet()).sorted()
            }

    val scanAllMediaFlow: Flow<Boolean>
        get() =
            store.data.map { prefs ->
                prefs[SCAN_ALL_MEDIA_KEY] ?: DEFAULT_SCAN_ALL_MEDIA
            }

    val resumeOnBluetoothReconnectFlow: Flow<Boolean>
        get() =
            store.data.map { prefs ->
                prefs[RESUME_ON_BLUETOOTH_RECONNECT_KEY] ?: DEFAULT_RESUME_ON_BLUETOOTH_RECONNECT
            }

    val fadeEnabledFlow: Flow<Boolean>
        get() =
            store.data.map { prefs ->
                prefs[FADE_ENABLED_KEY] ?: DEFAULT_FADE_ENABLED
            }

    val wheelSensitivityFlow: Flow<Float>
        get() =
            store.data.map {
                (it[WHEEL_SENSITIVITY_KEY] ?: DEFAULT_WHEEL_SENSITIVITY).coerceIn(
                    0.25f,
                    3f,
                )
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

    val currentScanDirectoriesNullable: List<String>?
        get() = _prefs.value?.let { prefs -> (prefs[SCAN_DIRECTORIES_KEY] ?: emptySet()).sorted() }

    val currentScanAllMedia: Boolean
        get() = _prefs.value?.get(SCAN_ALL_MEDIA_KEY) ?: DEFAULT_SCAN_ALL_MEDIA

    val currentResumeOnBluetoothReconnect: Boolean
        get() =
            _prefs.value?.get(RESUME_ON_BLUETOOTH_RECONNECT_KEY)
                ?: DEFAULT_RESUME_ON_BLUETOOTH_RECONNECT

    val currentWheelSensitivity: Float
        get() =
            (_prefs.value?.get(WHEEL_SENSITIVITY_KEY) ?: DEFAULT_WHEEL_SENSITIVITY).coerceIn(
                0.25f,
                3f,
            )

    val currentFadeEnabled: Boolean
        get() = _prefs.value?.get(FADE_ENABLED_KEY) ?: DEFAULT_FADE_ENABLED

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

    suspend fun setLastScanResult(timeMs: Long, count: Long, durationMs: Long) {
        store.edit { prefs ->
            prefs[LAST_SCAN_TIME_MS_KEY] = timeMs
            prefs[LAST_SCAN_COUNT_KEY] = count
            prefs[LAST_SCAN_DURATION_MS_KEY] = durationMs
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

    suspend fun setWheelSensitivity(value: Float) {
        store.edit { it[WHEEL_SENSITIVITY_KEY] = value.coerceIn(0.25f, 3f) }
    }

    suspend fun setFadeEnabled(value: Boolean) {
        store.edit { prefs ->
            prefs[FADE_ENABLED_KEY] = value
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
}
