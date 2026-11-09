package com.pxr.cymatic.sync

import android.content.Context
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.media.PlaylistRepository
import com.pxr.cymatic.data.model.LibraryAvailability
import com.pxr.cymatic.data.store.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

data class OfflineSelection(
    val tracks: Set<String> = emptySet(),
    val playlists: Set<String> = emptySet()
) {
    val isEmpty get() = tracks.isEmpty() && playlists.isEmpty()
    fun trackIds(catalog: RemoteCatalog): Set<String> = tracks + catalog.playlists
        .filter { it.id in playlists }.flatMap { it.trackIds }
}

internal object OfflineSelectionStore {
    fun read(context: Context, sourceId: String): OfflineSelection {
        val prefs = context.getSharedPreferences("library-offline", Context.MODE_PRIVATE)
        return OfflineSelection(
            prefs.getStringSet("${sourceId}_tracks", emptySet()).orEmpty().toSet(),
            prefs.getStringSet("${sourceId}_playlists", emptySet()).orEmpty().toSet()
        )
    }

    fun save(context: Context, sourceId: String, selection: OfflineSelection) {
        check(
            context.getSharedPreferences("library-offline", Context.MODE_PRIVATE).edit()
                .putStringSet("${sourceId}_tracks", selection.tracks)
                .putStringSet("${sourceId}_playlists", selection.playlists).commit()
        ) { "Could not save offline selection" }
    }
}

object LibraryOfflineManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = CoalescingWorkQueue<Pair<Context, String>> { it.second }
    private val selectionMutex = Mutex()
    private val work = OfflineWorkTracker()
    val workState = work.state

    internal fun setSyncWork(items: OfflineWorkItems) = work.setActive(items)
    private val noticeIds = AtomicLong()
    private val mutableNotice = MutableStateFlow<OfflineQueueNotice?>(null)
    val notice = mutableNotice.asStateFlow()

    private fun notify(message: String, failed: Boolean = false) {
        val notice = OfflineQueueNotice(noticeIds.incrementAndGet(), message, failed)
        mutableNotice.value = notice
        scope.launch {
            delay(if (failed) 6000 else 3500)
            mutableNotice.compareAndSet(notice, null)
        }
    }

    init {
        scope.launch {
            while (true) {
                val (context, sourceId) = queue.receive()
                val source = SyncCatalogStore.source(context, sourceId)
                if (source == null) {
                    work.finish(work.snapshot(sourceId))
                    notify("Download unavailable · reconnect the library", failed = true)
                    continue
                }
                while (LibrarySyncManager.isRunning) delay(250)
                val requests = work.snapshot(sourceId)
                try {
                    while (true) {
                        val result = runCatching { LibrarySyncManager.sync(context, source) }
                        val error = result.exceptionOrNull()
                        if (error?.message != "A library sync is already running") {
                            if (error != null && error !is SyncCancelledException)
                                notify(
                                    "Download failed · ${error.message ?: "try again"}",
                                    failed = true
                                )
                            break
                        }
                        delay(250)
                    }
                } finally {
                    work.finish(requests)
                }
            }
        }
    }

    fun keepTrack(context: Context, sourceId: String, remoteId: String, title: String = "Song") =
        request(context, sourceId, setOf(remoteId), emptySet(), title)

    fun keepPlaylist(
        context: Context,
        sourceId: String,
        remoteId: String,
        title: String = "Playlist"
    ) =
        request(context, sourceId, emptySet(), setOf(remoteId), title)

    fun keepPlaylist(context: Context, playlistId: Long, title: String = "Playlist") {
        scope.launch {
            try {
                val songs = PlaylistRepository.getInstance(context).getPlaylistAudio(playlistId)
                songs.filter { it.sourceId != null && it.remoteId != null }
                    .groupBy { it.sourceId!! }.forEach { (source, tracks) ->
                    request(
                        context,
                        source,
                        tracks.mapTo(mutableSetOf()) { it.remoteId!! },
                        emptySet(),
                        title,
                        playlistId
                    )
                }
            } catch (error: Exception) {
                notify("Could not queue $title", failed = true)
            }
        }
    }

    fun keepSongs(
        context: Context,
        songs: List<com.pxr.cymatic.data.model.AudioFile>,
        title: String
    ) {
        songs.filter { it.sourceId != null && it.remoteId != null }
            .groupBy { it.sourceId!! }
            .forEach { (source, tracks) ->
                request(
                    context,
                    source,
                    tracks.mapTo(mutableSetOf()) { it.remoteId!! },
                    emptySet(),
                    title
                )
            }
    }

    private fun request(
        context: Context,
        sourceId: String,
        tracks: Set<String>,
        playlists: Set<String>,
        title: String,
        playlistId: Long? = null
    ) {
        val application = context.applicationContext
        scope.launch {
            try {
                selectionMutex.withLock {
                    val previous = OfflineSelectionStore.read(application, sourceId)
                    OfflineSelectionStore.save(
                        application, sourceId,
                        previous.copy(
                            tracks = previous.tracks + tracks,
                            playlists = previous.playlists + playlists
                        )
                    )
                }
                val ids = tracks + SyncCatalogStore.read(application, sourceId)?.playlists.orEmpty()
                    .filter { it.id in playlists }.flatMap { it.trackIds }
                val offline = AudioRepository.getInstance(application).getAllAudio()
                    .filter { it.sourceId == sourceId && it.availability == LibraryAvailability.OFFLINE }
                    .mapNotNullTo(mutableSetOf()) { it.remoteId }
                work.enqueue(
                    sourceId, OfflineWorkItems(
                        (ids - offline).mapTo(mutableSetOf()) {
                            RemoteLibraryItemKey(
                                sourceId,
                                it
                            )
                        },
                        playlists.mapTo(mutableSetOf()) { RemoteLibraryItemKey(sourceId, it) },
                        setOfNotNull(playlistId),
                    )
                )
                com.pxr.cymatic.data.media.LibraryChanges.notifyChanged()
                queue.offer(application to sourceId)
                runCatching {
                    if (RemoteLibraries.currentSource().id == sourceId) {
                        if (!SettingsStore.getSyncContentConfigured() && SettingsStore.getSyncNetwork() == SyncNetwork.NEVER.value)
                            SettingsStore.setSyncNetwork(SyncNetwork.WIFI.value)
                        LibrarySyncJobService.reschedule(application)
                    }
                }
            } catch (error: Exception) {
                notify("Could not queue $title", failed = true)
                SettingsStore.setSyncResult(
                    System.currentTimeMillis(),
                    "Download failed: ${error.message}"
                )
            }
        }
    }
}

data class OfflineQueueNotice(val id: Long, val message: String, val failed: Boolean = false)
