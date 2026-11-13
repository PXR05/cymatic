package com.pxr.cymatic.data.store

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

object LibrarySyncSettings {
    const val DEFAULT_SYNC_URL = "https://audiostream.pxr.dpdns.org/"
    const val DEFAULT_SYNC_USERNAME = "pxr"
    private const val DEFAULT_SYNC_ADAPTER = "audiostream"
    private const val DEFAULT_SYNC_NETWORK = "never"
    private const val DEFAULT_SYNC_INTERVAL_HOURS = 24L
    private const val DEFAULT_SYNC_LAYOUT = "artist_album_tracks"
    private const val DEFAULT_SYNC_CONTENT_MODE = "none"
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
    private val SYNC_CONTENT_CONFIGURED_KEY = booleanPreferencesKey("SYNC_CONTENT_CONFIGURED")
    private val SYNC_SELECTED_PLAYLISTS_KEY = stringSetPreferencesKey("SYNC_SELECTED_PLAYLISTS")
    val syncUrlFlow: Flow<String>
        get() = SettingsStore.store.data.map { it[SYNC_URL_KEY] ?: DEFAULT_SYNC_URL }

    val syncUsernameFlow: Flow<String>
        get() =
            SettingsStore.store.data.map {
                it[SYNC_USERNAME_KEY] ?: DEFAULT_SYNC_USERNAME
            }

    val syncAdapterFlow: Flow<String>
        get() =
            SettingsStore.store.data.map {
                it[SYNC_ADAPTER_KEY] ?: DEFAULT_SYNC_ADAPTER
            }

    val syncNetworkFlow: Flow<String>
        get() =
            SettingsStore.store.data.map {
                it[SYNC_NETWORK_KEY] ?: DEFAULT_SYNC_NETWORK
            }

    val syncIntervalHoursFlow: Flow<Long>
        get() =
            SettingsStore.store.data.map {
                it[SYNC_INTERVAL_HOURS_KEY] ?: DEFAULT_SYNC_INTERVAL_HOURS
            }

    val syncLayoutFlow: Flow<String>
        get() =
            SettingsStore.store.data.map {
                it[SYNC_LAYOUT_KEY] ?: DEFAULT_SYNC_LAYOUT
            }

    val syncDirectoryFlow: Flow<String>
        get() = SettingsStore.store.data.map { it[SYNC_DIRECTORY_KEY].orEmpty() }

    val syncLastTimeFlow: Flow<Long>
        get() = SettingsStore.store.data.map { it[SYNC_LAST_TIME_KEY] ?: 0L }

    val syncLastResultFlow: Flow<String>
        get() =
            SettingsStore.store.data.map {
                it[SYNC_LAST_RESULT_KEY] ?: "Not synced yet"
            }

    val syncContentModeFlow: Flow<String>
        get() =
            SettingsStore.store.data.map {
                it[SYNC_CONTENT_MODE_KEY] ?: DEFAULT_SYNC_CONTENT_MODE
            }

    val syncContentConfiguredFlow: Flow<Boolean>
        get() =
            SettingsStore.store.data.map {
                it[SYNC_CONTENT_CONFIGURED_KEY] ?: (it[SYNC_CONTENT_MODE_KEY] != null)
            }

    val syncSelectedPlaylistsFlow: Flow<Set<String>>
        get() =
            SettingsStore.store.data.map {
                it[SYNC_SELECTED_PLAYLISTS_KEY] ?: emptySet()
            }

    val currentSyncUrl: String
        get() = SettingsStore.currentPreferences?.get(SYNC_URL_KEY) ?: DEFAULT_SYNC_URL

    val currentSyncUsername: String
        get() = SettingsStore.currentPreferences?.get(SYNC_USERNAME_KEY) ?: DEFAULT_SYNC_USERNAME

    val currentSyncAdapter: String
        get() = SettingsStore.currentPreferences?.get(SYNC_ADAPTER_KEY) ?: DEFAULT_SYNC_ADAPTER

    val currentSyncNetwork: String
        get() = SettingsStore.currentPreferences?.get(SYNC_NETWORK_KEY) ?: DEFAULT_SYNC_NETWORK

    val currentSyncIntervalHours: Long
        get() =
            SettingsStore.currentPreferences?.get(SYNC_INTERVAL_HOURS_KEY)
                ?: DEFAULT_SYNC_INTERVAL_HOURS

    val currentSyncLayout: String
        get() = SettingsStore.currentPreferences?.get(SYNC_LAYOUT_KEY) ?: DEFAULT_SYNC_LAYOUT

    val currentSyncDirectory: String
        get() = SettingsStore.currentPreferences?.get(SYNC_DIRECTORY_KEY).orEmpty()

    val currentSyncContentMode: String
        get() =
            SettingsStore.currentPreferences?.get(SYNC_CONTENT_MODE_KEY)
                ?: DEFAULT_SYNC_CONTENT_MODE

    val currentSyncSelectedPlaylists: Set<String>
        get() = SettingsStore.currentPreferences?.get(SYNC_SELECTED_PLAYLISTS_KEY) ?: emptySet()

    suspend fun setSyncUrl(value: String) =
        SettingsStore.store.edit { it[SYNC_URL_KEY] = value.trim() }

    suspend fun setSyncConnection(url: String, username: String, resetSelection: Boolean) =
        SettingsStore.store.edit {
            it[SYNC_URL_KEY] = url.trim()
            it[SYNC_USERNAME_KEY] = username.trim()
            it[SYNC_ADAPTER_KEY] = "audiostream"
            if (resetSelection) {
                it[SYNC_CONTENT_MODE_KEY] = DEFAULT_SYNC_CONTENT_MODE
                it[SYNC_SELECTED_PLAYLISTS_KEY] = emptySet()
                it[SYNC_CONTENT_CONFIGURED_KEY] = false
                it[SYNC_NETWORK_KEY] = DEFAULT_SYNC_NETWORK
                it[SYNC_DIRECTORY_KEY] = ""
                it[SYNC_LAST_TIME_KEY] = 0L
                it[SYNC_LAST_RESULT_KEY] = "Not synced yet"
            }
        }

    suspend fun setSyncUsername(value: String) =
        SettingsStore.store.edit { it[SYNC_USERNAME_KEY] = value.trim() }

    suspend fun setSyncAdapter(value: String) =
        SettingsStore.store.edit { it[SYNC_ADAPTER_KEY] = value }

    suspend fun setSyncNetwork(value: String) =
        SettingsStore.store.edit { it[SYNC_NETWORK_KEY] = value }

    suspend fun setSyncIntervalHours(value: Long) =
        SettingsStore.store.edit { it[SYNC_INTERVAL_HOURS_KEY] = value.coerceAtLeast(1L) }

    suspend fun setSyncLayout(value: String) =
        SettingsStore.store.edit { it[SYNC_LAYOUT_KEY] = value }

    suspend fun setSyncDirectory(value: String) =
        SettingsStore.store.edit { it[SYNC_DIRECTORY_KEY] = value }

    suspend fun setSyncContentMode(value: String) =
        SettingsStore.store.edit {
            it[SYNC_CONTENT_MODE_KEY] = value
            it[SYNC_CONTENT_CONFIGURED_KEY] =
                it[SYNC_CONTENT_CONFIGURED_KEY] == true || value != "none"
        }

    suspend fun setSyncSelectedPlaylists(value: Set<String>) =
        SettingsStore.store.edit {
            it[SYNC_SELECTED_PLAYLISTS_KEY] = value
            if (value.isNotEmpty()) it[SYNC_CONTENT_CONFIGURED_KEY] = true
        }

    suspend fun setSyncResult(time: Long, result: String) =
        SettingsStore.store.edit {
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

    suspend fun getSyncContentConfigured(): Boolean = syncContentConfiguredFlow.first()

    suspend fun getSyncSelectedPlaylists(): Set<String> = syncSelectedPlaylistsFlow.first()

    suspend fun migrateSyncPlaylistIds(sourceId: String) =
        SettingsStore.store.edit { prefs ->
            val legacy = prefs[SYNC_PLAYLIST_IDS_KEY]
            val key = stringPreferencesKey("SYNC_PLAYLIST_IDS_$sourceId")
            if (legacy != null && prefs[key] == null) prefs[key] = legacy
            prefs.remove(SYNC_PLAYLIST_IDS_KEY)
        }

    suspend fun getSyncPlaylistIds(sourceId: String): Map<String, Long> =
        parseLongMap(
            SettingsStore.store.data.first()[stringPreferencesKey("SYNC_PLAYLIST_IDS_$sourceId")]
        )

    suspend fun setSyncPlaylistIds(value: Map<String, Long>, sourceId: String) =
        SettingsStore.store.edit { prefs ->
            val json = org.json.JSONObject()
            value.forEach { (remoteId, localId) -> json.put(remoteId, localId) }
            prefs[stringPreferencesKey("SYNC_PLAYLIST_IDS_$sourceId")] = json.toString()
        }

    private fun parseLongMap(jsonStr: String?): Map<String, Long> {
        if (jsonStr.isNullOrBlank()) return emptyMap()
        return runCatching {
                val json = org.json.JSONObject(jsonStr)
                json.keys().asSequence().associateWith(json::getLong)
            }
            .getOrDefault(emptyMap())
    }
}
