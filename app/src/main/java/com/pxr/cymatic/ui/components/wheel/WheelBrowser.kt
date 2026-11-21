package com.pxr.cymatic.ui.components.wheel

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.navigation.MusicNavHost
import com.pxr.cymatic.ui.navigation.Screen
import com.pxr.cymatic.ui.screens.settings.InterfaceSettingsScreen
import com.pxr.cymatic.ui.state.PlaybackState

@Composable
internal fun WheelBrowser(
    state: WheelPlayerState,
    playback: PlaybackState,
    volume: Int,
    maxVolume: Int,
    overlay: @Composable () -> Unit,
) {
    val controller = LocalMediaController.current
    BackHandler(onBack = state::back)
    MusicNavHost(
        footer = {
            WheelPlaybackBar(
                title = controller?.currentMediaItem?.mediaMetadata?.title?.toString(),
                isPlaying = playback.isPlaying,
                onNowPlaying = state::nowPlaying,
            )
        },
        overlay = overlay,
        contentModifier =
            if (state.blocksBrowserInput) Modifier.clearAndSetSemantics {} else Modifier,
        home = { MainMenu(state) },
        settingsItems =
            listOf(NavigationItem("Interface") { state.open(Screen.InterfaceSettings.route) }),
        additionalRoutes =
            mapOf(
                Screen.NowPlaying.route to
                        {
                            WheelNowPlaying(
                                playback,
                                volume,
                                maxVolume,
                                state.coverVisible,
                                onToggleCover = state::toggleCover,
                                onSeek = { controller?.seekTo(it) },
                                onSeekStart = { state.beginSeek(controller) },
                                onSeekEnd = { state.endSeek(controller) },
                            )
                        },
                Screen.QuickSettings.route to
                        {
                            WheelPlayerMenu(state, playback, controller, trackActions = false)
                        },
                Screen.TrackActions.route to
                        {
                            WheelPlayerMenu(state, playback, controller, trackActions = true)
                        },
                Screen.Queue.route to { WheelQueueScreen(onNowPlaying = state::nowPlaying) },
                Screen.InterfaceSettings.route to { InterfaceSettingsScreen() },
                Screen.TextScrollingSettings.route to
                        {
                            InterfaceSettingsScreen(Screen.TextScrollingSettings.route)
                        },
            ),
    )
}

@Composable
private fun MainMenu(state: WheelPlayerState) {
    BaseScreen(title = "Main menu") {
        NavigationList(
            listOf(
                NavigationItem("Now Playing", onClick = state::nowPlaying),
                NavigationItem(
                    "All Songs",
                    onClick = { state.open(Screen.AllSongs.createRoute()) },
                ),
                NavigationItem("Artists", onClick = { state.open(Screen.Artists.route) }),
                NavigationItem("Albums", onClick = { state.open(Screen.Albums.route) }),
                NavigationItem("Playlists", onClick = { state.open(Screen.Playlists.route) }),
                NavigationItem("Current Queue", onClick = { state.open(Screen.Queue.route) }),
                NavigationItem("Settings", onClick = { state.open(Screen.Settings.route) }),
            )
        )
    }
}
