package com.pxr.cymatic.sync

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.media.PlaylistRepository
import com.pxr.cymatic.data.media.syncAudioFilesToDb
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.store.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

data class SyncSummary(
    val downloaded: Int,
    val unchanged: Int,
    val removed: Int,
    val failed: Int,
    val playlists: Int = 0,
)

sealed interface SyncProgress {
    data object Idle : SyncProgress
    data class Preparing(val message: String) : SyncProgress
    data class Downloading(
        val current: Int,
        val total: Int,
        val title: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : SyncProgress
    data object Cancelling : SyncProgress
    data class Complete(val summary: SyncSummary) : SyncProgress
    data class Failed(val message: String) : SyncProgress
    data object Cancelled : SyncProgress
}

class SyncCancelledException : IOException("Sync cancelled")

object LibrarySyncManager {
    private const val MANIFEST = ".cymatic-sync.json"
    private val running = AtomicBoolean(false)
    private val cancelRequested = AtomicBoolean(false)
    private val activeClient = AtomicReference<AudioStreamClient?>(null)
    private val _progress = MutableStateFlow<SyncProgress>(SyncProgress.Idle)
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()
    private val manualScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context): Boolean {
        if (running.get()) return false
        manualScope.launch { runCatching { sync(context.applicationContext, metadataOnly = false) } }
        return true
    }

    fun refreshMetadata(context: Context): Boolean {
        if (running.get()) return false
        manualScope.launch { runCatching { sync(context.applicationContext, metadataOnly = true) } }
        return true
    }

    fun cancel() {
        if (!running.get()) return
        cancelRequested.set(true)
        _progress.value = SyncProgress.Cancelling
        activeClient.get()?.cancel()
    }

    suspend fun sync(context: Context, metadataOnly: Boolean = false): SyncSummary = withContext(Dispatchers.IO) {
        check(running.compareAndSet(false, true)) { "A library sync is already running" }
        cancelRequested.set(false)
        _progress.value = SyncProgress.Preparing("Connecting to AudioStream")
        try {
            syncBlocking(context.applicationContext, metadataOnly).also {
                _progress.value = SyncProgress.Complete(it)
            }
        } catch (_: SyncCancelledException) {
            _progress.value = SyncProgress.Cancelled
            SettingsStore.setSyncResult(System.currentTimeMillis(), "Sync stopped")
            throw SyncCancelledException()
        } catch (error: Throwable) {
            if (cancelRequested.get()) {
                _progress.value = SyncProgress.Cancelled
                SettingsStore.setSyncResult(System.currentTimeMillis(), "Sync stopped")
                throw SyncCancelledException()
            }
            _progress.value = SyncProgress.Failed(error.message ?: "Sync failed")
            SettingsStore.setSyncResult(
                System.currentTimeMillis(),
                "Sync failed: ${error.message ?: "unknown error"}",
            )
            throw error
        } finally {
            activeClient.set(null)
            running.set(false)
        }
    }

    private suspend fun syncBlocking(context: Context, metadataOnly: Boolean): SyncSummary {
        val password = SyncCredentialStore.getPassword(context)
            ?: throw IllegalStateException("Enter the AudioStream password first")
        require(SettingsStore.getSyncAdapter() == "audiostream") { "Unsupported sync adapter" }

        val client = AudioStreamClient(
            baseUrl = SettingsStore.getSyncUrl(),
            username = SettingsStore.getSyncUsername(),
            password = password,
        )
        activeClient.set(client)
        checkCancelled()
        val tracks = client.getTracks()
        val remotePlaylists = catalogPlaylists(tracks, client.getPlaylists())
        val catalog = RemoteCatalog(tracks, remotePlaylists, System.currentTimeMillis())
        SyncCatalogStore.write(context, catalog)
        if (metadataOnly) {
            val summary = SyncSummary(0, 0, 0, 0, remotePlaylists.size)
            SettingsStore.setSyncResult(
                System.currentTimeMillis(),
                "Playlist list updated: ${remotePlaylists.size} playlists",
            )
            return summary
        }

        val mode = SyncContentMode.from(SettingsStore.getSyncContentMode())
        val selectedIds = SettingsStore.getSyncSelectedPlaylists()
        val playlistsToSync = when (mode) {
            SyncContentMode.ALL -> remotePlaylists
            SyncContentMode.SELECTED_PLAYLISTS -> remotePlaylists.filter { it.id in selectedIds }
        }
        val tracksToSync = when (mode) {
            SyncContentMode.ALL -> tracks
            SyncContentMode.SELECTED_PLAYLISTS -> {
                val trackIds = playlistsToSync.flatMapTo(mutableSetOf()) { it.trackIds }
                tracks.filter { it.id in trackIds }
            }
        }
        if (mode == SyncContentMode.SELECTED_PLAYLISTS && playlistsToSync.isEmpty()) {
            val summary = SyncSummary(0, 0, 0, 0, 0)
            SettingsStore.setSyncResult(
                System.currentTimeMillis(),
                "Playlist metadata updated; choose playlists to download",
            )
            return summary
        }

        val directory = SettingsStore.getSyncDirectory()
        require(directory.isNotBlank()) { "Choose a local sync folder first" }
        val tree = SafTree(context.contentResolver, Uri.parse(directory))
        val layout = SyncLayout.from(SettingsStore.getSyncLayout())
        _progress.value = SyncProgress.Preparing("Planning local files")
        checkCancelled()
        val placements = if (layout == SyncLayout.PLAYLIST_TRACKS) {
            buildMap<String, MutableList<PlaylistPlacement>> {
                playlistsToSync.forEach { playlist ->
                    playlist.trackIds.forEachIndexed { position, trackId ->
                        getOrPut(trackId) { mutableListOf() } += PlaylistPlacement(playlist.name, position)
                    }
                }
            }
        } else {
            emptyMap()
        }

        val previous = readManifest(tree)
        val desired = makeUniqueTargets(tracksToSync.flatMap { track ->
            SyncPathPlanner.targets(track, layout, placements[track.id].orEmpty())
        })
        val next = mutableMapOf<String, ManifestEntry>()
        var downloaded = 0
        var unchanged = 0
        var failed = 0

        desired.forEachIndexed { index, target ->
            checkCancelled()
            _progress.value = SyncProgress.Downloading(
                current = index + 1,
                total = desired.size,
                title = target.track.title,
                bytesDownloaded = 0L,
                totalBytes = target.track.size.coerceAtLeast(0L),
            )
            val old = previous[target.relativePath]
            val existingSize = tree.size(target.relativePath)
            val manifestMatch = old?.remoteId == target.track.id &&
                old.updatedAt == target.track.updatedAt && old.size == target.track.size
            val fileMatch = target.track.size >= 0L && existingSize == target.track.size
            if ((manifestMatch || fileMatch) && existingSize != null) {
                next[target.relativePath] = ManifestEntry(
                    remoteId = target.track.id,
                    updatedAt = target.track.updatedAt,
                    size = target.track.size,
                )
                unchanged++
                return@forEachIndexed
            }

            val result = runCatching {
                val connection = client.openTrack(target.track.id)
                try {
                    tree.write(target.relativePath, mimeType(target.track.filename)) { output ->
                        connection.inputStream.buffered().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var copied = 0L
                            var lastUpdate = 0L
                            while (true) {
                                checkCancelled()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                copied += count
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate >= 150L) {
                                    _progress.value = SyncProgress.Downloading(
                                        current = index + 1,
                                        total = desired.size,
                                        title = target.track.title,
                                        bytesDownloaded = copied,
                                        totalBytes = target.track.size.coerceAtLeast(0L),
                                    )
                                    lastUpdate = now
                                }
                            }
                        }
                    }
                } finally {
                    client.release(connection)
                }
            }
            result.exceptionOrNull()?.let { if (it is SyncCancelledException || cancelRequested.get()) throw SyncCancelledException() }
            if (result.isSuccess) {
                next[target.relativePath] = ManifestEntry(
                    remoteId = target.track.id,
                    updatedAt = target.track.updatedAt,
                    size = target.track.size,
                )
                downloaded++
            } else {
                failed++
                if (old != null && tree.exists(target.relativePath)) next[target.relativePath] = old
            }
        }

        var removed = 0
        _progress.value = SyncProgress.Preparing("Finishing sync")
        checkCancelled()
        if (failed == 0) {
            (previous.keys - next.keys).forEach { path ->
                if (tree.delete(path)) removed++
            }
        } else {
            previous.filterKeys { it !in next }.forEach { (path, entry) -> next[path] = entry }
        }
        writeManifest(tree, next)

        val syncedPaths = (next.keys + previous.keys).distinct().mapNotNull(tree::absolutePath)
        scanMediaFiles(context, syncedPaths)
        val localFiles = syncAudioFilesToDb(
            context,
            SettingsStore.getScanDirectories() + directory,
            SettingsStore.getScanAllMedia(),
        )
        if (failed == 0) syncPlaylists(context, playlistsToSync, tracksToSync, localFiles)

        val summary = SyncSummary(downloaded, unchanged, removed, failed, playlistsToSync.size)
        val message = if (failed == 0) {
            "Synced: $downloaded downloaded, $unchanged unchanged, ${playlistsToSync.size} playlists"
        } else {
            "Sync incomplete: $downloaded downloaded, $failed failed"
        }
        SettingsStore.setSyncResult(System.currentTimeMillis(), message)
        if (failed > 0) throw IOException(message)
        return summary
    }

    private fun makeUniqueTargets(targets: List<SyncTarget>): List<SyncTarget> {
        val used = mutableSetOf<String>()
        return targets.map { target ->
            if (used.add(target.relativePath.lowercase())) return@map target
            val dot = target.relativePath.lastIndexOf('.')
            val suffix = " [${target.track.id.take(8)}]"
            val unique = if (dot > target.relativePath.lastIndexOf('/')) {
                target.relativePath.substring(0, dot) + suffix + target.relativePath.substring(dot)
            } else {
                target.relativePath + suffix
            }
            used += unique.lowercase()
            target.copy(relativePath = unique)
        }
    }

    private fun readManifest(tree: SafTree): Map<String, ManifestEntry> = runCatching {
        val root = JSONObject(tree.readText(MANIFEST) ?: return emptyMap())
        val entries = root.getJSONArray("files")
        buildMap {
            for (index in 0 until entries.length()) {
                val item = entries.getJSONObject(index)
                put(
                    item.getString("path"),
                    ManifestEntry(
                        remoteId = item.getString("remoteId"),
                        updatedAt = item.optString("updatedAt"),
                        size = item.optLong("size", -1L),
                    ),
                )
            }
        }
    }.getOrDefault(emptyMap())

    private fun writeManifest(tree: SafTree, entries: Map<String, ManifestEntry>) {
        val files = JSONArray()
        entries.toSortedMap().forEach { (path, entry) ->
            files.put(
                JSONObject()
                    .put("path", path)
                    .put("remoteId", entry.remoteId)
                    .put("updatedAt", entry.updatedAt)
                    .put("size", entry.size)
            )
        }
        tree.writeText(
            MANIFEST,
            JSONObject().put("version", 1).put("files", files).toString(),
        )
    }

    private fun mimeType(filename: String): String = when (filename.substringAfterLast('.').lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a", "mp4" -> "audio/mp4"
        "flac" -> "audio/flac"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        "aac" -> "audio/aac"
        else -> "application/octet-stream"
    }

    private fun checkCancelled() {
        if (cancelRequested.get()) throw SyncCancelledException()
    }

    private suspend fun scanMediaFiles(context: Context, paths: List<String>) {
        if (paths.isEmpty()) return
        suspendCancellableCoroutine { continuation ->
            var remaining = paths.size
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
                remaining--
                if (remaining == 0 && continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    private suspend fun syncPlaylists(
        context: Context,
        remotePlaylists: List<RemotePlaylist>,
        tracks: List<RemoteTrack>,
        localFiles: List<AudioFile>,
    ) {
        _progress.value = SyncProgress.Preparing("Syncing playlists")
        checkCancelled()
        val playlistRepository = PlaylistRepository.getInstance(context)
        val existing = playlistRepository.getPlaylists().associateBy { it.id }.toMutableMap()
        val mappedIds = SettingsStore.getSyncPlaylistIds().toMutableMap()
        val remoteIds = remotePlaylists.mapTo(mutableSetOf()) { it.id }

        (mappedIds.keys - remoteIds).forEach { remoteId ->
            mappedIds.remove(remoteId)?.let { localId ->
                if (existing.containsKey(localId)) playlistRepository.deletePlaylist(localId)
                existing.remove(localId)
            }
        }

        val localByRemoteId = tracks.associate { track ->
            track.id to bestLocalMatch(track, localFiles)
        }
        remotePlaylists.forEach { remote ->
            checkCancelled()
            var localId = mappedIds[remote.id]?.takeIf(existing::containsKey)
            if (localId == null) {
                val usedNames = existing.values.mapTo(mutableSetOf()) { it.name.lowercase() }
                var localName = remote.name
                var suffix = 1
                while (localName.lowercase() in usedNames) {
                    localName = if (suffix == 1) "${remote.name} (AudioStream)" else "${remote.name} (AudioStream $suffix)"
                    suffix++
                }
                localId = playlistRepository.createPlaylist(localName)
                mappedIds[remote.id] = localId
                playlistRepository.getPlaylist(localId)?.let { existing[localId] = it }
            }
            val current = existing[localId]
            if (current != null && current.name != remote.name &&
                existing.values.none { it.id != localId && it.name.equals(remote.name, ignoreCase = true) }
            ) {
                playlistRepository.renamePlaylist(localId, remote.name)
            }
            val audioIds = remote.trackIds.mapNotNull { localByRemoteId[it]?.id }.distinct()
            playlistRepository.replacePlaylistAudio(localId, audioIds)
        }
        SettingsStore.setSyncPlaylistIds(mappedIds)
    }

    private fun bestLocalMatch(track: RemoteTrack, files: List<AudioFile>): AudioFile? {
        val sizeMatches = files.filter { track.size < 0 || it.size.toLong() == track.size }
        fun String?.same(value: String) = orEmpty().trim().equals(value.trim(), ignoreCase = true)
        return sizeMatches.firstOrNull {
            it.metadata.title.same(track.title) &&
                it.metadata.artist.same(track.artist) &&
                it.metadata.album.same(track.album)
        } ?: sizeMatches.firstOrNull { it.metadata.title.same(track.title) }
    }

    private data class ManifestEntry(val remoteId: String, val updatedAt: String, val size: Long)
}
