package com.pxr.cymatic.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.pxr.cymatic.R
import com.pxr.cymatic.playback.toAudioMetadata
import com.pxr.cymatic.ui.components.common.SwipeCarousel
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.state.rememberPlaybackState

@Composable
fun FullPlayer(modifier: Modifier = Modifier, onMaximize: (() -> Unit)? = null) {
    val mediaController = LocalMediaController.current ?: return
    val playbackState = rememberPlaybackState(mediaController)
    if (playbackState.currentMediaId == null) return
    val currentMediaItem = mediaController.currentMediaItem ?: return
    val metadata = currentMediaItem.toAudioMetadata()
    val title = metadata.title ?: "Unknown Title"
    val artist = metadata.artist ?: "Unknown Artist"
    val durationMs = playbackState.durationMs ?: 0L
    val progressFraction = if (durationMs > 0L) {
        (playbackState.currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val latestController by rememberUpdatedState(mediaController)

    val hasNext = mediaController.hasNextMediaItem()
    val hasPrev = mediaController.hasPreviousMediaItem()
    val nextItem =
        if (hasNext) mediaController.getMediaItemAt(mediaController.nextMediaItemIndex) else null
    val prevItem =
        if (hasPrev) mediaController.getMediaItemAt(mediaController.previousMediaItemIndex) else null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 28.dp)
    ) {
        Column {
            Text(
                text = "NOW PLAYING · ${playbackState.currentIndex + 1}/${playbackState.totalTracks}",
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 10.sp,
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                SwipeCarousel(
                    modifier = Modifier.weight(1f),
                    hasPrev = hasPrev,
                    hasNext = hasNext,
                    onPrev = { latestController.seekToPrevious() },
                    onNext = { latestController.seekToNext() },
                    onTap = {
                        onMaximize?.invoke()
                    },
                    content = { TrackText(title, artist) },
                    prevContent = {
                        if (prevItem != null) {
                            TrackText(
                                prevItem.mediaMetadata.title?.toString() ?: "Unknown Title",
                                prevItem.mediaMetadata.artist?.toString() ?: ""
                            )
                        }
                    },
                    nextContent = {
                        if (nextItem != null) {
                            TrackText(
                                nextItem.mediaMetadata.title?.toString() ?: "Unknown Title",
                                nextItem.mediaMetadata.artist?.toString() ?: ""
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                Icon(
                    painter = painterResource(
                        if (playbackState.isPlaying) R.drawable.ic_pixel_pause else R.drawable.ic_pixel_play
                    ),
                    contentDescription = "Play/Pause",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(
                            onClick = {
                                if (playbackState.isPlaying) {
                                    mediaController.pause()
                                } else {
                                    mediaController.play()
                                }
                            },
                            indication = null,
                            interactionSource = null
                        )
                        .padding(6.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.onBackground)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun TrackText(title: String, artist: String) {
    Column {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = artist,
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
