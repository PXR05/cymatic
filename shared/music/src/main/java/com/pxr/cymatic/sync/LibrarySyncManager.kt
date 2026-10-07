package com.pxr.cymatic.sync

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import com.pxr.cymatic.data.media.PlaylistRepository
import com.pxr.cymatic.data.media.loadCachedAudioFiles
import com.pxr.cymatic.data.media.syncAudioFilesToDb
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.store.SettingsStore
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

private class SyncCheckpointException(cause: Exception) :
    IOException("Could not save sync progress: ${cause.message ?: "storage error"}", cause)

private class IncompleteSyncDownloadException(expected: Long, received: Long) :
    IOException("Expected $expected bytes, received $received")

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
        manualScope.launch {
            runCatching { sync(context.applicationContext, metadataOnly = false) }
        }
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

    suspend fun sync(context: Context, metadataOnly: Boolean = false): SyncSummary =
        withContext(Dispatchers.IO) {
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
        val password =
            SyncCredentialStore.getPassword(context)
                ?: throw IllegalStateException("Enter the AudioStream password first")
        require(SettingsStore.getSyncAdapter() == "audiostream") { "Unsupported sync adapter" }

        val client =
            AudioStreamClient(
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
        val playlistsToSync =
            when (mode) {
                SyncContentMode.ALL -> remotePlaylists
                SyncContentMode.SELECTED_PLAYLISTS ->
                    remotePlaylists.filter { it.id in selectedIds }
            }
        val tracksToSync =
            when (mode) {
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
        val placements =
            if (layout == SyncLayout.PLAYLIST_TRACKS) {
                buildMap<String, MutableList<PlaylistPlacement>> {
                    playlistsToSync.forEach { playlist ->
                        playlist.trackIds.forEachIndexed { position, trackId ->
                            getOrPut(trackId) { mutableListOf() } +=
                                PlaylistPlacement(playlist.name, position)
                        }
                    }
                }
            } else {
                emptyMap()
            }

        val manifestStore =
            SyncManifestStore(
                context,
                "${SettingsStore.getSyncUrl()}\n${SettingsStore.getSyncUsername()}\n$directory",
            )
        val previous = manifestStore.read() ?: readManifest(tree)
        manifestStore.write(previous)
        val desired =
            makeUniqueTargets(
                tracksToSync.flatMap { track ->
                    SyncPathPlanner.targets(track, layout, placements[track.id].orEmpty())
                }
            )
        val reuse =
            SyncFileReuse(
                previous = previous,
                desired = desired,
                files =
                    object : SyncLocalFiles {
                        override fun exists(path: String) = tree.exists(path)

                        override fun size(path: String) = tree.size(path)

                        override fun copy(source: String, destination: String) =
                            tree.copy(source, destination, mimeType(destination), ::checkCancelled)
                    },
                saveManifest = { entries ->
                    try {
                        manifestStore.write(entries)
                    } catch (error: Exception) {
                        throw SyncCheckpointException(error)
                    }
                },
            )
        syncPlaylists(context, playlistsToSync, tracksToSync, null)
        syncPlaylists(context, playlistsToSync, tracksToSync, loadCachedAudioFiles(context))
        var downloaded = 0
        var unchanged = 0
        var failed = 0
        var firstFailure: String? = null
        val pendingPaths = linkedMapOf<String, String>()
        val localUrisByRemoteId = mutableMapOf<String, String>()

        suspend fun indexCompletedTracks() {
            if (pendingPaths.isEmpty()) return
            _progress.value = SyncProgress.Preparing("Adding downloaded tracks to playlists")
            val paths = pendingPaths.keys.mapNotNull(tree::absolutePath)
            val scanned = scanMediaFiles(context, paths)
            pendingPaths.forEach { (path, remoteId) ->
                scanned[tree.absolutePath(path)]?.let {
                    localUrisByRemoteId[remoteId] = it.toString()
                }
            }
            val localFiles =
                syncAudioFilesToDb(
                    context,
                    SettingsStore.getScanDirectories() + directory,
                    SettingsStore.getScanAllMedia(),
                )
            syncPlaylists(
                context,
                playlistsToSync,
                tracksToSync,
                localFiles,
                checkCancellation = false,
                localUrisByRemoteId = localUrisByRemoteId,
            )
            pendingPaths.clear()
        }

        var interrupted: Throwable? = null
        try {
            desired.forEachIndexed { index, target ->
                checkCancelled()
                _progress.value =
                    SyncProgress.Preparing("Checking local files (${index + 1}/${desired.size})")
                val result = runCatching {
                    if (reuse.reuse(target)) return@runCatching false
                    reuse.prepareWrite(target)
                    downloadTrack(client, tree, target, index + 1, desired.size)
                    _progress.value = SyncProgress.Preparing("Saving ${target.track.title}")
                    true
                }
                result.exceptionOrNull()?.let {
                    if (it is SyncCheckpointException) throw it
                    reuse.discardMissing(target.relativePath)
                    if (it is SyncCancelledException || cancelRequested.get())
                        throw SyncCancelledException()
                }
                if (result.isSuccess) {
                    val transferred = result.getOrThrow()
                    if (transferred) {
                        pendingPaths[target.relativePath] = target.track.id
                        reuse.record(target)
                        downloaded++
                    } else {
                        unchanged++
                        if (previous[target.relativePath]?.matches(target.track) != true) {
                            pendingPaths[target.relativePath] = target.track.id
                        }
                    }
                    if (pendingPaths.size >= 25) indexCompletedTracks()
                } else {
                    failed++
                    val error = result.exceptionOrNull()
                    if (firstFailure == null)
                        firstFailure = "${target.track.title}: ${error?.message ?: "unknown error"}"
                    Log.e(
                        "LibrarySync",
                        "Could not sync ${target.track.id} to ${target.relativePath}",
                        error,
                    )
                }
            }
        } catch (error: Throwable) {
            interrupted = error
            throw error
        } finally {
            withContext(NonCancellable) {
                try {
                    indexCompletedTracks()
                } catch (error: Throwable) {
                    val original = interrupted
                    if (original == null) throw error
                    original.addSuppressed(error)
                    Log.e("LibrarySync", "Could not index completed sync tracks", error)
                }
            }
        }

        var removed = 0
        _progress.value = SyncProgress.Preparing("Finishing sync")
        checkCancelled()
        val entries = reuse.entries
        val next = entries.toMutableMap()
        if (failed == 0) {
            val desiredPaths = desired.mapTo(mutableSetOf()) { it.relativePath }
            (entries.keys - desiredPaths).forEach { path ->
                if (tree.delete(path)) {
                    removed++
                    next.remove(path)
                } else if (!tree.exists(path)) {
                    next.remove(path)
                }
            }
        }
        manifestStore.write(next)
        writeManifest(tree, next)

        val syncedPaths =
            (entries.keys + previous.keys)
                .distinct()
                .filterNot { it.substringAfterLast('/').startsWith(".cymatic-sync-") }
                .mapNotNull(tree::absolutePath)
        val scanned = scanMediaFiles(context, syncedPaths)
        next.forEach { (path, entry) ->
            scanned[tree.absolutePath(path)]?.let {
                localUrisByRemoteId[entry.remoteId] = it.toString()
            }
        }
        val localFiles =
            syncAudioFilesToDb(
                context,
                SettingsStore.getScanDirectories() + directory,
                SettingsStore.getScanAllMedia(),
            )
        syncPlaylists(
            context,
            playlistsToSync,
            tracksToSync,
            localFiles,
            removeMissing = failed == 0,
            localUrisByRemoteId = localUrisByRemoteId,
        )

        val summary = SyncSummary(downloaded, unchanged, removed, failed, playlistsToSync.size)
        val message =
            if (failed == 0) {
                "Synced: $downloaded downloaded, $unchanged unchanged, ${playlistsToSync.size} playlists"
            } else {
                "Sync incomplete: $downloaded downloaded, $failed failed. $firstFailure"
            }
        SettingsStore.setSyncResult(System.currentTimeMillis(), message)
        if (failed > 0) throw IOException(message)
        return summary
    }

    private suspend fun downloadTrack(
        client: AudioStreamClient,
        tree: SafTree,
        target: SyncTarget,
        current: Int,
        total: Int,
    ) {
        repeat(3) { attempt ->
            checkCancelled()
            _progress.value =
                SyncProgress.Downloading(
                    current,
                    total,
                    target.track.title,
                    0L,
                    target.track.size.coerceAtLeast(0L),
                )
            try {
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
                                    _progress.value =
                                        SyncProgress.Downloading(
                                            current,
                                            total,
                                            target.track.title,
                                            copied,
                                            target.track.size.coerceAtLeast(0L),
                                        )
                                    lastUpdate = now
                                }
                            }
                            if (target.track.size >= 0 && copied < target.track.size) {
                                throw IncompleteSyncDownloadException(target.track.size, copied)
                            }
                            if (target.track.size >= 0 && copied > target.track.size) {
                                throw IOException(
                                    "Expected ${target.track.size} bytes, received $copied"
                                )
                            }
                        }
                    }
                } finally {
                    client.release(connection)
                }
                return
            } catch (error: IOException) {
                checkCancelled()
                val retryable =
                    when (error) {
                        is AudioStreamRequestException ->
                            error.status == 408 || error.status == 429 || error.status >= 500
                        is SocketException,
                        is SocketTimeoutException,
                        is IncompleteSyncDownloadException -> true
                        else -> false
                    }
                if (!retryable || attempt == 2) throw error
                _progress.value = SyncProgress.Preparing("Retrying ${target.track.title}")
                Log.w(
                    "LibrarySync",
                    "Retrying ${target.track.id} after transfer attempt ${attempt + 1}",
                    error,
                )
                val retryAfter = (error as? AudioStreamRequestException)?.retryAfterMillis ?: 0L
                delay(maxOf(1000L shl attempt, retryAfter))
            }
        }
    }

    private fun makeUniqueTargets(targets: List<SyncTarget>): List<SyncTarget> {
        val used = mutableSetOf<String>()
        return targets.map { target ->
            if (used.add(target.relativePath.lowercase())) return@map target
            val dot = target.relativePath.lastIndexOf('.')
            val suffix = " [${target.track.id.take(8)}]"
            val unique =
                if (dot > target.relativePath.lastIndexOf('/')) {
                    target.relativePath.substring(0, dot) +
                        suffix +
                        target.relativePath.substring(dot)
                } else {
                    target.relativePath + suffix
                }
            used += unique.lowercase()
            target.copy(relativePath = unique)
        }
    }

    private fun readManifest(tree: SafTree): Map<String, SyncManifestEntry> = runCatching {
        SyncManifestStore.decode(tree.readText(MANIFEST) ?: return emptyMap())
    }
        .getOrDefault(emptyMap())

    private fun writeManifest(tree: SafTree, entries: Map<String, SyncManifestEntry>) {
        tree.writeText(MANIFEST, SyncManifestStore.encode(entries))
    }

    private fun mimeType(filename: String): String =
        when (filename.substringAfterLast('.').lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a",
            "mp4" -> "audio/mp4"
            "flac" -> "audio/flac"
            "dsf" -> "audio/dsd"
            "ogg",
            "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            else -> "application/octet-stream"
        }

    private fun checkCancelled() {
        if (cancelRequested.get()) throw SyncCancelledException()
    }

    private suspend fun scanMediaFiles(context: Context, paths: List<String>): Map<String, Uri> {
        val results = mutableMapOf<String, Uri>()
        for (batch in paths.distinct().chunked(25)) {
            val scanned =
                withTimeoutOrNull(60_000L) {
                    suspendCancellableCoroutine<Map<String, Uri>> { continuation ->
                        val scanned = ConcurrentHashMap<String, Uri>()
                        val remaining = AtomicInteger(batch.size)
                        MediaScannerConnection.scanFile(context, batch.toTypedArray(), null) {
                            path,
                            uri ->
                            if (uri != null) scanned[path] = uri
                            if (remaining.decrementAndGet() == 0 && continuation.isActive)
                                continuation.resume(scanned.toMap())
                        }
                    }
                }
                    ?: throw IOException(
                        "Timed out while adding downloaded files to the music library"
                    )
            results.putAll(scanned)
        }
        return results
    }

    private suspend fun syncPlaylists(
        context: Context,
        remotePlaylists: List<RemotePlaylist>,
        tracks: List<RemoteTrack>,
        localFiles: List<AudioFile>?,
        removeMissing: Boolean = false,
        checkCancellation: Boolean = true,
        localUrisByRemoteId: Map<String, String> = emptyMap(),
    ) {
        _progress.value =
            SyncProgress.Preparing(
                if (localFiles == null) "Creating playlists" else "Syncing playlists"
            )
        if (checkCancellation) checkCancelled()
        val playlistRepository = PlaylistRepository.getInstance(context)
        val existing = playlistRepository.getPlaylists().associateBy { it.id }.toMutableMap()
        val mappedIds = SettingsStore.getSyncPlaylistIds().toMutableMap()
        val remoteIds = remotePlaylists.mapTo(mutableSetOf()) { it.id }

        if (removeMissing)
            (mappedIds.keys - remoteIds).forEach { remoteId ->
                mappedIds.remove(remoteId)?.let { localId ->
                    if (existing.containsKey(localId)) playlistRepository.deletePlaylist(localId)
                    existing.remove(localId)
                }
            }

        val localByUri = localFiles.orEmpty().associateBy { it.uri.toString() }
        val localByRemoteId = tracks.associate { track ->
            track.id to
                (localByUri[localUrisByRemoteId[track.id]]
                    ?: bestLocalMatch(track, localFiles.orEmpty()))
        }
        remotePlaylists.forEach { remote ->
            if (checkCancellation) checkCancelled()
            var localId = mappedIds[remote.id]?.takeIf(existing::containsKey)
            if (localId == null) {
                val usedNames = existing.values.mapTo(mutableSetOf()) { it.name.lowercase() }
                var localName = remote.name
                var suffix = 1
                while (localName.lowercase() in usedNames) {
                    localName =
                        if (suffix == 1) "${remote.name} (AudioStream)"
                        else "${remote.name} (AudioStream $suffix)"
                    suffix++
                }
                localId = playlistRepository.createPlaylist(localName)
                mappedIds[remote.id] = localId
                SettingsStore.setSyncPlaylistIds(mappedIds)
                playlistRepository.getPlaylist(localId)?.let { existing[localId] = it }
            }
            val current = existing[localId]
            if (
                current != null &&
                    current.name != remote.name &&
                    existing.values.none {
                        it.id != localId && it.name.equals(remote.name, ignoreCase = true)
                    }
            ) {
                playlistRepository.renamePlaylist(localId, remote.name)
            }
            if (localFiles != null) {
                val audioIds = remote.trackIds.mapNotNull { localByRemoteId[it]?.id }.distinct()
                playlistRepository.replacePlaylistAudio(localId, audioIds)
            }
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
}
