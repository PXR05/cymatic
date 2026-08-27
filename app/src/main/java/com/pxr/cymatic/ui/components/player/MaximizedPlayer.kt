package com.pxr.cymatic.ui.components.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pxr.cymatic.R
import com.pxr.cymatic.playback.toAudioMetadata
import com.pxr.cymatic.ui.components.common.SwipeCarousel
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.state.PlayerUiState
import com.pxr.cymatic.ui.state.rememberPlaybackState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaximizedPlayer(
    modifier: Modifier = Modifier
) {
    val mediaController = LocalMediaController.current ?: return
    val playbackState = rememberPlaybackState(mediaController)
    if (playbackState.currentMediaId == null) return
    val currentMediaItem = mediaController.currentMediaItem ?: return
    val metadata = currentMediaItem.toAudioMetadata()
    val title = metadata.title ?: "Unknown Title"
    val artist = metadata.artist ?: "Unknown Artist"

    val latestPlaybackState by rememberUpdatedState(playbackState)
    val latestController by rememberUpdatedState(mediaController)

    val hasNext = mediaController.hasNextMediaItem()
    val hasPrev = mediaController.hasPreviousMediaItem()
    val nextItem =
        if (hasNext) mediaController.getMediaItemAt(mediaController.nextMediaItemIndex) else null
    val prevItem =
        if (hasPrev) mediaController.getMediaItemAt(mediaController.previousMediaItemIndex) else null

    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded
        )
    )
    val queueAlpha by animateFloatAsState(
        targetValue = if (scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded) {
            1f
        } else {
            0f
        },
        animationSpec = tween(durationMillis = 200),
        label = "queueAlpha"
    )

    BackHandler {
        if (scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
            scope.launch { scaffoldState.bottomSheetState.partialExpand() }
        } else {
            PlayerUiState.isMaximized.value = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            containerColor = Color.Transparent,
            sheetContainerColor = MaterialTheme.colorScheme.background,
            sheetShape = RectangleShape,
            sheetDragHandle = null,
            sheetPeekHeight = 80.dp,
            sheetContent = {
                QueueSheetContent(
                    mediaController = mediaController,
                    currentIndex = playbackState.currentIndex,
                    totalTracks = playbackState.totalTracks,
                    listAlpha = queueAlpha,
                    onSelect = { index ->
                        mediaController.seekTo(index, 0L)
                    },
                    onClose = {
                        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                    }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .safeDrawingPadding()
                    .imePadding()
                    .padding(horizontal = 24.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pixel_arrow_down),
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                onClick = { PlayerUiState.isMaximized.value = false },
                                indication = null,
                                interactionSource = null
                            )
                            .padding(6.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 16.dp)
                ) {
                    SwipeCarousel(
                        modifier = Modifier.fillMaxWidth(),
                        hasPrev = hasPrev,
                        hasNext = hasNext,
                        onPrev = { latestController.seekToPrevious() },
                        onNext = { latestController.seekToNext() },
                        onTap = {
                            if (latestPlaybackState.isPlaying) {
                                latestController.pause()
                            } else {
                                latestController.play()
                            }
                        },
                        content = {
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
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                            )
                        },
                        prevContent = {
                            if (prevItem != null) {
                                AsyncImage(
                                    model = ImageRequest
                                        .Builder(LocalContext.current)
                                        .data(prevItem.mediaMetadata.artworkUri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    filterQuality = FilterQuality.High,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                )
                            }
                        },
                        nextContent = {
                            if (nextItem != null) {
                                AsyncImage(
                                    model = ImageRequest
                                        .Builder(LocalContext.current)
                                        .data(nextItem.mediaMetadata.artworkUri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    filterQuality = FilterQuality.High,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                )
                            }
                        }
                    )
                }

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
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(24.dp))

                ProgressBar(
                    currentPosition = playbackState.currentPositionMs,
                    durationMs = playbackState.durationMs ?: 0L,
                    onSeek = { mediaController.seekTo(it) },
                    showNumber = true
                )

                Spacer(modifier = Modifier.height(12.dp))

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

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
