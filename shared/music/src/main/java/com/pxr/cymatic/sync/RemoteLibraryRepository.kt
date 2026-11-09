package com.pxr.cymatic.sync

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.pxr.cymatic.data.media.AudioEntity
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.media.CymaticDatabase
import com.pxr.cymatic.data.media.LibraryChanges
import com.pxr.cymatic.data.media.PlaylistEntity
import com.pxr.cymatic.data.media.PlaylistRepository
import com.pxr.cymatic.data.store.SettingsStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

internal object RemoteLibraryRepository {
    private val mutex = Mutex()

    suspend fun restore(context: Context) {
        val current = RemoteLibraries.currentSource()
        SyncCredentialStore.migrateLegacy(context, current.id)
        SyncCatalogStore.migrateLegacy(context, current)
        val currentCatalog = SyncCatalogStore.read(context, current)
        val refreshMetadata = SyncCatalogStore.needsMetadataRefresh(context, current)
        if (currentCatalog != null && SyncCatalogStore.source(context, current.id) == null)
            SyncCatalogStore.write(context, current, currentCatalog, metadataVersion = 1)
        File(context.filesDir, "library-catalogs").listFiles().orEmpty()
            .filter { it.extension == "json" }.forEach { file ->
            val source =
                SyncCatalogStore.source(context, file.nameWithoutExtension) ?: return@forEach
            val catalog = SyncCatalogStore.read(context, source) ?: return@forEach
            publish(context, source, catalog)
        }
        if (refreshMetadata && SyncCredentialStore.hasPassword(context, current.id))
            LibraryCatalogManager.refresh(context)
    }

    fun uri(key: RemoteLibraryItemKey): Uri = Uri.Builder().scheme("cymatic")
        .authority(key.sourceId).appendPath(key.remoteId).build()

    fun isReadable(context: Context, value: String): Boolean = runCatching {
        val uri = Uri.parse(value)
        if (uri.scheme == "file") File(requireNotNull(uri.path)).isFile
        else context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    suspend fun publish(context: Context, source: RemoteLibrarySource, catalog: RemoteCatalog) =
        mutex.withLock {
            val database = CymaticDatabase.getInstance(context)
            val audioDao = database.audioDao()
            val playlistDao = database.playlistDao()
            val old = audioDao.getRemoteAudio(source.id).associateBy { it.remoteId }
            val offline = mutableMapOf<String, String>()
            val managed =
                ManagedSyncStorage(File(context.filesDir, "library-downloads/${source.id}"))
            collectOffline(managed, source.id, catalog, offline)
            val current = RemoteLibraries.currentSource()
            val directory = SettingsStore.getSyncDirectory()
            if (source.id == current.id && directory.isNotBlank()) {
                runCatching {
                    val mediaUris = mutableMapOf<String, String>()
                    val collection = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    runCatching {
                        context.contentResolver.query(
                            collection,
                            arrayOf("_id", "_data"),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            while (cursor.moveToNext()) cursor.getString(1)?.let { path ->
                                mediaUris[path] = android.content.ContentUris.withAppendedId(
                                    collection,
                                    cursor.getLong(0)
                                ).toString()
                            }
                        }
                    }
                    collectOffline(
                        SafTree(context.contentResolver, Uri.parse(directory)),
                        source.id,
                        catalog,
                        offline,
                        mediaUris
                    )
                }
            }
            val records = catalog.tracks.map { track ->
                val key = source.itemKey(track.id)
                val previous = old[track.id]
                val version = track.updatedAt.hashCode().toLong()
                val local = offline[track.id] ?: previous?.offlineUri?.takeIf {
                    previous.dateModified == version && isReadable(context, it)
                }
                AudioEntity(
                    id = key.audioId(),
                    uri = uri(key).toString(),
                    size = track.size.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    duration = track.durationMs ?: previous?.duration,
                    bitRate = track.bitRate ?: previous?.bitRate,
                    sampleRate = track.sampleRate ?: previous?.sampleRate,
                    format = remoteMimeType(track.filename),
                    artworkUri = LibraryArtworkProvider.uri(context, key, version).toString(),
                    dateModified = version,
                    sourceId = source.id,
                    remoteId = track.id,
                    offlineUri = local,
                )
            }
            SettingsStore.migrateSyncPlaylistIds(source.id)
            val mappings = SettingsStore.getSyncPlaylistIds(source.id).toMutableMap()
            database.withTransaction {
                audioDao.upsertAudio(records)
                val byUri =
                    audioDao.getAllAudio().filter { it.sourceId == null }.associateBy { it.uri }
                records.forEach { record ->
                    record.offlineUri?.let(byUri::get)?.let { legacy ->
                        playlistDao.remapAudio(legacy.id, record.id)
                    }
                }
                val keepIds = records.mapTo(mutableSetOf()) { it.id }
                audioDao.deleteByIds(old.values.filter { it.id !in keepIds && it.offlineUri == null }
                    .map { it.id })
                val usedNames =
                    playlistDao.getPlaylists().associateBy { it.name.lowercase() }.toMutableMap()
                catalog.playlists.forEach { playlist ->
                    val existing = playlistDao.getRemotePlaylist(source.id, playlist.id)
                        ?: mappings[playlist.id]?.let { playlistDao.getPlaylist(it) }
                    var name = playlist.name.ifBlank { "Unnamed playlist" }
                    val base = name
                    var suffix = 1
                    while (usedNames[name.lowercase()]?.id?.let { it != existing?.id } == true) {
                        name =
                            "$base (${Uri.parse(source.address).host ?: source.provider}${if (suffix > 1) " $suffix" else ""})"
                        suffix++
                    }
                    val entity = PlaylistEntity(
                        id = existing?.id ?: 0L,
                        name = name,
                        createdAt = existing?.createdAt ?: catalog.refreshedAt,
                        updatedAt = catalog.refreshedAt,
                        sourceId = source.id,
                        remoteId = playlist.id,
                    )
                    val id = if (existing == null) playlistDao.insertPlaylist(entity) else {
                        playlistDao.updatePlaylist(entity)
                        existing.id
                    }
                    mappings[playlist.id] = id
                    usedNames[name.lowercase()] = entity.copy(id = id)
                    val ids = playlist.trackIds.map { source.itemKey(it).audioId() }
                        .filter { it in keepIds }.distinct()
                    playlistDao.replacePlaylistItems(id, ids, catalog.refreshedAt)
                }
                val remoteIds = catalog.playlists.mapTo(mutableSetOf()) { it.id }
                playlistDao.getPlaylists()
                    .filter { it.sourceId == source.id && it.remoteId !in remoteIds }
                    .forEach { playlistDao.deletePlaylist(it.id) }
            }
            SettingsStore.setSyncPlaylistIds(
                mappings.filterKeys { id -> catalog.playlists.any { it.id == id } },
                source.id
            )
            AudioRepository.getInstance(context).invalidate()
            PlaylistRepository.getInstance(context).invalidate()
            LibraryChanges.notifyChanged()
        }

    private fun collectOffline(
        storage: SyncStorage, sourceId: String, catalog: RemoteCatalog,
        results: MutableMap<String, String>, mediaUris: Map<String, String> = emptyMap()
    ) {
        val value = storage.readText(".cymatic-sync.json") ?: return
        val owner = org.json.JSONObject(value).optString("sourceId")
        if (owner.isNotBlank() && owner != sourceId) return
        val tracks = catalog.tracks.associateBy { it.id }
        SyncManifestStore.decode(value).forEach { (path, entry) ->
            val track = tracks[entry.remoteId] ?: return@forEach
            if (!path.substringAfterLast('/')
                    .startsWith(".cymatic-sync-") && entry.matches(track) &&
                storage.size(path) == entry.localSize
            ) {
                val mediaUri = storage.absolutePath(path)?.let(mediaUris::get)
                (mediaUri ?: storage.documentUri(path)?.toString())?.let { results[track.id] = it }
            }
        }
    }
}
