package com.pxr.cymatic.ui.components.wheel

import androidx.compose.runtime.Composable
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.pxr.cymatic.playback.cycleRepeat
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.navigation.Screen
import com.pxr.cymatic.ui.state.PlaybackState

@Composable
internal fun WheelPlayerMenu(
    state: WheelPlayerState,
    playback: PlaybackState,
    controller: MediaController?,
) {
    val trackActions = state.panel == WheelPanel.TRACK_ACTIONS
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
                        checked = playback.isShuffling,
                        onClick = { controller?.shuffleModeEnabled = !playback.isShuffling },
                    )
                )
                add(
                    NavigationItem(
                        "Repeat",
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
                add(NavigationItem("Current Queue", onClick = { state.open(Screen.Queue.route) }))
                add(NavigationItem("Equalizer", onClick = { state.open(Screen.EQSettings.route) }))
                if (trackActions) {
                    add(
                        NavigationItem(
                            "Add to Playlist",
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
                            onClick = { state.open(Screen.OutputInfo.route) },
                        )
                    )
                }
                add(NavigationItem("Return", onClick = state::back))
            }
        )
    }
}
