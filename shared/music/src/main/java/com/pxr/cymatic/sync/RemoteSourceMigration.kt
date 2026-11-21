package com.pxr.cymatic.sync

import android.content.Context
import android.util.Log
import com.pxr.cymatic.data.media.CymaticDatabase
import com.pxr.cymatic.data.store.LibrarySyncSettings
import com.pxr.cymatic.data.store.RemoteSourceRegistry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.net.URI

internal data class LegacyMigrationSourceInput(
    val provider: String,
    val address: String,
    val account: String,
    val hasCredentials: Boolean,
    val hasCatalogFile: Boolean,
    val hasRoomAudio: Boolean,
    val contentMode: String,
    val contentConfigured: Boolean,
    val selectedPlaylists: Set<String>,
    val lastSyncTime: Long,
    val lastSyncResult: String,
)

internal data class LegacyCatalogDescriptor(
    val sourceId: String,
    val provider: String,
    val address: String,
    val account: String,
    val lastModified: Long,
)

internal object RemoteSourceMigrationPlanner {
    fun plan(
        primary: LegacyMigrationSourceInput?,
        otherCatalogs: List<LegacyCatalogDescriptor>,
        now: Long = System.currentTimeMillis(),
    ): List<RemoteSourceConfig> {
        val result = mutableListOf<RemoteSourceConfig>()
        var primaryId: String? = null
        if (primary != null && primary.address.isNotBlank() && primary.account.isNotBlank()) {
            val id = runCatching {
                RemoteLibrarySource.legacyId(primary.provider, primary.address, primary.account)
            }.getOrNull()
            if (id != null && (primary.hasCredentials || primary.hasCatalogFile || primary.hasRoomAudio)) {
                primaryId = id
                val normalized = runCatching {
                    AudioStreamClient.normalizeBaseUrl(primary.address)
                }.getOrDefault(primary.address.trim())
                result += RemoteSourceConfig(
                    id = id,
                    provider = primary.provider,
                    displayName = hostDisplayName(normalized, primary.provider),
                    address = normalized,
                    account = primary.account.trim(),
                    enabled = true,
                    folderName = "",
                    contentMode = primary.contentMode,
                    contentConfigured = primary.contentConfigured,
                    selectedPlaylists = primary.selectedPlaylists,
                    lastSyncTime = primary.lastSyncTime,
                    lastSyncResult = primary.lastSyncResult,
                    createdAt = now,
                )
            }
        }
        otherCatalogs
            .filter { it.sourceId != primaryId && it.address.isNotBlank() && it.provider.isNotBlank() }
            .sortedBy { it.lastModified }
            .forEachIndexed { index, catalog ->
                val normalized = runCatching {
                    AudioStreamClient.normalizeBaseUrl(catalog.address)
                }.getOrDefault(catalog.address.trim())
                result += RemoteSourceConfig(
                    id = catalog.sourceId,
                    provider = catalog.provider,
                    displayName = hostDisplayName(normalized, catalog.provider),
                    address = normalized,
                    account = catalog.account.trim(),
                    enabled = true,
                    folderName = null,
                    createdAt = now + index + 1,
                )
            }
        return result
    }

    internal fun hostDisplayName(address: String, provider: String): String {
        val host = runCatching { URI(address).host }.getOrNull()?.takeIf(String::isNotBlank)
        return host ?: RemoteProvider.from(provider)?.label ?: provider
    }
}

object RemoteSourceMigration {
    private val mutex = Mutex()

    suspend fun migrate(context: Context) {
        if (RemoteSourceRegistry.isMigrated()) return
        mutex.withLock {
            if (RemoteSourceRegistry.isMigrated()) return
            val application = context.applicationContext
            val adapter = LibrarySyncSettings.getSyncAdapter()
            val url = LibrarySyncSettings.getSyncUrl()
            val username = LibrarySyncSettings.getSyncUsername()

            var primaryInput: LegacyMigrationSourceInput? = null
            if (url.isNotBlank() && username.isNotBlank()) {
                val legacyId = runCatching {
                    RemoteLibrarySource.legacyId(adapter, url, username)
                }.getOrNull()
                if (legacyId != null) {
                    val tempSource = RemoteLibrarySource(
                        id = legacyId,
                        provider = adapter,
                        address = url,
                        account = username,
                    )
                    runCatching { SyncCredentialStore.migrateLegacy(application, legacyId) }
                        .onFailure {
                            Log.w(
                                "RemoteSourceMigration",
                                "Legacy credential migration failed",
                                it
                            )
                        }
                    runCatching { SyncCatalogStore.migrateLegacy(application, tempSource) }
                        .onFailure {
                            Log.w(
                                "RemoteSourceMigration",
                                "Legacy catalog migration failed",
                                it
                            )
                        }
                    val hasCreds = SyncCredentialStore.has(application, legacyId)
                    val catalogFile =
                        File(File(application.filesDir, "library-catalogs"), "$legacyId.json")
                    val hasRoomAudio = runCatching {
                        CymaticDatabase.getInstance(application).audioDao().getRemoteAudio(legacyId)
                            .isNotEmpty()
                    }.getOrDefault(false)
                    primaryInput = LegacyMigrationSourceInput(
                        provider = adapter,
                        address = url,
                        account = username,
                        hasCredentials = hasCreds,
                        hasCatalogFile = catalogFile.isFile,
                        hasRoomAudio = hasRoomAudio,
                        contentMode = LibrarySyncSettings.getSyncContentMode(),
                        contentConfigured = LibrarySyncSettings.getSyncContentConfigured(),
                        selectedPlaylists = LibrarySyncSettings.getSyncSelectedPlaylists(),
                        lastSyncTime = LibrarySyncSettings.currentSyncLastTime,
                        lastSyncResult = LibrarySyncSettings.currentSyncLastResult,
                    )
                }
            }

            val otherCatalogs = File(application.filesDir, "library-catalogs")
                .listFiles()
                .orEmpty()
                .filter {
                    it.isFile && it.extension == "json" && it.nameWithoutExtension.matches(
                        Regex("[a-f0-9]{64}")
                    )
                }
                .mapNotNull { file ->
                    runCatching {
                        val root = JSONObject(file.readText())
                        val provider = root.optString("provider")
                        val address = root.optString("address")
                        val account = root.optString("account")
                        if (provider.isBlank() || address.isBlank()) return@runCatching null
                        LegacyCatalogDescriptor(
                            sourceId = file.nameWithoutExtension,
                            provider = provider,
                            address = address,
                            account = account,
                            lastModified = file.lastModified(),
                        )
                    }.getOrNull()
                }

            val planned = RemoteSourceMigrationPlanner.plan(primaryInput, otherCatalogs)
            if (planned.isNotEmpty()) {
                RemoteSourceRegistry.replaceAllRaw(planned)
            } else {
                RemoteSourceRegistry.markMigrated()
            }
        }
    }
}
