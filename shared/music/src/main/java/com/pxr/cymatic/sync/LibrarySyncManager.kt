package com.pxr.cymatic.sync

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import com.pxr.cymatic.data.store.LibrarySyncSettings
import java.io.EOFException
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

object LibrarySyncManager {
    private const val MANIFEST = ".cymatic-sync.json"
    private val running = AtomicBoolean(false)
    private val cancelRequested = AtomicBoolean(false)
    private val activeClient = AtomicReference<RemoteLibraryClient?>(null)
    private val _progress = MutableStateFlow<SyncProgress>(SyncProgress.Idle)
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()
    private val manualScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val isRunning: Boolean
        get() = running.get()

    fun start(context: Context): Boolean {
        if (!begin()) return false
        manualScope.launch {
            runCatching { syncAcquired(context.applicationContext) }
        }
        return true
    }

    fun cancel() {
        if (!running.get()) return
        cancelRequested.set(true)
        _progress.value = SyncProgress.Cancelling
        activeClient.get()?.cancel()
    }

    fun clearStatus() {
        if (!running.get()) _progress.value = SyncProgress.Idle
    }

    suspend fun sync(context: Context, source: RemoteLibrarySource? = null): SyncSummary =
        withContext(Dispatchers.IO) {
            check(begin()) { "A library sync is already running" }
            syncAcquired(context.applicationContext, source)
        }

    private fun begin(): Boolean {
        if (!running.compareAndSet(false, true)) return false
        cancelRequested.set(false)
        _progress.value = SyncProgress.Preparing("Updating library")
        return true
    }

    private suspend fun syncAcquired(
        context: Context,
        source: RemoteLibrarySource? = null,
    ): SyncSummary {
        return try {
            syncBlocking(context.applicationContext, source).also {
                _progress.value = SyncProgress.Complete(it)
            }
        } catch (_: SyncCancelledException) {
            _progress.value = SyncProgress.Cancelled
            LibrarySyncSettings.setSyncResult(System.currentTimeMillis(), "Sync stopped")
            throw SyncCancelledException()
        } catch (error: Throwable) {
            if (cancelRequested.get()) {
                _progress.value = SyncProgress.Cancelled
                LibrarySyncSettings.setSyncResult(System.currentTimeMillis(), "Sync stopped")
                throw SyncCancelledException()
            }
            _progress.value = SyncProgress.Failed(error.message ?: "Sync failed")
            LibrarySyncSettings.setSyncResult(
                System.currentTimeMillis(),
                "Sync failed: ${error.message ?: "unknown error"}",
            )
            throw error
        } finally {
            LibraryOfflineManager.setSyncWork(OfflineWorkItems())
            activeClient.set(null)
            running.set(false)
        }
    }

    private suspend fun syncBlocking(
        context: Context,
        requestedSource: RemoteLibrarySource?,
    ): SyncSummary {
        val currentSource = RemoteLibraries.currentSource()
        val source = requestedSource ?: currentSource
        val current = source.id == currentSource.id
        SyncCredentialStore.migrateLegacy(context, source.id)
        SyncCatalogStore.migrateLegacy(context, source)
        LibrarySyncSettings.migrateSyncPlaylistIds(source.id)
        val client = RemoteLibraries.client(context, source)
        activeClient.set(client)
        val catalog =
            RemoteLibraries.catalogRepository(context).refresh(source, client, ::checkCancelled)
        RemoteLibraryRepository.publish(context, source, catalog)
        val tracks = catalog.tracks
        val remotePlaylists = catalog.playlists

        val mode =
            if (current) SyncContentMode.from(LibrarySyncSettings.getSyncContentMode())
            else SyncContentMode.NONE
        val pins = OfflineSelectionStore.read(context, source.id)
        val selectedIds =
            (if (current) LibrarySyncSettings.getSyncSelectedPlaylists() else emptySet()) +
                pins.playlists
        val playlistsToSync =
            when (mode) {
                SyncContentMode.NONE -> remotePlaylists.filter { it.id in pins.playlists }
                SyncContentMode.ALL -> remotePlaylists
                SyncContentMode.SELECTED_PLAYLISTS ->
                    remotePlaylists.filter { it.id in selectedIds }
            }
        val selectedTrackIds = pins.trackIds(catalog) + playlistsToSync.flatMap { it.trackIds }
        val tracksToSync =
            if (mode == SyncContentMode.ALL) tracks else tracks.filter { it.id in selectedTrackIds }
        if (tracksToSync.isEmpty()) {
            LibrarySyncSettings.setSyncResult(
                System.currentTimeMillis(),
                "No music selected for download",
            )
            return SyncSummary(0, 0, 0, 0, 0)
        }

        val offline =
            com.pxr.cymatic.data.media.AudioRepository.getInstance(context)
                .getAllAudio()
                .filter { it.sourceId == source.id && it.offlineUri != null }
                .mapNotNullTo(mutableSetOf()) { it.remoteId }
        val pending = tracksToSync.map { it.id }.toSet() - offline
        LibraryOfflineManager.setSyncWork(
            OfflineWorkItems(
                pending.mapTo(mutableSetOf()) { source.itemKey(it) },
                playlistsToSync
                    .filter { playlist -> playlist.trackIds.any { it in pending } }
                    .mapTo(mutableSetOf()) { source.itemKey(it.id) },
            )
        )

        val directory = if (current) LibrarySyncSettings.getSyncDirectory() else ""
        val tree: SyncStorage =
            if (directory.isBlank()) {
                ManagedSyncStorage(java.io.File(context.filesDir, "library-downloads/${source.id}"))
            } else {
                SafTree(context.contentResolver, Uri.parse(directory))
            }
        val layout = SyncLayout.from(LibrarySyncSettings.getSyncLayout())
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
                (if (source.provider == "audiostream") "" else "${source.provider}\n") +
                    "${source.address}\n${source.account}\n$directory",
            )
        val folderManifest = readManifest(tree, source.id)
        val previous = manifestStore.read() ?: folderManifest
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
                        if (directory.isBlank()) writeManifest(tree, entries, source.id)
                    } catch (error: Exception) {
                        throw SyncCheckpointException(error)
                    }
                },
            )
        var downloaded = 0
        var unchanged = 0
        var failed = 0
        var firstFailure: String? = null
        val pendingPaths = linkedMapOf<String, String>()

        suspend fun indexCompletedTracks() {
            if (pendingPaths.isEmpty()) return
            _progress.value = SyncProgress.Preparing("Adding downloaded tracks to playlists")
            writeManifest(tree, reuse.entries, source.id)
            val paths = pendingPaths.keys.mapNotNull(tree::absolutePath)
            scanMediaFiles(context, paths)
            RemoteLibraryRepository.publish(context, source, catalog)
            pendingPaths.values.distinct().forEach { remoteId ->
                LibraryArtworkProvider.cache(context, source.itemKey(remoteId))
            }
            pendingPaths.clear()
        }

        var interrupted: Throwable? = null
        try {
            desired.forEachIndexed { index, target ->
                checkCancelled()
                _progress.value =
                    SyncProgress.Preparing("Checking local files (${index + 1}/${desired.size})")
                val result = runCatching {
                    if (reuse.reuse(target)) return@runCatching null
                    reuse.prepareWrite(target)
                    val received = downloadTrack(client, tree, target, index + 1, desired.size)
                    _progress.value = SyncProgress.Preparing("Saving ${target.track.title}")
                    received
                }
                result.exceptionOrNull()?.let {
                    if (it is SyncCheckpointException) throw it
                    if (
                        it is SyncLocalCommitException && it.destinationPath == target.relativePath
                    ) {
                        reuse.record(target.copy(relativePath = it.temporaryPath), it.localSize)
                    }
                    reuse.discardMissing(target.relativePath)
                    if (it is SyncCancelledException || cancelRequested.get())
                        throw SyncCancelledException()
                }
                if (result.isSuccess) {
                    val received = result.getOrThrow()
                    if (received != null) {
                        pendingPaths[target.relativePath] = target.track.id
                        reuse.record(target, received)
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
        if (failed == 0 && mode != SyncContentMode.NONE) {
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
        writeManifest(tree, next, source.id)

        val syncedPaths =
            (entries.keys + previous.keys)
                .distinct()
                .filterNot { it.substringAfterLast('/').startsWith(".cymatic-sync-") }
                .mapNotNull(tree::absolutePath)
        scanMediaFiles(context, syncedPaths)
        RemoteLibraryRepository.publish(context, source, catalog)

        val summary = SyncSummary(downloaded, unchanged, removed, failed, playlistsToSync.size)
        val message =
            if (failed == 0) {
                "Synced: $downloaded downloaded, $unchanged unchanged, ${playlistsToSync.size} playlists"
            } else {
                "Sync incomplete: $downloaded downloaded, $failed failed. $firstFailure"
            }
        LibrarySyncSettings.setSyncResult(System.currentTimeMillis(), message)
        if (failed > 0) throw IOException(message)
        return summary
    }

    private suspend fun downloadTrack(
        client: RemoteLibraryClient,
        tree: SyncStorage,
        target: SyncTarget,
        current: Int,
        total: Int,
    ): Long {
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
                val connection = client.openDownload(target.track.id)
                try {
                    val expected =
                        SyncDownloadValidation.expectedSize(
                            connection.contentLength,
                            connection.contentEncoding,
                            connection.contentType,
                        )
                    val totalBytes = expected.coerceAtLeast(0L)
                    var copied = 0L
                    _progress.value =
                        SyncProgress.Downloading(current, total, target.track.title, 0L, totalBytes)
                    val input = connection.input.buffered()
                    try {
                        tree.write(target.relativePath, mimeType(target.track.filename)) { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
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
                                            totalBytes,
                                        )
                                    lastUpdate = now
                                }
                            }
                            SyncDownloadValidation.validate(copied, expected)
                            _progress.value = SyncProgress.Preparing("Saving ${target.track.title}")
                        }
                    } finally {
                        runCatching { input.close() }
                            .onFailure {
                                Log.w(
                                    "LibrarySync",
                                    "Could not close the audio response for ${target.track.id}",
                                    it,
                                )
                            }
                    }
                    return copied
                } finally {
                    connection.close()
                }
            } catch (error: IOException) {
                checkCancelled()
                val retryable =
                    when (error) {
                        is AudioStreamRequestException ->
                            error.status == 408 || error.status == 429 || error.status >= 500

                        is SocketException,
                        is SocketTimeoutException,
                        is EOFException,
                        is IncompleteSyncDownloadException -> true

                        else -> false
                    }
                if (!retryable || attempt == 2) throw error
                _progress.value =
                    SyncProgress.Preparing(
                        "Retrying ${target.track.title}: ${error.message ?: "transfer interrupted"}"
                    )
                Log.w(
                    "LibrarySync",
                    "Retrying ${target.track.id} after transfer attempt ${attempt + 1}",
                    error,
                )
                val retryAfter = (error as? AudioStreamRequestException)?.retryAfterMillis ?: 0L
                delay(maxOf(1000L shl attempt, retryAfter))
            }
        }
        throw IOException("Could not complete the audio transfer")
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

    private fun readManifest(tree: SyncStorage, sourceId: String): Map<String, SyncManifestEntry> {
        val value = tree.readText(MANIFEST) ?: return emptyMap()
        val owner = org.json.JSONObject(value).optString("sourceId")
        require(owner.isBlank() || owner == sourceId) {
            "This folder belongs to another library. Choose a separate folder or use Cymatic storage."
        }
        return SyncManifestStore.decode(value)
    }

    private fun writeManifest(
        tree: SyncStorage,
        entries: Map<String, SyncManifestEntry>,
        sourceId: String,
    ) {
        val value = org.json.JSONObject(SyncManifestStore.encode(entries)).put("sourceId", sourceId)
        tree.writeText(MANIFEST, value.toString())
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
}
