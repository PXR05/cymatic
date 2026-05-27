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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.model.AudioMetadata
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.components.common.SongInfoDialog
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen
import com.pxr.cymatic.ui.state.rememberPlaybackState
import com.pxr.cymatic.playback.toAudioMetadata
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerBar(
    modifier: Modifier = Modifier,
    isDocked: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current ?: return
    val playbackState = rememberPlaybackState(mediaController)
    val currentMediaId = playbackState.currentMediaId ?: return
    val currentMediaItem = mediaController.currentMediaItem ?: return
    val metadata = currentMediaItem.toAudioMetadata()
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
                    if (!locked && !isDocked) return@combinedClickable
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
        if (locked || isDocked) {
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
                    .then(
                        if (isDocked) {
                            Modifier.fillMaxWidth().aspectRatio(1f)
                        } else {
                            Modifier.fillMaxHeight()
                        }
                    )
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

            if (locked && !isDocked) {
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
            }

            if (hideArtwork) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }
        }

        if (isDocked) {
            val layoutDirection = LocalLayoutDirection.current
            val safeDrawingPaddingValues = WindowInsets.safeDrawing.asPaddingValues()
            val leftPadding = safeDrawingPaddingValues.calculateLeftPadding(layoutDirection)
            val rightPadding = safeDrawingPaddingValues.calculateRightPadding(layoutDirection)
            val maxHorizontalPadding = maxOf(leftPadding, rightPadding)
            val topPadding = safeDrawingPaddingValues.calculateTopPadding()
            val bottomPadding = safeDrawingPaddingValues.calculateBottomPadding()

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = maxHorizontalPadding + 48.dp,
                        end = maxHorizontalPadding + 48.dp,
                        top = topPadding + 48.dp,
                        bottom = bottomPadding + 48.dp
                    ),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(48.dp)
            ) {
                if (!hideArtwork) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
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
                                .fillMaxSize()
                                .aspectRatio(1f)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(if (hideArtwork) 1f else 1.2f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TrackQueueIndex(
                            currentIndex = playbackState.currentIndex,
                            totalTracks = playbackState.totalTracks,
                            queueSource = playbackState.queueSource,
                            currentMediaId = playbackState.currentMediaId,
                            locked = locked,
                            navController = navController
                        )

                        TrackFileInfo(
                            metadata = metadata,
                            locked = locked,
                            onShowInfo = { showInfoDialog = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TrackInfoAndStatus(
                        metadata = metadata,
                        isShuffling = playbackState.isShuffling,
                        repeatMode = playbackState.repeatMode,
                        locked = locked,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TrackProgressBar(
                        currentPosition = playbackState.currentPositionMs,
                        durationMs = playbackState.durationMs ?: 0L,
                        locked = locked,
                        onSeek = { mediaController.seekTo(it) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TrackControls(
                        isPlaying = playbackState.isPlaying,
                        isShuffling = playbackState.isShuffling,
                        repeatMode = playbackState.repeatMode,
                        locked = locked,
                        mediaController = mediaController
                    )
                }
            }
        } else {
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
                    TrackQueueIndex(
                        currentIndex = playbackState.currentIndex,
                        totalTracks = playbackState.totalTracks,
                        queueSource = playbackState.queueSource,
                        currentMediaId = playbackState.currentMediaId,
                        locked = locked,
                        navController = navController
                    )

                    TrackFileInfo(
                        metadata = metadata,
                        locked = locked,
                        onShowInfo = { showInfoDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(baseGap))

                TrackInfoAndStatus(
                    metadata = metadata,
                    isShuffling = playbackState.isShuffling,
                    repeatMode = playbackState.repeatMode,
                    locked = locked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(baseGap * 1.5f))

                TrackProgressBar(
                    currentPosition = playbackState.currentPositionMs,
                    durationMs = playbackState.durationMs ?: 0L,
                    locked = locked,
                    onSeek = { mediaController.seekTo(it) },
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(baseGap))

                TrackControls(
                    isPlaying = playbackState.isPlaying,
                    isShuffling = playbackState.isShuffling,
                    repeatMode = playbackState.repeatMode,
                    locked = locked,
                    mediaController = mediaController
                )

                Spacer(modifier = Modifier.height(baseGap * 1f))
            }
        }
    }
}

@Composable
private fun TrackQueueIndex(
    currentIndex: Int,
    totalTracks: Int,
    queueSource: String?,
    currentMediaId: String?,
    locked: Boolean,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    Text(
        text = "${currentIndex + 1} of $totalTracks",
        color = MaterialTheme.colorScheme.secondary,
        fontSize = 14.sp,
        modifier = modifier
            .padding(vertical = 2.dp)
            .clickable(
                onClick = {
                    if (!locked) {
                        navController.navigate(Screen.Queue.route) {
                            launchSingleTop = true
                        }
                    }
                },
                indication = null,
                interactionSource = null
            )
    )
}

@Composable
private fun TrackFileInfo(
    metadata: AudioMetadata,
    locked: Boolean,
    onShowInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    FileInfo(
        metadata = metadata,
        modifier = modifier.clickable(
            onClick = {
                if (!locked) {
                    onShowInfo()
                }
            },
            indication = null,
            interactionSource = null
        )
    )
}

@Composable
private fun TrackInfoAndStatus(
    metadata: AudioMetadata,
    isShuffling: Boolean,
    repeatMode: Int,
    locked: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
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
            if (isShuffling || repeatMode != Player.REPEAT_MODE_OFF) {
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (isShuffling) {
                Text(
                    text = "SHUF",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (repeatMode != Player.REPEAT_MODE_OFF) {
                Text(
                    text = when (repeatMode) {
                        Player.REPEAT_MODE_ALL -> "ALL"
                        Player.REPEAT_MODE_ONE -> "ONE"
                        else -> ""
                    },
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun TrackProgressBar(
    currentPosition: Long,
    durationMs: Long,
    locked: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    ProgressBar(
        currentPosition = currentPosition,
        durationMs = durationMs,
        onSeek = { targetMs ->
            if (locked) return@ProgressBar
            onSeek(targetMs)
        },
        seekEnabled = !locked,
        modifier = modifier,
        showNumber = false,
    )
}

@Composable
private fun TrackControls(
    isPlaying: Boolean,
    isShuffling: Boolean,
    repeatMode: Int,
    locked: Boolean,
    mediaController: MediaController
) {
    if (!locked) {
        Controls(
            isPlaying = isPlaying,
            isShuffling = isShuffling,
            repeatMode = repeatMode,
            onClick = mapOf(
                "shuffle" to {
                    mediaController.shuffleModeEnabled = !isShuffling
                },
                "previous" to { mediaController.seekToPrevious() },
                "play_pause" to {
                    if (isPlaying) mediaController.pause()
                    else mediaController.play()
                },
                "next" to { mediaController.seekToNext() },
                "repeat" to {
                    val newMode = when (repeatMode) {
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