package com.pxr.cymatic.ui.navigation

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.motion.CymaticMotion
import com.pxr.cymatic.ui.screens.library.AllSongsScreen
import com.pxr.cymatic.ui.screens.library.QueueScreen
import com.pxr.cymatic.ui.screens.library.UnknownAlbum
import com.pxr.cymatic.ui.screens.library.UnknownArtist
import com.pxr.cymatic.ui.screens.library.album.AlbumSongsScreen
import com.pxr.cymatic.ui.screens.library.album.AlbumsScreen
import com.pxr.cymatic.ui.screens.library.artist.ArtistSongsScreen
import com.pxr.cymatic.ui.screens.library.artist.ArtistsScreen
import com.pxr.cymatic.ui.screens.library.playlist.PlaylistSongsScreen
import com.pxr.cymatic.ui.screens.library.playlist.PlaylistsScreen
import com.pxr.cymatic.ui.screens.settings.EQSettingsScreen
import com.pxr.cymatic.ui.screens.settings.PlaybackSettingsScreen
import com.pxr.cymatic.ui.screens.settings.SettingsScreen
import com.pxr.cymatic.ui.screens.settings.StorageSettingsScreen
import com.pxr.cymatic.ui.screens.settings.VersionSettingsScreen

/** Registers shared music destinations, with product-specific entry points supplied by the host. */
@Composable
fun MusicNavHost(
    home: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    settingsItems: List<NavigationItem> = emptyList(),
    additionalRoutes: Map<String, @Composable (NavBackStackEntry) -> Unit> = emptyMap(),
    animate: Boolean = false,
) {
    val pageDistancePx = with(LocalDensity.current) { 24.dp.roundToPx() }
    val routes = mapOf<String, @Composable (NavBackStackEntry) -> Unit>(
        Screen.Home.route to { home() },
        Screen.AllSongs.route to { entry ->
            val scrollId = entry.arguments?.getString("scrollId")
            AllSongsScreen(
                scrollTargetId = scrollId?.toLongOrNull()
            )
        },
        Screen.Artists.route to { ArtistsScreen() },
        Screen.ArtistSongs.route to { entry ->
            val rawName = entry.arguments?.getString("artistName")
            val artistName = rawName?.let(Uri::decode) ?: UnknownArtist
            val scrollId = entry.arguments?.getString("scrollId")
            ArtistSongsScreen(
                artistName = artistName,
                scrollTargetId = scrollId?.toLongOrNull()
            )
        },
        Screen.Albums.route to { AlbumsScreen() },
        Screen.AlbumSongs.route to { entry ->
            val rawName = entry.arguments?.getString("albumName")
            val albumName = rawName?.let(Uri::decode) ?: UnknownAlbum
            val scrollId = entry.arguments?.getString("scrollId")
            AlbumSongsScreen(
                albumName = albumName,
                scrollTargetId = scrollId?.toLongOrNull()
            )
        },
        Screen.Playlists.route to { PlaylistsScreen() },
        Screen.PlaylistSongs.route to { entry ->
            val playlistId = entry.arguments?.getString("playlistId")?.toLongOrNull() ?: return@to
            val scrollId = entry.arguments?.getString("scrollId")
            PlaylistSongsScreen(
                playlistId = playlistId,
                scrollTargetId = scrollId?.toLongOrNull()
            )
        },
        Screen.Settings.route to { SettingsScreen(additionalItems = settingsItems) },
        Screen.EQSettings.route to { EQSettingsScreen() },
        Screen.PlaybackSettings.route to { PlaybackSettingsScreen() },
        Screen.StorageSettings.route to { StorageSettingsScreen() },
        Screen.VersionSettings.route to { VersionSettingsScreen() },
        Screen.Queue.route to { QueueScreen() },
    ) + additionalRoutes
    NavHost(
        navController = LocalNavController.current,
        startDestination = Screen.Home.route,
        enterTransition = {
            if (!animate) EnterTransition.None else CymaticMotion.pageEnter(pageDistancePx)
        },
        exitTransition = {
            if (!animate) ExitTransition.None else CymaticMotion.pageExit()
        },
        popEnterTransition = {
            if (!animate) EnterTransition.None else CymaticMotion.pageEnter(-pageDistancePx)
        },
        popExitTransition = {
            if (!animate) ExitTransition.None else CymaticMotion.pageExit()
        },
        modifier = modifier.clipToBounds()
    ) {
        routes.forEach { (route, composable) ->
            composable(route) { backStackEntry ->
                composable(backStackEntry)
            }
        }
    }
}
