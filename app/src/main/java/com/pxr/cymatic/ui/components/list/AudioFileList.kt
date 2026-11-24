package com.pxr.cymatic.ui.components.list

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.pxr.cymatic.data.library.artistDisplayText
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.model.AudioMetadata
import com.pxr.cymatic.data.store.RemoteSourceRegistry
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation
import com.pxr.cymatic.ui.components.common.rememberWheelSelection

@Composable
fun AudioFileList(
    audioFiles: List<AudioFile>,
    modifier: Modifier = Modifier,
    scrollTargetId: Long? = null,
    onItemClick: (AudioFile) -> Unit,
    onItemLongClick: (AudioFile) -> Unit = {},
    topOffset: Dp = 0.dp,
    bottomOffset: Dp = 0.dp,
) {
    val listState = rememberLazyListState()
    val wheel = LocalWheelNavigation.current
    val showSourceLabel = remember(audioFiles) {
        audioFiles.map { it.sourceId }.distinct().size > 1
    }
    val sources by RemoteSourceRegistry.sourcesFlow.collectAsState(
        initial = RemoteSourceRegistry.currentSources
    )
    val sourceNames = remember(sources) { sources.associate { it.id to it.displayName } }
    val selectedIndex =
        rememberWheelSelection(
            audioFiles.size,
            listState,
            onSelect = {
                onItemClick(audioFiles[it])
                wheel?.onPlaybackRequested?.invoke()
            },
            onContext = { onItemLongClick(audioFiles[it]) },
        )

    LaunchedEffect(scrollTargetId) {
        if (scrollTargetId != null) {
            val index = audioFiles.indexOfFirst { it.id == scrollTargetId }
            if (index != -1) {
                listState.scrollToItem(index)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
    ) {
        items(
            count = audioFiles.size,
            key = { i -> audioFiles[i].id },
        ) { i ->
            val audioFile = audioFiles[i]
            val isCurrent = i == selectedIndex
            if (i == 0 && topOffset > 0.dp) {
                Box(modifier = Modifier.height(topOffset))
            }
            val sourceLabel =
                if (showSourceLabel) {
                    audioFile.sourceId?.let { sourceNames[it] ?: "Remote" } ?: "This device"
                } else null
            AudioFileItem(
                audioFile = audioFile,
                isCurrent = isCurrent,
                sourceLabel = sourceLabel,
                onClick = {
                    onItemClick(audioFile)
                    wheel?.onPlaybackRequested?.invoke()
                },
                onLongClick = { onItemLongClick(audioFile) },
                modifier = Modifier.heightIn(min = 64.dp),
            )
            if (i == audioFiles.size - 1 && bottomOffset > 0.dp) {
                Box(modifier = Modifier.height(bottomOffset))
            }
        }
    }
}

@Composable
fun AudioFileItem(
    audioFile: AudioFile,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sourceLabel: String? = null,
    onLongClick: () -> Unit = {},
) {
    val metadata = audioFile.metadata
    val work by com.pxr.cymatic.sync.LibraryOfflineManager.workState.collectAsState()
    val busy =
        audioFile.availability != com.pxr.cymatic.data.model.LibraryAvailability.OFFLINE &&
                com.pxr.cymatic.sync.RemoteLibraryItemKey(
                    audioFile.sourceId.orEmpty(),
                    audioFile.remoteId.orEmpty(),
                ) in work.tracks

    @SuppressLint("DefaultLocale")
    fun parseDuration(durationMs: Long?): String {
        if (durationMs == null) return "0:00"
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    val artistText = artistDisplayText(metadata)
    ListItem(
        label = metadata.title ?: "Unknown Title",
        subLabel = if (sourceLabel != null) "$artistText · $sourceLabel" else artistText,
        trailing = parseDuration(metadata.duration),
        trailingContent = { color ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibraryAvailabilityIcon(audioFile.availability, color, busy = busy)
                Text(parseDuration(metadata.duration), color = color, fontSize = 12.sp)
            }
        },
        isActive = isCurrent,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
fun AudioFileItemPreview() {
    val sampleAudioFile =
        AudioFile(
            id = 1L,
            uri = "file:///storage/emulated/0/Music/song1.mp3".toUri(),
            metadata =
                AudioMetadata(
                    title = "Song Title",
                    artist = "Artist A",
                    album = "Album X",
                    duration = 210000L,
                    format = "audio/mpeg",
                    bitRate = 320000L,
                    sampleRate = 44100L,
                    artworkUri = null,
                ),
            size = 5120000,
        )
    val sampleAudioFileCJK =
        AudioFile(
            id = 1L,
            uri = "file:///storage/emulated/0/Music/song1.mp3".toUri(),
            metadata =
                AudioMetadata(
                    title = "ささやくように",
                    artist = "恋を唄う - 3:30",
                    album = "Album X",
                    duration = 210000L,
                    format = "audio/mpeg",
                    bitRate = 320000L,
                    sampleRate = 44100L,
                    artworkUri = null,
                ),
            size = 5120000,
        )
    val sampleAudioFileCB1 =
        AudioFile(
            id = 1L,
            uri = "file:///storage/emulated/0/Music/song1.mp3".toUri(),
            metadata =
                AudioMetadata(
                    title = "ささやくように",
                    artist = "Artist",
                    album = "Album X",
                    duration = 210000L,
                    format = "audio/mpeg",
                    bitRate = 320000L,
                    sampleRate = 44100L,
                    artworkUri = null,
                ),
            size = 5120000,
        )
    val sampleAudioFileCB2 =
        AudioFile(
            id = 1L,
            uri = "file:///storage/emulated/0/Music/song1.mp3".toUri(),
            metadata =
                AudioMetadata(
                    title = "Song Title",
                    artist = "恋を唄う - 3:30",
                    album = "Album X",
                    duration = 210000L,
                    format = "audio/mpeg",
                    bitRate = 320000L,
                    sampleRate = 44100L,
                    artworkUri = null,
                ),
            size = 5120000,
        )
    Column {
        AudioFileItem(
            audioFile = sampleAudioFile,
            isCurrent = false,
            onClick = {},
        )
        Box(
            modifier = Modifier
                .height(1.dp)
                .background(Color.Gray)
                .fillMaxWidth()
        )
        AudioFileItem(
            audioFile = sampleAudioFileCJK,
            isCurrent = false,
            onClick = {},
            modifier = Modifier.heightIn(min = 76.dp),
        )
        Box(
            modifier = Modifier
                .height(1.dp)
                .background(Color.Gray)
                .fillMaxWidth()
        )
        AudioFileItem(
            audioFile = sampleAudioFileCB1,
            isCurrent = false,
            onClick = {},
        )
        Box(
            modifier = Modifier
                .height(1.dp)
                .background(Color.Gray)
                .fillMaxWidth()
        )
        AudioFileItem(
            audioFile = sampleAudioFileCB2,
            isCurrent = false,
            onClick = {},
        )
    }
}
