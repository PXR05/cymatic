package com.pxr.cymatic.ui.components.wheel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.media3.session.MediaController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.currentBackStackEntryAsState
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.data.store.WheelPageState
import com.pxr.cymatic.data.store.currentWheelPageState
import com.pxr.cymatic.data.store.setWheelPageState
import com.pxr.cymatic.data.store.wheelPageStateFlow
import com.pxr.cymatic.ui.components.common.WheelNavigation
import com.pxr.cymatic.ui.navigation.Screen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

internal const val MUSIC_BROWSER_ROUTE = "wheel_music"

internal enum class WheelPanel {
    BROWSER,
    NOW_PLAYING,
    QUICK_SETTINGS,
    TRACK_ACTIONS,
}

internal class WheelPlayerState(
    var nav: NavHostController,
    var wheel: WheelNavigation,
    panel: WheelPanel = WheelPanel.BROWSER,
    previousPanel: WheelPanel = WheelPanel.BROWSER,
    coverVisible: Boolean = true,
    coverDefault: Boolean = true,
    browserRoute: String = MUSIC_BROWSER_ROUTE,
    browserRoot: String = MUSIC_BROWSER_ROUTE,
    internal var needsBackStackRestore: Boolean = false,
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
    var showOutputInfo by mutableStateOf(false)

    private var seeking = false
    private var resumeAfterSeek = false
    private var seekPositionMs: Long? = null

    val isNowPlaying: Boolean
        get() = panel == WheelPanel.NOW_PLAYING

    val blocksBrowserInput: Boolean
        get() = panel != WheelPanel.BROWSER || wheel.overlay != null

    val controlsPlayback: Boolean
        get() = isNowPlaying && wheel.overlay == null

    fun beginSeek(controller: MediaController?) {
        val player = controller ?: return
        if (seeking) return
        seeking = true
        resumeAfterSeek = player.isPlaying || player.playWhenReady
        seekPositionMs = player.currentPosition
        if (resumeAfterSeek) player.pause()
    }

    fun seekBy(controller: MediaController?, durationMs: Long?, deltaMs: Long) {
        val player = controller ?: return
        beginSeek(player)
        val base = seekPositionMs ?: player.currentPosition
        val next = (base + deltaMs).coerceIn(0L, durationMs ?: Long.MAX_VALUE)
        seekPositionMs = next
        player.seekTo(next)
    }

    fun endSeek(controller: MediaController?) {
        if (!seeking) return
        val shouldResume = resumeAfterSeek
        seeking = false
        resumeAfterSeek = false
        seekPositionMs = null
        if (shouldResume) {
            controller?.prepare()
            controller?.play()
        }
    }

    fun clearDialogs() {
        wheel.clearOverlays()
        showPlaylistPicker = false
        showTrackInfo = false
        showOutputInfo = false
    }

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
        clearDialogs()
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
        clearDialogs()
        panel = WheelPanel.NOW_PLAYING
    }

    fun browse() {
        clearDialogs()
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
        clearDialogs()
        panel = WheelPanel.BROWSER
        nav.navigate(route) { launchSingleTop = true }
    }

    fun showBrowser() {
        clearDialogs()
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

    internal fun applyPersistedState(saved: WheelPageState) {
        panel = saved.panel.toWheelPanel()
        previousPanel = saved.previousPanel.toWheelPanel()
        browserRoute = saved.browserRoute
        browserRoot = saved.browserRoot
    }
}

private fun String.toWheelPanel(): WheelPanel =
    WheelPanel.entries.firstOrNull { it.name == this } ?: WheelPanel.BROWSER

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
                        panel = (it[0] as String).toWheelPanel(),
                        previousPanel = (it[1] as String).toWheelPanel(),
                        coverVisible = it[2] as Boolean,
                        coverDefault = it.getOrNull(5) as? Boolean ?: coverDefault,
                        browserRoute = it[3] as String,
                        browserRoot = it[4] as String,
                        needsBackStackRestore = false,
                    )
                },
            )
        }
    val state =
        rememberSaveable(saver = saver) {
            val saved = SettingsStore.currentWheelPageState
            WheelPlayerState(
                nav = nav,
                wheel = wheel,
                panel = saved?.panel?.toWheelPanel() ?: WheelPanel.BROWSER,
                previousPanel = saved?.previousPanel?.toWheelPanel() ?: WheelPanel.BROWSER,
                coverVisible = coverDefault,
                coverDefault = coverDefault,
                browserRoute = saved?.browserRoute ?: MUSIC_BROWSER_ROUTE,
                browserRoot = saved?.browserRoot ?: MUSIC_BROWSER_ROUTE,
                needsBackStackRestore = true,
            )
        }
    state.nav = nav
    state.wheel = wheel
    LaunchedEffect(coverDefault) { state.updateCoverDefault(coverDefault) }
    val entry by nav.currentBackStackEntryAsState()
    LaunchedEffect(entry) { state.rememberBrowser(entry) }

    LaunchedEffect(state, nav) {
        snapshotFlow { entry }.filterNotNull().first()
        val navigator =
            runCatching { nav.navigatorProvider.getNavigator<ComposeNavigator>("composable") }
                .getOrNull() ?: return@LaunchedEffect
        if (state.needsBackStackRestore) {
            val saved = SettingsStore.currentWheelPageState ?: SettingsStore.wheelPageStateFlow.first()
            if (saved != null) {
                val currentRoutes =
                    navigator.backStack.value.mapNotNull { it.resolvedRoute() }
                if (currentRoutes.size <= 1 && saved.backStack.isNotEmpty()) {
                    saved.backStack
                        .dropWhile { it == Screen.Home.route }
                        .forEach { route ->
                            runCatching { nav.navigate(route) { launchSingleTop = true } }
                        }
                }
                state.applyPersistedState(saved)
            }
            state.needsBackStackRestore = false
        }
        combine(
                snapshotFlow {
                    listOf(
                        state.panel.name,
                        state.previousPanel.name,
                        state.browserRoute,
                        state.browserRoot,
                    )
                },
                navigator.backStack,
            ) { stateFields, backStackEntries ->
                val routes =
                    backStackEntries.mapNotNull { it.resolvedRoute() }.ifEmpty {
                        listOf(Screen.Home.route)
                    }
                WheelPageState(
                    panel = stateFields[0],
                    previousPanel = stateFields[1],
                    browserRoute = stateFields[2],
                    browserRoot = stateFields[3],
                    backStack = routes,
                )
            }
            .distinctUntilChanged()
            .collectLatest { pageState -> SettingsStore.setWheelPageState(pageState) }
    }
    return state
}

private fun NavBackStackEntry.resolvedRoute(): String? = musicBrowserRoute() ?: destination.route

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
