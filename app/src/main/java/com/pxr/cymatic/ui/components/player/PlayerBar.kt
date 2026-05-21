package com.pxr.cymatic.ui.components.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pxr.cymatic.R
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.components.common.SongInfoDialog
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.state.rememberPlaybackState
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerBar(
    audioFiles: List<AudioFile>,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current ?: return
    val playbackState = rememberPlaybackState(mediaController)
    val currentMediaId = playbackState.currentMediaId ?: return
    val metadata =
        audioFiles.find { audioFile -> audioFile.id.toString() == currentMediaId }?.metadata
            ?: return
    val fontFamily = FontFamily(Font(R.font.pixel))
    val locked by SettingsStore.lockedFlow.collectAsState(initial = SettingsStore.currentLocked)
    val baseGap = 16.dp

    val hideArtwork by SettingsStore.hideArtworkFlow.collectAsState(initial = SettingsStore.currentHideArtwork)
    var showInfoDialog by remember { mutableStateOf(false) }

    SongInfoDialog(
        mediaId = currentMediaId.toLong(),
        showDialog = showInfoDialog,
        onDismissRequest = { showInfoDialog = false },
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onDoubleClick = {
                    if (!locked) return@combinedClickable
                    val newValue = !hideArtwork
                    scope.launch {
                        SettingsStore.setHideArtwork(newValue)
                    }
                    haptic.performHapticFeedback(
                        if (newValue) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                    )
                },
                onLongClick = {
                    scope.launch {
                        SettingsStore.setLocked(!SettingsStore.isLocked())
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                },
                indication = null,
                interactionSource = null
            ),
    ) {
        if (locked) {
            AsyncImage(
                model = ImageRequest
                    .Builder(LocalContext.current)
                    .data(metadata.artworkUri)
                    .crossfade(true)
                    .build(),
                contentDescription = metadata.album,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(32.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                            ),
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest
                        .Builder(LocalContext.current)
                        .data(metadata.artworkUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = metadata.album,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, bottom = 92.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
            }

            if (hideArtwork) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }
        }

        Column(
            modifier = Modifier
                .then(
                    if (locked) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.fillMaxWidth()
                    }
                ),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
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
                                if (playbackState.queueSource != null && !locked) {
                                    navController.navigate(
                                        playbackState.queueSource + "?scrollId=${playbackState.currentMediaId}"
                                    ) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            indication = null,
                            interactionSource = null
                        )
                )

                FileInfo(
                    metadata = metadata,
                    modifier = Modifier.clickable(
                        onClick = {
                            if (!locked) {
                                showInfoDialog = true
                            }
                        },
                        indication = null,
                        interactionSource = null
                    )
                )
            }

            Spacer(modifier = Modifier.height(baseGap))

            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Info(
                    metadata,
                    titleSize = 20.sp,
                    artistSize = 14.sp,
                    gap = 2.dp,
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f)
                )
                if (locked) {
                    if (playbackState.isShuffling || playbackState.repeatMode != Player.REPEAT_MODE_OFF) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (playbackState.isShuffling) {
                        Text(
                            text = "SHUF",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 14.sp,
                            fontFamily = fontFamily,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    if (playbackState.repeatMode != Player.REPEAT_MODE_OFF) {
                        Text(
                            text = when (playbackState.repeatMode) {
                                Player.REPEAT_MODE_ALL -> "ALL"
                                Player.REPEAT_MODE_ONE -> "ONE"
                                else -> ""
                            },
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 14.sp,
                            fontFamily = fontFamily,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(baseGap * 1.5f))

            ProgressBar(
                currentPosition = playbackState.currentPositionMs,
                durationMs = playbackState.durationMs ?: 0L,
                onSeek = { targetMs ->
                    if (locked) return@ProgressBar
                    mediaController.seekTo(targetMs)
                },
                seekEnabled = !locked,
                modifier = Modifier.padding(horizontal = 24.dp),
                showNumber = false,
            )

            Spacer(modifier = Modifier.height(baseGap))

            if (!locked) {
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

            Spacer(modifier = Modifier.height(baseGap * 1f))
        }
    }
}