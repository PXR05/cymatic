package com.pxr.cymatic.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.playbackDataStore by preferencesDataStore("playback")

object PlaybackStore {
    private lateinit var dataStore: DataStore<Preferences>

    private val QUEUE_IDS_KEY = stringPreferencesKey("QUEUE_IDS")
    private val CURRENT_INDEX_KEY = intPreferencesKey("CURRENT_INDEX")
    private val POSITION_MS_KEY = longPreferencesKey("POSITION_MS")
    private val SHUFFLE_ENABLED_KEY = booleanPreferencesKey("SHUFFLE_ENABLED")
    private val REPEAT_MODE_KEY = intPreferencesKey("REPEAT_MODE")
    private val QUEUE_SOURCE_KEY = stringPreferencesKey("QUEUE_SOURCE")
    private val WAS_PLAYING_KEY = booleanPreferencesKey("WAS_PLAYING")

    data class PersistedPlaybackState(
        val queueIds: List<Long>,
        val currentIndex: Int,
        val positionMs: Long,
        val shuffleEnabled: Boolean,
        val repeatMode: Int,
        val queueSource: String?,
        val wasPlaying: Boolean
    )

    fun init(context: Context) {
        dataStore = context.applicationContext.playbackDataStore
    }

    private fun requireInit() {
        check(::dataStore.isInitialized) {
            "PlaybackStore.init(context) must be called before use."
        }
    }

    private val store: DataStore<Preferences>
        get() {
            requireInit()
            return dataStore
        }

    suspend fun saveState(state: PersistedPlaybackState) {
        store.edit { prefs ->
            prefs[QUEUE_IDS_KEY] = state.queueIds.joinToString(",")
            prefs[CURRENT_INDEX_KEY] = state.currentIndex
            prefs[POSITION_MS_KEY] = state.positionMs
            prefs[SHUFFLE_ENABLED_KEY] = state.shuffleEnabled
            prefs[REPEAT_MODE_KEY] = state.repeatMode
            if (state.queueSource.isNullOrBlank()) {
                prefs.remove(QUEUE_SOURCE_KEY)
            } else {
                prefs[QUEUE_SOURCE_KEY] = state.queueSource
            }
            prefs[WAS_PLAYING_KEY] = state.wasPlaying
        }
    }

    suspend fun loadState(): PersistedPlaybackState? {
        val prefs = store.data.first()
        val queueIds = parseQueueIds(prefs[QUEUE_IDS_KEY])
        if (queueIds.isEmpty()) return null
        return PersistedPlaybackState(
            queueIds = queueIds,
            currentIndex = prefs[CURRENT_INDEX_KEY] ?: 0,
            positionMs = prefs[POSITION_MS_KEY] ?: 0L,
            shuffleEnabled = prefs[SHUFFLE_ENABLED_KEY] ?: false,
            repeatMode = prefs[REPEAT_MODE_KEY] ?: 0,
            queueSource = prefs[QUEUE_SOURCE_KEY],
            wasPlaying = prefs[WAS_PLAYING_KEY] ?: false
        )
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private fun parseQueueIds(raw: String?): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
    }
}

