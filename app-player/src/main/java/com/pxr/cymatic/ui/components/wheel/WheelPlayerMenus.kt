package com.pxr.cymatic.ui.components.wheel

import androidx.compose.runtime.Composable
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.pxr.cymatic.ui.components.common.AddToPlaylistDialog
import com.pxr.cymatic.ui.components.common.SongInfoDialog
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.navigation.Screen
import com.pxr.cymatic.ui.state.PlaybackState

@Composable
internal fun WheelPlayerMenu(
    state: WheelPlayerState,
    playback: PlaybackState,
    controller: MediaController?
) {
    val trackActions = state.panel == WheelPanel.TRACK_ACTIONS
    BaseScreen(
        title = if (trackActions) "Track actions" else "Quick settings",
        onBackClick = state::back
    ) {
        NavigationList(
            buildList {
                add(
                    NavigationItem(
                        "Shuffle",
                        subLabel = if (playback.isShuffling) "On" else "Off",
                        onClick = { controller?.shuffleModeEnabled = !playback.isShuffling }
                    )
                )
                add(
                    NavigationItem(
                        "Repeat",
                        subLabel = when (playback.repeatMode) {
                            Player.REPEAT_MODE_ONE -> "One track"
                            Player.REPEAT_MODE_ALL -> "All tracks"
                            else -> "Off"
                        },
                        onClick = { controller?.cycleRepeat() }
                    )
                )
                add(NavigationItem("Current Queue", onClick = { state.open(Screen.Queue.route) }))
                add(NavigationItem("Equalizer", onClick = { state.open(Screen.EQSettings.route) }))
                if (trackActions) {
                    add(NavigationItem("Add to Playlist", onClick = { state.showPlaylistPicker = true }))
                    add(NavigationItem("Track Info", onClick = { state.showTrackInfo = true }))
                }
                add(NavigationItem("Return", onClick = state::back))
            }
        )
    }
}

@Composable
internal fun WheelTrackDialogs(state: WheelPlayerState, mediaId: String?) {
    val id = mediaId?.toLongOrNull() ?: return
    if (state.showPlaylistPicker) {
        AddToPlaylistDialog(id, onDismiss = { state.showPlaylistPicker = false })
    }
    if (state.showTrackInfo) {
        SongInfoDialog(
            mediaId = id,
            showDialog = true,
            onDismissRequest = { state.showTrackInfo = false }
        )
    }
}

private fun MediaController.cycleRepeat() {
    repeatMode = when (repeatMode) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
    }
}
