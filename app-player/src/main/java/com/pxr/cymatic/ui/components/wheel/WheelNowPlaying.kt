package com.pxr.cymatic.ui.components.wheel

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pxr.cymatic.design.R
import com.pxr.cymatic.playback.toAudioMetadata
import com.pxr.cymatic.ui.components.ScrollingTrackText
import com.pxr.cymatic.ui.components.screen.WheelScreenHeader
import com.pxr.cymatic.ui.components.trackTextHeight
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.state.PlaybackState

@Composable
internal fun WheelNowPlaying(
    playback: PlaybackState,
    volume: Int,
    maxVolume: Int,
    coverVisible: Boolean,
    onToggleCover: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val metadata = playback.metadata ?: LocalMediaController.current?.currentMediaItem?.toAudioMetadata()
    val toggleCover by rememberUpdatedState(onToggleCover)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { toggleCover() }) }
    ) {
        WheelScreenHeader {
            Icon(
                painterResource(if (playback.isPlaying) R.drawable.ic_pixel_play else R.drawable.ic_pixel_pause),
                if (playback.isPlaying) "Playing" else "Paused",
                Modifier.size(18.dp)
            )
        }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 20.dp)
        ) {
            val compact = maxHeight < 300.dp
            val artistSize = if (compact) 18.sp else 20.sp
            val titleSize = if (compact) 26.sp else 30.sp
            val artistLineHeight = if (compact) 22.sp else 26.sp
            val titleLineHeight = if (compact) 30.sp else 36.sp
            val gap = if (compact) 6.dp else 8.dp
            val artistHeight = trackTextHeight(artistSize, artistLineHeight, 1)
            val titleHeight = trackTextHeight(titleSize, titleLineHeight, 1, FontWeight.SemiBold)
            val albumHeight = trackTextHeight(12.sp, 18.sp, 1)
            val format = when (val mime = metadata?.format?.substringAfterLast('/')?.uppercase()) {
                "MPEG" -> "MP3"
                else -> mime
            }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${(playback.queue.currentIndex + 1).coerceAtLeast(0)} OF ${playback.totalTracks}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!format.isNullOrBlank()) FormatBadge(format)
                        metadata?.bitRate?.takeIf { it > 0L }?.let { FormatBadge("${it / 1000}K") }
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(
                        gap / 2,
                        if (coverVisible) Alignment.Top else Alignment.CenterVertically
                    )
                ) {
                    if (coverVisible) {
                        BoxWithConstraints(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = gap * 2),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                Modifier
                                    .size(minOf(maxWidth, maxHeight))
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("♪", fontSize = 28.sp, color = MaterialTheme.colorScheme.secondary)
                                metadata?.artworkUri?.let {
                                    AsyncImage(
                                        it,
                                        metadata.album ?: "Album cover",
                                        Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                    ScrollingTrackText(
                        metadata?.artist ?: "Browse your music",
                        fontSize = artistSize,
                        lineHeight = artistLineHeight,
                        height = artistHeight
                    )
                    ScrollingTrackText(
                        metadata?.title ?: "No track selected",
                        fontSize = titleSize,
                        lineHeight = titleLineHeight,
                        height = titleHeight
                    )
                    ScrollingTrackText(
                        metadata?.album ?: "",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        height = albumHeight,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(Modifier.height(gap / 2))
                WheelPlaybackMeters(playback, volume, maxVolume, gap, onSeek)
            }
        }
    }
}

@Composable
private fun FormatBadge(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.onBackground)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
