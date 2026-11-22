package com.pxr.cymatic.ui.components.wheel

import androidx.compose.runtime.Composable
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.pxr.cymatic.R
import com.pxr.cymatic.playback.cycleRepeat
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.pixelIcon
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.navigation.Screen
import com.pxr.cymatic.ui.state.PlaybackState

@Composable
internal fun WheelPlayerMenu(
    state: WheelPlayerState,
    playback: PlaybackState,
    controller: MediaController?,
    trackActions: Boolean = state.panel == WheelPanel.TRACK_ACTIONS,
) {
    BaseScreen(
        title = if (trackActions) "Track actions" else "Quick settings",
        onBackClick = state::back,
    ) {
        NavigationList(
            buildList {
                add(
                    NavigationItem(
                        "Shuffle",
                        subLabel = if (playback.isShuffling) "On" else "Off",
                        icon = pixelIcon(R.drawable.ic_pixel_shuffle),
                        checked = playback.isShuffling,
                        onClick = { controller?.shuffleModeEnabled = !playback.isShuffling },
                    )
                )
                add(
                    NavigationItem(
                        "Repeat",
                        icon = pixelIcon(R.drawable.ic_pixel_repeat),
                        checked = playback.repeatMode != Player.REPEAT_MODE_OFF,
                        subLabel =
                            when (playback.repeatMode) {
                                Player.REPEAT_MODE_ONE -> "One track"
                                Player.REPEAT_MODE_ALL -> "All tracks"
                                else -> "Off"
                            },
                        onClick = { controller?.cycleRepeat() },
                    )
                )
                add(
                    NavigationItem(
                        "Current Queue",
                        icon = pixelIcon(R.drawable.ic_pixel_queue),
                        onClick = { state.open(Screen.Queue.route) },
                    )
                )
                add(
                    NavigationItem(
                        "Equalizer",
                        icon = pixelIcon(R.drawable.ic_pixel_equalizer),
                        onClick = { state.open(Screen.EQSettings.route) },
                    )
                )
                if (trackActions) {
                    add(
                        NavigationItem(
                            "Add to Playlist",
                            icon = pixelIcon(R.drawable.ic_pixel_add),
                            enabled = playback.currentMediaId?.toLongOrNull() != null,
                            onClick = {
                                playback.currentMediaId?.toLongOrNull()?.let {
                                    state.open(Screen.AddToPlaylist.createRoute(it))
                                }
                            },
                        )
                    )
                    add(
                        NavigationItem(
                            "Track Info",
                            icon = pixelIcon(R.drawable.ic_pixel_info),
                            enabled = playback.currentMediaId?.toLongOrNull() != null,
                            onClick = {
                                playback.currentMediaId?.toLongOrNull()?.let {
                                    state.open(Screen.SongInfo.createRoute(it))
                                }
                            },
                        )
                    )
                    add(
                        NavigationItem(
                            "Output info",
                            icon = pixelIcon(R.drawable.ic_pixel_speaker),
                            onClick = { state.open(Screen.OutputInfo.route) },
                        )
                    )
                }
                add(
                    NavigationItem(
                        "Return",
                        icon = pixelIcon(R.drawable.ic_pixel_back),
                        onClick = state::back,
                    )
                )
            }
        )
    }
}
