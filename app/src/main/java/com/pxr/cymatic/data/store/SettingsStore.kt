package com.pxr.cymatic.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

    private val THEME_KEY = stringPreferencesKey("THEME")
    private val TIMEOUT_MS_KEY = longPreferencesKey("TIMEOUT_MS")
    private val CONTROLS_SELECT_KEY = stringPreferencesKey("CONTROLS_SELECT")
    private val CONTROLS_FORWARD_KEY = stringPreferencesKey("CONTROLS_FORWARD")
    private val CONTROLS_BACKWARD_KEY = stringPreferencesKey("CONTROLS_BACKWARD")

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

    suspend fun getTheme(): String = themeFlow.first()

    suspend fun getTimeoutMs(): Long = timeoutMsFlow.first()

    suspend fun getControlsSelect(): String = controlsSelectFlow.first()

    suspend fun getControlsForward(): String = controlsForwardFlow.first()

    suspend fun getControlsBackward(): String = controlsBackwardFlow.first()

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
}
