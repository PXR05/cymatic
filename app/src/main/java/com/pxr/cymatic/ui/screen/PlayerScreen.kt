package com.pxr.cymatic.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.pxr.cymatic.R
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.ui.components.player.Controls
import com.pxr.cymatic.ui.components.player.Header
import com.pxr.cymatic.ui.components.player.Info
import com.pxr.cymatic.ui.components.player.ProgressBar
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.rememberPlaybackState

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    audioFiles: List<AudioFile>
) {
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current
    if (mediaController == null) {
        navController.popBackStack()
        return
    }

    val playbackState = rememberPlaybackState(mediaController)
    val currentMediaId = playbackState.currentMediaId
    val metadata = (audioFiles.find { a -> a.id.toString() == currentMediaId })?.metadata
    if (currentMediaId == null || metadata == null) {
        navController.popBackStack()
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = modifier.padding(innerPadding),
            verticalArrangement = Arrangement.Top
        ) {
            Header(
                queueSource = "All Songs"
            )

            AsyncImage(
                model = metadata.artworkUri,
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                colorFilter = ColorMatrix().apply { setToSaturation(0f) }.let { ColorFilter.colorMatrix(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .aspectRatio(1f)
                    .weight(1f)
            )

            Info(
                metadata = metadata,
                modifier = Modifier.padding(32.dp, 24.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            ProgressBar(
                currentPosition = playbackState.currentPositionMs,
                durationMs = playbackState.durationMs ?: 0L,
                onSeek = { targetMs -> mediaController.seekTo(targetMs) },
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

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

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "${playbackState.currentIndex + 1} of ${playbackState.totalTracks}",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily(Font(R.font.pixel)),
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            )
        }
    }
}
