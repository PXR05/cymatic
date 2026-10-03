package com.pxr.cymatic.ui.components.wheel

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.MusicNavHost
import com.pxr.cymatic.ui.navigation.Screen
import com.pxr.cymatic.ui.screens.settings.INTERFACE_SETTINGS_ROUTE
import com.pxr.cymatic.ui.screens.settings.InterfaceSettingsScreen
import com.pxr.cymatic.ui.screens.settings.ReleaseProduct

@Composable
internal fun WheelBrowser(state: WheelPlayerState) {
    MusicNavHost(
        releaseProduct = ReleaseProduct.PLAYER,
        modifier = if (state.blocksBrowserInput) Modifier.clearAndSetSemantics {} else Modifier,
        home = { MainMenu(state) },
        settingsItems = listOf(NavigationItem("Interface") { state.open(INTERFACE_SETTINGS_ROUTE) }),
        additionalRoutes = mapOf(
            MUSIC_BROWSER_ROUTE to { MusicMenu(state) },
            Screen.Queue.route to { WheelQueueScreen(onNowPlaying = state::nowPlaying) },
            INTERFACE_SETTINGS_ROUTE to { InterfaceSettingsScreen() }
        )
    )
    BackHandler(onBack = state::back)
}

@Composable
private fun MainMenu(state: WheelPlayerState) {
    BaseScreen(title = "Main menu") {
        NavigationList(
            listOf(
                NavigationItem("Now Playing", onClick = state::nowPlaying),
                NavigationItem("Music", onClick = { state.open(MUSIC_BROWSER_ROUTE) }),
                NavigationItem("Playlists", onClick = { state.open(Screen.Playlists.route) }),
                NavigationItem("Current Queue", onClick = { state.open(Screen.Queue.route) }),
                NavigationItem("Settings", onClick = { state.open(Screen.Settings.route) })
            )
        )
    }
}

@Composable
private fun MusicMenu(state: WheelPlayerState) {
    val nav = LocalNavController.current
    BaseScreen(title = "Music", onBackClick = { nav.popBackStack() }) {
        NavigationList(
            listOf(
                NavigationItem(
                    "All Songs",
                    onClick = { state.open(Screen.AllSongs.createRoute()) }),
                NavigationItem("Artists", onClick = { state.open(Screen.Artists.route) }),
                NavigationItem("Albums", onClick = { state.open(Screen.Albums.route) })
            )
        )
    }
}
