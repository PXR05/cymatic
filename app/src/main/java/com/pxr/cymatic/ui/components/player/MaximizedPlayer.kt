package com.pxr.cymatic.ui.components.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pxr.cymatic.playback.toAudioMetadata
import com.pxr.cymatic.ui.components.common.SwipeCarousel
import com.pxr.cymatic.ui.components.common.verticalFadingEdge
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.state.rememberPlaybackState
import com.pxr.cymatic.ui.theme.PixelCornerShape
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MaximizedPlayer(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    onClose: (() -> Unit)? = null
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

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()

    BackHandler(enabled = isActive) {
        if (pagerState.currentPage == 1) {
            scope.launch { pagerState.animateScrollToPage(0) }
        } else {
            onClose?.invoke()
        }
    }

    VerticalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .verticalFadingEdge(top = 48.dp, bottom = 48.dp)
            .background(Color.Transparent),
        beyondViewportPageCount = 1
    ) { page ->
        when (page) {
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                ) {
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
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp),
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
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(PixelCornerShape(cornerRadius = 36.dp))
                                    )
                                }
                            },
                            prevContent = {
                                if (prevItem != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
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
                                                .clip(PixelCornerShape(cornerRadius = 36.dp))
                                        )
                                    }
                                }
                            },
                            nextContent = {
                                if (nextItem != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
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
                                                .clip(PixelCornerShape(cornerRadius = 36.dp))
                                        )
                                    }
                                }
                            }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
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

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            Text(
                                text = "${playbackState.totalTracks} TRACKS IN QUEUE",
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 11.sp,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }
            }

            1 -> {
                val queueListState = rememberLazyListState()
                val nestedScrollConnection = remember(queueListState, pagerState, scope) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (available.y > 0f) {
                                val isAtTop = queueListState.firstVisibleItemIndex == 0 &&
                                    queueListState.firstVisibleItemScrollOffset == 0
                                if (isAtTop) {
                                    val consumed = pagerState.dispatchRawDelta(-available.y)
                                    return Offset(0f, -consumed)
                                }
                            }
                            return Offset.Zero
                        }

                        override suspend fun onPreFling(available: Velocity): Velocity {
                            if (available.y > 0f) {
                                val isAtTop = queueListState.firstVisibleItemIndex == 0 &&
                                    queueListState.firstVisibleItemScrollOffset == 0
                                if (isAtTop) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(0)
                                    }
                                    return available
                                }
                            }
                            return Velocity.Zero
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection)
                        .background(Color.Transparent)
                        .safeDrawingPadding()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    if (dragAmount > 12f) {
                                        scope.launch { pagerState.animateScrollToPage(0) }
                                    }
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "QUEUE · ${playbackState.totalTracks}",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 11.sp,
                            letterSpacing = 2.sp
                        )
                    }

                    var selectedQueueIndex by remember { mutableStateOf<Int?>(null) }
                    val haptic = LocalHapticFeedback.current

                    LazyColumn(
                        state = queueListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(count = mediaController.mediaItemCount, key = { it }) { index ->
                            val item = mediaController.getMediaItemAt(index)
                            val itemTitle = item.mediaMetadata.title?.toString() ?: "Unknown Title"
                            val itemArtist = item.mediaMetadata.artist?.toString() ?: ""
                            val isCurrent = index == playbackState.currentIndex

                            val itemShape = PixelCornerShape(cornerRadius = 16.dp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(itemShape)
                                    .background(
                                        if (isCurrent) {
                                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .combinedClickable(
                                        onClick = { mediaController.seekTo(index, 0L) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            selectedQueueIndex = index
                                        },
                                        indication = null,
                                        interactionSource = null
                                    )
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = (index + 1).toString().padStart(2, '0'),
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.width(28.dp)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = itemTitle,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (itemArtist.isNotBlank()) {
                                        Text(
                                            text = itemArtist,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.secondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    selectedQueueIndex?.let { selectedIdx ->
                        if (selectedIdx < mediaController.mediaItemCount) {
                            val selectedItem = mediaController.getMediaItemAt(selectedIdx)
                            QueueItemContextMenu(
                                index = selectedIdx,
                                totalCount = mediaController.mediaItemCount,
                                mediaItem = selectedItem,
                                isCurrent = selectedIdx == playbackState.currentIndex,
                                onDismiss = { selectedQueueIndex = null },
                                onPlay = { mediaController.seekTo(selectedIdx, 0L) },
                                onMoveUp = {
                                    if (selectedIdx > 0) {
                                        mediaController.moveMediaItem(selectedIdx, selectedIdx - 1)
                                    }
                                },
                                onMoveDown = {
                                    if (selectedIdx < mediaController.mediaItemCount - 1) {
                                        mediaController.moveMediaItem(selectedIdx, selectedIdx + 1)
                                    }
                                },
                                onMoveToTop = {
                                    if (selectedIdx > 0) {
                                        mediaController.moveMediaItem(selectedIdx, 0)
                                    }
                                },
                                onMoveToBottom = {
                                    if (selectedIdx < mediaController.mediaItemCount - 1) {
                                        mediaController.moveMediaItem(selectedIdx, mediaController.mediaItemCount - 1)
                                    }
                                },
                                onRemove = {
                                    if (selectedIdx < mediaController.mediaItemCount) {
                                        mediaController.removeMediaItem(selectedIdx)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
