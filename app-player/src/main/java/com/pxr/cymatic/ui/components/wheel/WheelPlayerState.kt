package com.pxr.cymatic.ui.components.wheel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.pxr.cymatic.ui.components.common.WheelNavigation
import com.pxr.cymatic.ui.navigation.Screen

internal const val MUSIC_BROWSER_ROUTE = "wheel_music"

internal enum class WheelPanel {
    BROWSER,
    NOW_PLAYING,
    QUICK_SETTINGS,
    TRACK_ACTIONS,
}

internal class WheelPlayerState(
    private val nav: NavHostController,
    val wheel: WheelNavigation,
    panel: WheelPanel = WheelPanel.BROWSER,
    previousPanel: WheelPanel = WheelPanel.BROWSER,
    coverVisible: Boolean = true,
    coverDefault: Boolean = true,
    browserRoute: String = MUSIC_BROWSER_ROUTE,
    browserRoot: String = MUSIC_BROWSER_ROUTE,
) {
    var panel by mutableStateOf(panel)
        private set

    var previousPanel by mutableStateOf(previousPanel)
        private set

    var coverVisible by mutableStateOf(coverVisible)
        private set

    var coverDefault by mutableStateOf(coverDefault)
        private set

    var browserRoute by mutableStateOf(browserRoute)
        private set

    var browserRoot by mutableStateOf(browserRoot)
        private set

    var showPlaylistPicker by mutableStateOf(false)
    var showTrackInfo by mutableStateOf(false)

    val isNowPlaying: Boolean
        get() = panel == WheelPanel.NOW_PLAYING

    val blocksBrowserInput: Boolean
        get() = panel != WheelPanel.BROWSER || wheel.overlay != null

    val controlsPlayback: Boolean
        get() = isNowPlaying && wheel.overlay == null

    fun rememberBrowser(entry: NavBackStackEntry?) {
        val route = entry?.musicBrowserRoute() ?: return
        browserRoute = route
        browserRoot =
            when (entry.destination.route) {
                Screen.Playlists.route,
                Screen.PlaylistSongs.route -> Screen.Playlists.route
                else -> MUSIC_BROWSER_ROUTE
            }
    }

    fun mainMenu() {
        if (
            nav.currentDestination?.route != Screen.Home.route &&
                !nav.popBackStack(Screen.Home.route, false, saveState = true)
        ) {
            nav.navigate(Screen.Home.route) {
                popUpTo(nav.graph.id) { saveState = true }
                launchSingleTop = true
            }
        }
        wheel.clearOverlays()
        showPlaylistPicker = false
        showTrackInfo = false
        panel = WheelPanel.BROWSER
    }

    fun back() {
        when {
            wheel.overlay != null -> wheel.overlay?.onDismiss?.invoke()
            panel == WheelPanel.QUICK_SETTINGS || panel == WheelPanel.TRACK_ACTIONS ->
                panel = previousPanel
            isNowPlaying -> panel = WheelPanel.BROWSER
            nav.currentDestination?.route == Screen.Home.route -> Unit
            nav.previousBackStackEntry == null -> mainMenu()
            else -> if (!nav.popBackStack()) mainMenu()
        }
    }

    fun nowPlaying() {
        panel = WheelPanel.NOW_PLAYING
    }

    fun browse() {
        panel = WheelPanel.BROWSER
        if (nav.currentBackStackEntry?.musicBrowserRoute() != null) return
        if (nav.popBackStack(browserRoute, false)) return
        nav.navigate(browserRoot) {
            launchSingleTop = true
            restoreState = true
        }
    }

    fun quickSettings() {
        previousPanel = panel
        panel = WheelPanel.QUICK_SETTINGS
    }

    fun trackActions() {
        previousPanel = panel
        panel = WheelPanel.TRACK_ACTIONS
    }

    fun open(route: String) {
        panel = WheelPanel.BROWSER
        nav.navigate(route) { launchSingleTop = true }
    }

    fun showBrowser() {
        panel = WheelPanel.BROWSER
    }

    fun toggleCover() {
        coverVisible = !coverVisible
    }

    fun updateCoverDefault(visible: Boolean) {
        if (coverDefault == visible) return
        coverDefault = visible
        coverVisible = visible
    }
}

@Composable
internal fun rememberWheelPlayerState(
    nav: NavHostController,
    wheel: WheelNavigation,
    coverDefault: Boolean,
): WheelPlayerState {
    val saver =
        remember(nav, wheel) {
            listSaver<WheelPlayerState, Any>(
                save = {
                    listOf(
                        it.panel.name,
                        it.previousPanel.name,
                        it.coverVisible,
                        it.browserRoute,
                        it.browserRoot,
                        it.coverDefault,
                    )
                },
                restore = {
                    WheelPlayerState(
                        nav = nav,
                        wheel = wheel,
                        panel = WheelPanel.valueOf(it[0] as String),
                        previousPanel = WheelPanel.valueOf(it[1] as String),
                        coverVisible = it[2] as Boolean,
                        coverDefault = it.getOrNull(5) as? Boolean ?: coverDefault,
                        browserRoute = it[3] as String,
                        browserRoot = it[4] as String,
                    )
                },
            )
        }
    val state =
        rememberSaveable(nav, wheel, saver = saver) {
            WheelPlayerState(nav, wheel, coverVisible = coverDefault, coverDefault = coverDefault)
        }
    LaunchedEffect(coverDefault) { state.updateCoverDefault(coverDefault) }
    val entry by nav.currentBackStackEntryAsState()
    LaunchedEffect(entry) { state.rememberBrowser(entry) }
    return state
}

private fun NavBackStackEntry.musicBrowserRoute(): String? {
    val artist = arguments?.getString("artistName")
    val album = arguments?.getString("albumName")
    val scrollId = arguments?.getString("scrollId")?.toLongOrNull()
    return when (destination.route) {
        MUSIC_BROWSER_ROUTE -> MUSIC_BROWSER_ROUTE
        Screen.AllSongs.route -> Screen.AllSongs.createRoute(scrollId)
        Screen.Artists.route -> Screen.Artists.route
        Screen.ArtistAlbums.route -> artist?.let { Screen.ArtistAlbums.createRoute(it) }
        Screen.ArtistSongs.route -> artist?.let { Screen.ArtistSongs.createRoute(it, scrollId) }
        Screen.ArtistAlbumSongs.route ->
            if (artist != null && album != null) {
                Screen.ArtistAlbumSongs.createRoute(artist, album, scrollId)
            } else {
                null
            }
        Screen.Albums.route -> Screen.Albums.route
        Screen.AlbumSongs.route -> album?.let { Screen.AlbumSongs.createRoute(it, scrollId) }
        Screen.Playlists.route -> Screen.Playlists.route
        Screen.PlaylistSongs.route ->
            arguments?.getString("playlistId")?.toLongOrNull()?.let {
                Screen.PlaylistSongs.createRoute(it, scrollId)
            }
        else -> null
    }
}
