package com.pxr.cymatic.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.R
import com.pxr.cymatic.data.library.albumDisplayName
import com.pxr.cymatic.data.library.artistDisplayNames
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.media.LibraryChanges
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.model.LibraryAvailability
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.components.PixelIcon
import com.pxr.cymatic.ui.components.list.LibraryAvailabilityIcon
import com.pxr.cymatic.ui.locals.LocalInterfaceSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class LibraryGroup(val songs: List<AudioFile>) {
    val availability =
        LibraryAvailability.fromCounts(
            songs.count { it.availability == LibraryAvailability.OFFLINE },
            songs.size,
            remote = songs.any { it.sourceId != null },
        )
    val canKeepOffline = songs.any {
        it.sourceId != null && it.remoteId != null && it.availability != LibraryAvailability.OFFLINE
    }
}

internal class LibraryGroups(songs: List<AudioFile>) {
    val albums = songs.groupBy(::albumDisplayName).mapValues { LibraryGroup(it.value) }
    val albumsBySource =
        songs.groupBy { albumDisplayName(it) to it.sourceId }.mapValues { LibraryGroup(it.value) }
    val artists =
        buildMap<String, MutableList<AudioFile>> {
            for (song in songs) {
                for (name in artistDisplayNames(song)) {
                    getOrPut(name) { mutableListOf() }.add(song)
                }
            }
        }
            .mapValues { LibraryGroup(it.value) }
}

private object LibraryGroupsCache {
    private val mutex = Mutex()

    @Volatile
    private var snapshot: Pair<List<AudioFile>, LibraryGroups>? = null
    val empty = LibraryGroups(emptyList())

    fun cached(songs: List<AudioFile>?): LibraryGroups? {
        if (songs == null) return null
        snapshot?.takeIf { it.first === songs }?.second?.let { return it }
        return LibraryGroups(songs).also { snapshot = songs to it }
    }

    suspend fun groups(songs: List<AudioFile>): LibraryGroups = mutex.withLock {
        snapshot?.takeIf { it.first === songs }?.second
            ?: withContext(Dispatchers.Default) { LibraryGroups(songs) }
                .also {
                    snapshot = songs to it
                }
    }
}

@Composable
internal fun rememberLibraryGroups(): LibraryGroups {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { AudioRepository.getInstance(context) }
    val groups by
    produceState(
        LibraryGroupsCache.cached(repository.getCachedAudio()) ?: LibraryGroupsCache.empty,
        repository,
    ) {
        merge(LibraryChanges.version, SettingsStore.lastScanTimeMsFlow).collectLatest {
            try {
                val songs = withContext(Dispatchers.IO) { repository.getAllAudio() }
                value = LibraryGroupsCache.groups(songs)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }
    }
    return groups
}

@Composable
internal fun LibraryGroupIndicator(group: LibraryGroup?, color: Color) {
    val work by com.pxr.cymatic.sync.LibraryOfflineManager.workState.collectAsState()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        group?.let {
            val busy =
                it.availability != LibraryAvailability.OFFLINE &&
                        it.songs.any { song ->
                            com.pxr.cymatic.sync.RemoteLibraryItemKey(
                                song.sourceId.orEmpty(),
                                song.remoteId.orEmpty(),
                            ) in work.tracks
                        }
            LibraryAvailabilityIcon(it.availability, color, busy = busy)
        }
        if (LocalInterfaceSettings.current.showIcons) {
            PixelIcon(
                resId = R.drawable.ic_pixel_chevron_right,
                size = 14.dp,
                tint = color,
            )
        } else {
            Text(">", color = color, fontSize = 20.sp)
        }
    }
}
