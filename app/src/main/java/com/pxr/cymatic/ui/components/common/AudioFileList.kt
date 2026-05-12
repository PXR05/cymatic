package com.pxr.cymatic.ui.components.common

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.model.AudioMetadata
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.state.rememberPlaybackState

@Composable
fun AudioFileList(
    audioFiles: List<AudioFile>,
    modifier: Modifier = Modifier,
    scrollTargetId: Long? = null,
    onItemClick: (AudioFile) -> Unit,
    onItemLongClick: (AudioFile) -> Unit = {},
    topOffset: Dp = 0.dp,
    bottomOffset: Dp = 0.dp
) {
    val mediaController = LocalMediaController.current
    val playbackState = rememberPlaybackState(mediaController)
    val listState = rememberLazyListState()

    LaunchedEffect(scrollTargetId) {
        if (scrollTargetId != null) {
            val index = audioFiles.indexOfFirst{ it.id == scrollTargetId }
            if (index != -1) {
                listState.scrollToItem(index)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
    ) {
        items(
            count = audioFiles.size,
            key = { i -> audioFiles[i].id }
        ) { i ->
            val audioFile = audioFiles[i]
            val isCurrent = playbackState.currentMediaId == audioFile.id.toString()
            if (i == 0 && topOffset > 0.dp) {
                Box(modifier = Modifier.height(topOffset))
            }
            AudioFileItem(
                audioFile = audioFile,
                isCurrent = isCurrent,
                onClick = { onItemClick(audioFile) },
                onLongClick = { onItemLongClick(audioFile) },
                modifier = Modifier.height(76.dp)
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
    onLongClick: () -> Unit = {},
) {
    val metadata = audioFile.metadata

    @SuppressLint("DefaultLocale")
    fun parseDuration(durationMs: Long?): String {
        if (durationMs == null) return "0:00"
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    ListItem(
        label = metadata.title ?: "Unknown Title",
        subLabel = metadata.artist ?: "Unknown Artist",
        trailing = parseDuration(metadata.duration),
        isActive = isCurrent,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
    )
}


@Preview(showBackground = true)
@Composable
fun AudioFileItemPreview() {
    val sampleAudioFile = AudioFile(
        id = 1L,
        uri = "file:///storage/emulated/0/Music/song1.mp3".toUri(),
        metadata = AudioMetadata(
            title = "Song Title",
            artist = "Artist A",
            album = "Album X",
            duration = 210000L,
            format = "audio/mpeg",
            bitRate = 320000L,
            sampleRate = 44100L,
            artworkUri = null
        ),
        size = 5120000
    )
    val sampleAudioFileCJK = AudioFile(
        id = 1L,
        uri = "file:///storage/emulated/0/Music/song1.mp3".toUri(),
        metadata = AudioMetadata(
            title = "ささやくように",
            artist = "恋を唄う - 3:30",
            album = "Album X",
            duration = 210000L,
            format = "audio/mpeg",
            bitRate = 320000L,
            sampleRate = 44100L,
            artworkUri = null
        ),
        size = 5120000
    )
    val sampleAudioFileCB1 = AudioFile(
        id = 1L,
        uri = "file:///storage/emulated/0/Music/song1.mp3".toUri(),
        metadata = AudioMetadata(
            title = "ささやくように",
            artist = "Artist",
            album = "Album X",
            duration = 210000L,
            format = "audio/mpeg",
            bitRate = 320000L,
            sampleRate = 44100L,
            artworkUri = null
        ),
        size = 5120000
    )
    val sampleAudioFileCB2 = AudioFile(
        id = 1L,
        uri = "file:///storage/emulated/0/Music/song1.mp3".toUri(),
        metadata = AudioMetadata(
            title = "Song Title",
            artist = "恋を唄う - 3:30",
            album = "Album X",
            duration = 210000L,
            format = "audio/mpeg",
            bitRate = 320000L,
            sampleRate = 44100L,
            artworkUri = null
        ),
        size = 5120000
    )
    Column {
        AudioFileItem(
            audioFile = sampleAudioFile,
            isCurrent = false,
            onClick = {}
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
            modifier = Modifier.height(76.dp)
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
            onClick = {}
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
            onClick = {}
        )
    }
}


