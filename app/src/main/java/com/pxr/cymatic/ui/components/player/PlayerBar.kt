package com.pxr.cymatic.ui.components.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.pxr.cymatic.R
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.rememberPlaybackState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerBar(
    audioFiles: List<AudioFile>,
    modifier: Modifier = Modifier
) {
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current ?: return
    val playbackState = rememberPlaybackState(mediaController)
    val currentMediaId = playbackState.currentMediaId ?: return
    val metadata =
        audioFiles.find { audioFile -> audioFile.id.toString() == currentMediaId }?.metadata
            ?: return
    val fontFamily = FontFamily(Font(R.font.pixel))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    /* TODO: expand bar */
                },
                indication = null,
                interactionSource = null
            ),
        verticalArrangement = Arrangement.Top
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${playbackState.currentIndex + 1} of ${playbackState.totalTracks}",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 14.sp,
                    fontFamily = fontFamily,
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .clickable(
                            onClick = {
                                if (playbackState.queueSource != null) {
                                    navController.navigate(playbackState.queueSource)
                                }
                            },
                            indication = null,
                            interactionSource = null
                        )
                )

                FileInfo(
                    metadata = metadata,
                    modifier = Modifier.clickable(
                        onClick = { /* TODO: show detailed song info */ },
                        indication = null,
                        interactionSource = null
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Info(
                metadata,
                titleSize = 20.sp,
                artistSize = 14.sp,
                gap = 2.dp,
                modifier = Modifier
                    .height(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        ProgressBar(
            currentPosition = playbackState.currentPositionMs,
            durationMs = playbackState.durationMs ?: 0L,
            onSeek = { targetMs -> mediaController.seekTo(targetMs) },
            modifier = Modifier.padding(horizontal = 24.dp),
            showNumber = false,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Controls(
            isPlaying = playbackState.isPlaying,
            isShuffling = playbackState.isShuffling,
            repeatMode = playbackState.repeatMode,
            onClick = mapOf(
                "shuffle" to {
                    mediaController.shuffleModeEnabled = !playbackState.isShuffling
                },
                "previous" to { mediaController.seekToPrevious() },
                "play_pause" to {
                    if (playbackState.isPlaying) mediaController.pause()
                    else mediaController.play()
                },
                "next" to { mediaController.seekToNext() },
                "repeat" to {
                    val newMode = when (playbackState.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    mediaController.repeatMode = newMode
                }
            )
        )
    }
}