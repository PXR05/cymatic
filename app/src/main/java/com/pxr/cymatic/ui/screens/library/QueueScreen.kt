package com.pxr.cymatic.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.pxr.cymatic.R
import com.pxr.cymatic.ui.components.common.EmptyState
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController

@Composable
fun QueueScreen(
    modifier: Modifier = Modifier
) {
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current

    var queueItems by remember { mutableStateOf(emptyList<MediaItem>()) }
    var currentIndex by remember { mutableIntStateOf(mediaController?.currentMediaItemIndex ?: -1) }
    var currentId by remember { mutableStateOf(mediaController?.currentMediaItem?.mediaId ?: "") }

    fun updateQueue() {
        mediaController?.let { controller ->
            val items = mutableListOf<MediaItem>()
            for (i in 0 until controller.mediaItemCount) {
                items.add(controller.getMediaItemAt(i))
            }
            queueItems = items
            currentIndex = controller.currentMediaItemIndex
        }
    }

    DisposableEffect(mediaController) {
        if (mediaController == null) return@DisposableEffect onDispose { }

        updateQueue()

        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                updateQueue()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = mediaController.currentMediaItemIndex
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                ) {
                    updateQueue()
                }
            }
        }

        mediaController.addListener(listener)
        onDispose {
            mediaController.removeListener(listener)
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(currentId) {
        if (currentIndex >= 0 && currentIndex < queueItems.size) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    BaseScreen(
        title = "Queue",
        onBackClick = { navController.popBackStack() },
        modifier = modifier,
        actions = {
            if (queueItems.isNotEmpty()) {
                Text(
                    text = "CLEAR",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(
                            onClick = { mediaController?.clearMediaItems() },
                            indication = null,
                            interactionSource = null
                        )
                        .padding(vertical = 16.dp, horizontal = 24.dp)
                )
            }
        }
    ) {
        if (queueItems.isEmpty()) {
            EmptyState(
                title = "Queue is Empty",
                message = "Cymatic has no tracks in the queue. Scanned music will appear here.",
                iconText = "( ! )",
                actionLabel = "GO TO SONGS",
                onActionClick = { navController.navigate(com.pxr.cymatic.ui.navigation.Screen.AllSongs.createRoute()) }
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    count = queueItems.size,
                    key = { i -> "${queueItems[i].mediaId}_$i" }
                ) { i ->
                    val item = queueItems[i]
                    val isActive = i == currentIndex
                    val isFirst = i == 0
                    val isLast = i == queueItems.size - 1

                    QueueItemRow(
                        index = i,
                        title = item.mediaMetadata.title?.toString() ?: "Unknown Title",
                        artist = item.mediaMetadata.artist?.toString() ?: "Unknown Artist",
                        isActive = isActive,
                        isFirst = isFirst,
                        isLast = isLast,
                        onPlay = {
                            mediaController?.seekTo(i, 0L)
                            mediaController?.play()
                        },
                        onMoveUp = {
                            if (i > 0) {
                                mediaController?.moveMediaItem(i, i - 1)
                            }
                        },
                        onMoveDown = {
                            if (i < queueItems.size - 1) {
                                mediaController?.moveMediaItem(i, i + 1)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueItemRow(
    index: Int,
    title: String,
    artist: String?,
    isActive: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onPlay: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val cjkRegex = Regex("[\\u4E00-\\u9FFF|\\u3040-\\u309F\\u30A0-\\u30FF\\uAC00-\\uD7AF]")
    val isLabelCJK = title.contains(cjkRegex)
    val isSubLabelCJK = artist?.contains(cjkRegex) ?: false
    val labelColor =
        if (isActive) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground
    val secondaryLabelColor =
        if (isActive) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.secondary
    val labelFontStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 20.sp,
        letterSpacing = if (isLabelCJK) 2.sp else 0.sp
    )
    val subLabelFontStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 14.sp,
        letterSpacing = if (isSubLabelCJK) 1.5.sp else 0.sp
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) MaterialTheme.colorScheme.onBackground else Color.Transparent)
            .clickable(
                onClick = onPlay,
                indication = null,
                interactionSource = null
            )
            .padding(start = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 16.dp, bottom = 14.5.dp)
        ) {
            Text(
                text = title,
                color = labelColor,
                style = labelFontStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!artist.isNullOrEmpty()) {
                Text(
                    text = artist,
                    color = secondaryLabelColor,
                    style = subLabelFontStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!isFirst) {
                Icon(
                    painter = painterResource(R.drawable.ic_pixel_arrow_up),
                    contentDescription = "Move Up",
                    tint = secondaryLabelColor,
                    modifier = Modifier
                        .clickable(
                            onClick = onMoveUp,
                            indication = null,
                            interactionSource = null
                        )
                        .padding(top = 10.dp, bottom = 4.dp, start = 24.dp, end = 24.dp)
                        .size(24.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(24.dp))
            }

            if (!isLast) {
                Icon(
                    painter = painterResource(R.drawable.ic_pixel_arrow_down),
                    contentDescription = "Move Down",
                    tint = secondaryLabelColor,
                    modifier = Modifier
                        .clickable(
                            onClick = onMoveDown,
                            indication = null,
                            interactionSource = null
                        )
                        .padding(bottom = 10.dp, top = 4.dp, start = 24.dp, end = 24.dp)
                        .size(24.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(24.dp))
            }
        }
    }
}
