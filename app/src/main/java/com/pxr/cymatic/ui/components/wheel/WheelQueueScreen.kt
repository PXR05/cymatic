package com.pxr.cymatic.ui.components.wheel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pxr.cymatic.R
import com.pxr.cymatic.ui.components.common.EmptyState
import com.pxr.cymatic.ui.components.common.WheelContextMenu
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.pixelIcon
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalInterfaceSettings
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.state.rememberPlaybackState

@Composable
internal fun WheelQueueScreen(onNowPlaying: () -> Unit) {
    val controller = LocalMediaController.current
    val nav = LocalNavController.current
    val playback = rememberPlaybackState(controller)
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    BaseScreen(title = "Current queue", onBackClick = { nav.popBackStack() }) {
        if (playback.queue.items.isEmpty()) {
            EmptyState(
                title = "Queue is empty",
                message = "Choose a song from your library or playlists.",
            )
        } else {
            val showIcons = LocalInterfaceSettings.current.showIcons
            NavigationList(
                playback.queue.items.mapIndexed { index, entry ->
                    val metadata = entry.mediaItem.mediaMetadata
                    val isCurrent = index == playback.queue.currentIndex
                    val marker = if (isCurrent && !showIcons) "▶ " else ""
                    val title = metadata.title ?: "Unknown Title"
                    NavigationItem(
                        label = "$marker${index + 1}. $title",
                        subLabel = metadata.artist?.toString(),
                        icon = if (isCurrent) pixelIcon(R.drawable.ic_pixel_play) else null,
                        subLabelMaxLines = 1,
                        showIndicator = false,
                        onClick = {
                            controller?.seekTo(entry.mediaItemIndex, 0L)
                            controller?.play()
                            onNowPlaying()
                        },
                        onLongClick = { selectedIndex = entry.mediaItemIndex },
                    )
                }
            )
        }
    }
    selectedIndex?.let { index ->
        val entry = playback.queue.items.firstOrNull { it.mediaItemIndex == index }
        if (entry != null) {
            WheelContextMenu(
                entry.mediaItem.mediaMetadata.title?.toString() ?: "Track actions",
                buildList {
                    add(
                        NavigationItem(
                            "Play now",
                            icon = pixelIcon(R.drawable.ic_pixel_play),
                            onClick = {
                                controller?.seekTo(index, 0L)
                                controller?.play()
                                onNowPlaying()
                            },
                        )
                    )
                    if (!playback.isShuffling) {
                        if (index > 0) {
                            add(
                                NavigationItem(
                                    "Move up",
                                    icon = pixelIcon(R.drawable.ic_pixel_arrow_up),
                                    onClick = {
                                        controller?.moveMediaItem(index, index - 1)
                                        selectedIndex = null
                                    },
                                )
                            )
                        }
                        if (index < playback.totalTracks - 1) {
                            add(
                                NavigationItem(
                                    "Move down",
                                    icon = pixelIcon(R.drawable.ic_pixel_arrow_down),
                                    onClick = {
                                        controller?.moveMediaItem(index, index + 1)
                                        selectedIndex = null
                                    },
                                )
                            )
                        }
                    }
                    add(
                        NavigationItem(
                            "Remove from queue",
                            icon = pixelIcon(R.drawable.ic_pixel_delete),
                            onClick = {
                                controller?.removeMediaItem(index)
                                selectedIndex = null
                            },
                        )
                    )
                    add(
                        NavigationItem(
                            "Clear queue",
                            icon = pixelIcon(R.drawable.ic_pixel_close),
                            onClick = {
                                controller?.clearMediaItems()
                                selectedIndex = null
                            },
                        )
                    )
                },
                onDismiss = { selectedIndex = null },
            )
        }
    }
}
