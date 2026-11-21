package com.pxr.cymatic.data.store

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pxr.cymatic.sync.RemoteLibraries
import com.pxr.cymatic.sync.RemoteSourceConfig
import com.pxr.cymatic.sync.RemoteSourceMigration
import com.pxr.cymatic.sync.SyncContentMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal object RemoteSourceRegistryCodec {
    private const val CURRENT_VERSION = 1

    fun encode(sources: List<RemoteSourceConfig>): String {
        val array = JSONArray()
        sources.forEach { config ->
            val obj = JSONObject()
                .put("id", config.id)
                .put("provider", config.provider)
                .put("displayName", config.displayName)
                .put("address", config.address)
                .put("account", config.account)
                .put("enabled", config.enabled)
                .put("contentMode", config.contentMode)
                .put("contentConfigured", config.contentConfigured)
                .put("selectedPlaylists", JSONArray(config.selectedPlaylists.sorted()))
                .put("transcodeOnMetered", config.transcodeOnMetered)
                .put("transcodeBitrateKbps", config.transcodeBitrateKbps)
                .put("lastSyncTime", config.lastSyncTime)
                .put("lastSyncResult", config.lastSyncResult)
                .put("createdAt", config.createdAt)
            config.folderName?.let { obj.put("folderName", it) }
            config.lastError?.let { obj.put("lastError", it) }
            if (config.extras.isNotEmpty()) {
                val extrasJson = JSONObject()
                config.extras.forEach { (k, v) -> extrasJson.put(k, v) }
                obj.put("extras", extrasJson)
            }
            array.put(obj)
        }
        return JSONObject()
            .put("v", CURRENT_VERSION)
            .put("sources", array)
            .toString()
    }

    fun decode(raw: String?): List<RemoteSourceConfig> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            val array = root.optJSONArray("sources") ?: return emptyList()
            buildList {
                for (index in 0 until array.length()) {
                    runCatching {
                        val obj = array.getJSONObject(index)
                        val id = obj.optString("id")
                        val provider = obj.optString("provider")
                        val address = obj.optString("address")
                        if (id.isBlank() || provider.isBlank() || address.isBlank()) {
                            return@runCatching
                        }
                        val playlistsArray = obj.optJSONArray("selectedPlaylists")
                        val playlists = buildSet {
                            if (playlistsArray != null) {
                                for (i in 0 until playlistsArray.length()) {
                                    playlistsArray.optString(i).takeIf(String::isNotBlank)
                                        ?.let(::add)
                                }
                            }
                        }
                        val extrasObj = obj.optJSONObject("extras")
                        val extras = buildMap {
                            if (extrasObj != null) {
                                val keys = extrasObj.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    put(key, extrasObj.optString(key))
                                }
                            }
                        }
                        val folderName =
                            if (obj.has("folderName") && !obj.isNull("folderName"))
                                obj.optString("folderName")
                            else null
                        val lastError =
                            if (obj.has("lastError") && !obj.isNull("lastError"))
                                obj.optString("lastError").takeIf(String::isNotBlank)
                            else null
                        add(
                            RemoteSourceConfig(
                                id = id,
                                provider = provider,
                                displayName = obj.optString("displayName").ifBlank { provider },
                                address = address,
                                account = obj.optString("account"),
                                enabled = obj.optBoolean("enabled", true),
                                folderName = folderName,
                                contentMode = obj.optString(
                                    "contentMode",
                                    SyncContentMode.NONE.value
                                )
                                    .ifBlank { SyncContentMode.NONE.value },
                                contentConfigured = obj.optBoolean("contentConfigured", false),
                                selectedPlaylists = playlists,
                                transcodeOnMetered = obj.optBoolean("transcodeOnMetered", false),
                                transcodeBitrateKbps = obj.optInt("transcodeBitrateKbps", 192),
                                lastSyncTime = obj.optLong("lastSyncTime", 0L),
                                lastSyncResult = obj.optString("lastSyncResult", "Not synced yet")
                                    .ifBlank { "Not synced yet" },
                                lastError = lastError,
                                extras = extras,
                                createdAt = obj.optLong("createdAt", 0L),
                            )
                        )
                    }.onFailure { error ->
                        Log.w(
                            "RemoteSourceRegistry",
                            "Skipping malformed remote source entry",
                            error
                        )
                    }
                }
            }.sortedBy { it.createdAt }
        }.onFailure { error ->
            Log.w("RemoteSourceRegistry", "Could not decode remote source registry", error)
        }.getOrDefault(emptyList())
    }
}

object RemoteSourceRegistry {
    private val REMOTE_SOURCES_KEY = stringPreferencesKey("REMOTE_SOURCES")
    private val REMOTE_SOURCES_MIGRATED_KEY = booleanPreferencesKey("REMOTE_SOURCES_MIGRATED")
    private val REMOTE_DEVICE_ID_KEY = stringPreferencesKey("REMOTE_DEVICE_ID")

    val sourcesFlow: Flow<List<RemoteSourceConfig>>
        get() =
            SettingsStore.store.data
                .map { prefs -> RemoteSourceRegistryCodec.decode(prefs[REMOTE_SOURCES_KEY]) }
                .distinctUntilChanged()

    val currentSources: List<RemoteSourceConfig>
        get() = RemoteSourceRegistryCodec.decode(
            SettingsStore.currentPreferences?.get(REMOTE_SOURCES_KEY)
        )

    fun cached(id: String): RemoteSourceConfig? = currentSources.firstOrNull { it.id == id }

    suspend fun isMigrated(): Boolean =
        SettingsStore.store.data.first()[REMOTE_SOURCES_MIGRATED_KEY] == true

    internal suspend fun markMigrated() {
        SettingsStore.store.edit { it[REMOTE_SOURCES_MIGRATED_KEY] = true }
    }

    internal suspend fun replaceAllRaw(sources: List<RemoteSourceConfig>) {
        SettingsStore.store.edit {
            it[REMOTE_SOURCES_KEY] =
                RemoteSourceRegistryCodec.encode(sources.sortedBy { s -> s.createdAt })
            it[REMOTE_SOURCES_MIGRATED_KEY] = true
        }
    }

    suspend fun all(context: Context? = null): List<RemoteSourceConfig> {
        if (context != null && !isMigrated()) {
            RemoteSourceMigration.migrate(context)
        }
        return sourcesFlow.first()
    }

    suspend fun get(id: String, context: Context? = null): RemoteSourceConfig? =
        all(context).firstOrNull { it.id == id }

    suspend fun add(config: RemoteSourceConfig) {
        SettingsStore.store.edit { prefs ->
            val current = RemoteSourceRegistryCodec.decode(prefs[REMOTE_SOURCES_KEY])
            val updated = current.filterNot { it.id == config.id } + config
            prefs[REMOTE_SOURCES_KEY] =
                RemoteSourceRegistryCodec.encode(updated.sortedBy { it.createdAt })
            prefs[REMOTE_SOURCES_MIGRATED_KEY] = true
        }
    }

    suspend fun update(
        id: String,
        invalidateClient: Boolean = false,
        transform: (RemoteSourceConfig) -> RemoteSourceConfig,
    ): RemoteSourceConfig? {
        var result: RemoteSourceConfig? = null
        SettingsStore.store.edit { prefs ->
            val current = RemoteSourceRegistryCodec.decode(prefs[REMOTE_SOURCES_KEY])
            val updated = current.map { item ->
                if (item.id == id) transform(item).also { result = it } else item
            }
            if (result != null) {
                prefs[REMOTE_SOURCES_KEY] =
                    RemoteSourceRegistryCodec.encode(updated.sortedBy { it.createdAt })
            }
        }
        if (invalidateClient && result != null) {
            RemoteLibraries.invalidate(id)
        }
        return result
    }

    suspend fun remove(id: String) {
        SettingsStore.store.edit { prefs ->
            val current = RemoteSourceRegistryCodec.decode(prefs[REMOTE_SOURCES_KEY])
            val updated = current.filterNot { it.id == id }
            prefs[REMOTE_SOURCES_KEY] = RemoteSourceRegistryCodec.encode(updated)
        }
        RemoteLibraries.invalidate(id)
    }

    suspend fun setResult(
        id: String,
        time: Long,
        message: String,
        lastError: String? = null,
    ) {
        update(id) { config ->
            config.copy(
                lastSyncTime = time,
                lastSyncResult = message,
                lastError = lastError,
            )
        }
    }

    suspend fun deviceId(): String {
        val existing = SettingsStore.store.data.first()[REMOTE_DEVICE_ID_KEY]
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        SettingsStore.store.edit { prefs ->
            if (prefs[REMOTE_DEVICE_ID_KEY].isNullOrBlank()) {
                prefs[REMOTE_DEVICE_ID_KEY] = generated
            }
        }
        return SettingsStore.store.data.first()[REMOTE_DEVICE_ID_KEY] ?: generated
    }
}
