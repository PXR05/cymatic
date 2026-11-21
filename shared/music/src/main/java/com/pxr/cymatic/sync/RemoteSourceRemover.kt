package com.pxr.cymatic.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.media.CymaticDatabase
import com.pxr.cymatic.data.media.LibraryChanges
import com.pxr.cymatic.data.media.PlaylistRepository
import com.pxr.cymatic.data.store.LibrarySyncSettings
import com.pxr.cymatic.data.store.RemoteSourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

object RemoteSourceRemover {
    private const val MANIFEST = ".cymatic-sync.json"

    suspend fun remove(context: Context, sourceId: String, deleteDownloads: Boolean) =
        withContext(Dispatchers.IO) {
            val application = context.applicationContext
            RemoteSourceRegistry.update(sourceId) { it.copy(enabled = false) }
            clearDataInternal(
                application,
                sourceId,
                deleteDownloads,
                deleteCredentialsAndRegistry = true
            )
        }

    suspend fun clearData(context: Context, sourceId: String, deleteDownloads: Boolean = true) =
        withContext(Dispatchers.IO) {
            clearDataInternal(
                context.applicationContext,
                sourceId,
                deleteDownloads,
                deleteCredentialsAndRegistry = false,
            )
        }

    private suspend fun clearDataInternal(
        context: Context,
        sourceId: String,
        deleteDownloads: Boolean,
        deleteCredentialsAndRegistry: Boolean,
    ) {
        if (LibrarySyncManager.isRunning && LibrarySyncManager.activeSourceId == sourceId) {
            LibrarySyncManager.cancel()
        }
        if (LibraryCatalogManager.isRunning && LibraryCatalogManager.activeSourceId == sourceId) {
            LibraryCatalogManager.cancel()
        }
        var waited = 0L
        while (
            waited < 5_000L &&
            ((LibrarySyncManager.isRunning && LibrarySyncManager.activeSourceId == sourceId) ||
                    (LibraryCatalogManager.isRunning && LibraryCatalogManager.activeSourceId == sourceId))
        ) {
            delay(100L)
            waited += 100L
        }

        val config = RemoteSourceRegistry.get(sourceId, context)
        val database = CymaticDatabase.getInstance(context)
        val audioDao = database.audioDao()
        val playlistDao = database.playlistDao()
        val audioIds = audioDao.getRemoteAudio(sourceId).map { it.id }

        database.withTransaction {
            playlistDao.deleteRemotePlaylists(sourceId)
            audioDao.deleteRemoteAudio(sourceId)
        }

        // Managed downloads are private to the app and always removed.
        runCatching {
            File(context.filesDir, "library-downloads/$sourceId").deleteRecursively()
        }.onFailure { Log.w("RemoteSourceRemover", "Could not delete managed downloads", it) }

        val directory = LibrarySyncSettings.getSyncDirectory()
        if (config != null) {
            val folderName = config.folderName.orEmpty()
            val manifestStore =
                SyncManifestStore.forSource(context, config.toSource(), directory, folderName)
            if (deleteDownloads && directory.isNotBlank()) {
                runCatching {
                    val root = SafTree(context.contentResolver, Uri.parse(directory))
                    val storage: SyncStorage =
                        if (folderName.isEmpty()) root else PrefixedSyncStorage(root, folderName)
                    val entries =
                        manifestStore.read()
                            ?: storage.readText(MANIFEST)?.let { SyncManifestStore.decode(it) }
                            ?: emptyMap()
                    entries.keys.forEach { path -> runCatching { storage.delete(path) } }
                    runCatching { storage.delete(MANIFEST) }
                    if (folderName.isNotEmpty()) {
                        runCatching { root.delete(folderName) }
                    }
                }.onFailure { Log.w("RemoteSourceRemover", "Could not delete SAF downloads", it) }
            }
            manifestStore.delete()
        }

        SyncCatalogStore.delete(context, sourceId)

        val artworkDir = File(context.filesDir, "library-artwork")
        if (artworkDir.isDirectory && audioIds.isNotEmpty()) {
            val prefixes = audioIds.mapTo(mutableSetOf()) { "$it-" }
            artworkDir.listFiles().orEmpty().forEach { file ->
                if (prefixes.any { file.name.startsWith(it) }) {
                    file.delete()
                }
            }
        }

        OfflineSelectionStore.clear(context, sourceId)
        LibrarySyncSettings.clearSyncPlaylistIds(sourceId)

        if (deleteCredentialsAndRegistry) {
            SyncCredentialStore.delete(context, sourceId)
            RemoteLibraries.invalidate(sourceId)
            RemoteSourceRegistry.remove(sourceId)
        } else {
            RemoteLibraries.invalidate(sourceId)
        }

        AudioRepository.getInstance(context).invalidate()
        PlaylistRepository.getInstance(context).invalidate()
        LibraryChanges.notifyChanged()
        LibrarySyncJobService.reschedule(context)
    }
}
