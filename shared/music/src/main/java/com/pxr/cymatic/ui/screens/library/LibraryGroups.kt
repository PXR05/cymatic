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
import com.pxr.cymatic.data.library.albumDisplayName
import com.pxr.cymatic.data.library.artistDisplayName
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.media.LibraryChanges
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.model.LibraryAvailability
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.components.list.LibraryAvailabilityIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.merge
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
    val artists = songs.groupBy(::artistDisplayName).mapValues { LibraryGroup(it.value) }
}

@Composable
internal fun rememberLibraryGroups(): LibraryGroups {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { AudioRepository.getInstance(context) }
    val songs by
        produceState(repository.getCachedAudio().orEmpty(), repository) {
            merge(LibraryChanges.version, SettingsStore.lastScanTimeMsFlow).collect {
                runCatching { withContext(Dispatchers.IO) { repository.getAllAudio() } }
                    .onSuccess { value = it }
            }
        }
    return remember(songs) { LibraryGroups(songs) }
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
        Text(">", color = color, fontSize = 20.sp)
    }
}
