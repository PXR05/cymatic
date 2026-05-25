package com.pxr.cymatic.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.model.AudioMetadata

@Preview(showBackground = true)
@Composable
fun InfoPreview() {
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
        for (file in listOf(
            sampleAudioFile,
            sampleAudioFileCJK,
            sampleAudioFileCB1,
            sampleAudioFileCB2
        )) {
            Info(
                metadata = file.metadata
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface)
            )
        }
    }
}

@Composable
fun Info(
    metadata: AudioMetadata,
    modifier: Modifier = Modifier,
    titleSize: TextUnit = 20.sp,
    artistSize: TextUnit = 14.sp,
    gap: Dp = 4.dp,
) {
    val cjkRegex = Regex("[\\u4E00-\\u9FFF|\\u3040-\\u309F\\u30A0-\\u30FF\\uAC00-\\uD7AF]")
    val title = metadata.title ?: "Unknown Title"
    val artist = metadata.artist ?: "Unknown Artist"
    val isTitleCJK = title.contains(cjkRegex)
    val isArtistCJK = artist.contains(cjkRegex)
    val titleFontStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = titleSize,
        letterSpacing = if (isTitleCJK) 2.sp else 0.sp,
    )
    val artistFontStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = artistSize,
        letterSpacing = if (isArtistCJK) 1.5.sp else 0.sp,
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = modifier
            .fillMaxWidth()
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            fontWeight = FontWeight.SemiBold,
            style = titleFontStyle,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(
                    top = if (isTitleCJK) 6.dp else 0.dp,
                )
        )

        Text(
            text = artist,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            style = artistFontStyle,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(
                    top = if (isArtistCJK) 2.5.dp else 0.dp,
                    bottom = if (isArtistCJK) 1.dp else 0.dp
                ),
        )
    }
}