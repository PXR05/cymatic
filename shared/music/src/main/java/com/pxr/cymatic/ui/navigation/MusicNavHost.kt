package com.pxr.cymatic.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navigation
import com.pxr.cymatic.data.library.UnknownAlbum
import com.pxr.cymatic.data.library.UnknownArtist
import com.pxr.cymatic.data.licenses.LicenseGroup
import com.pxr.cymatic.ui.components.common.ReadingScreen
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.storage.StatusBento
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.screens.library.AllSongsScreen
import com.pxr.cymatic.ui.screens.library.LibraryCategory
import com.pxr.cymatic.ui.screens.library.LibraryCategoryScreen
import com.pxr.cymatic.ui.screens.library.SongInfoScreen
import com.pxr.cymatic.ui.screens.library.album.AlbumSongsScreen
import com.pxr.cymatic.ui.screens.library.artist.ArtistAlbumsScreen
import com.pxr.cymatic.ui.screens.library.artist.ArtistSongsScreen
import com.pxr.cymatic.ui.screens.library.playlist.AddToPlaylistScreen
import com.pxr.cymatic.ui.screens.library.playlist.PlaylistSongsScreen
import com.pxr.cymatic.ui.screens.library.playlist.PlaylistsScreen
import com.pxr.cymatic.ui.screens.settings.SettingsScreen
import com.pxr.cymatic.ui.screens.settings.VersionSettingsScreen
import com.pxr.cymatic.ui.screens.settings.VersionSettingsViewModel
import com.pxr.cymatic.ui.screens.settings.eq.EQSettingsScreen
import com.pxr.cymatic.ui.screens.settings.eq.EqViewModel
import com.pxr.cymatic.ui.screens.settings.eq.hardware.HardwareEqSettingsScreen
import com.pxr.cymatic.ui.screens.settings.eq.hardware.HardwareEqViewModel
import com.pxr.cymatic.ui.screens.settings.licenses.LicensesSettingsScreen
import com.pxr.cymatic.ui.screens.settings.playback.OutputInfoScreen
import com.pxr.cymatic.ui.screens.settings.playback.PlaybackSettingsScreen
import com.pxr.cymatic.ui.screens.settings.storage.StorageSettingsScreen
import com.pxr.cymatic.ui.screens.settings.sync.LibrarySyncSettingsScreen
import com.pxr.cymatic.ui.screens.settings.sync.LibrarySyncSettingsViewModel
import com.pxr.cymatic.ui.screens.settings.usb.UsbAudioDiagnosticsViewModel
import com.pxr.cymatic.ui.screens.settings.usb.UsbSettingsScreen

@Composable
fun MusicNavHost(
    home: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    settingsItems: List<NavigationItem> = emptyList(),
    additionalRoutes: Map<String, @Composable (NavBackStackEntry) -> Unit> = emptyMap(),
    startDestination: String = Screen.Home.route,
    footer: @Composable () -> Unit = {},
    overlay: @Composable () -> Unit = {},
) {
    val routes =
        mapOf<String, @Composable (NavBackStackEntry) -> Unit>(
            Screen.Home.route to { home() },
            Screen.AllSongs.route to
                { entry ->
                    val scrollId = entry.arguments?.getString("scrollId")
                    AllSongsScreen(scrollTargetId = scrollId?.toLongOrNull())
                },
            Screen.Artists.route to { LibraryCategoryScreen(LibraryCategory.ARTISTS) },
            Screen.ArtistAlbums.route to
                { entry ->
                    val artistName = entry.arguments?.getString("artistName") ?: UnknownArtist
                    ArtistAlbumsScreen(artistName = artistName)
                },
            Screen.ArtistSongs.route to
                { entry ->
                    val rawName = entry.arguments?.getString("artistName")
                    val artistName = rawName ?: UnknownArtist
                    val scrollId = entry.arguments?.getString("scrollId")
                    ArtistSongsScreen(
                        artistName = artistName,
                        scrollTargetId = scrollId?.toLongOrNull(),
                    )
                },
            Screen.ArtistAlbumSongs.route to
                { entry ->
                    val artistName = entry.arguments?.getString("artistName") ?: UnknownArtist
                    val albumName = entry.arguments?.getString("albumName") ?: UnknownAlbum
                    val scrollId = entry.arguments?.getString("scrollId")
                    AlbumSongsScreen(
                        albumName = albumName,
                        artistName = artistName,
                        scrollTargetId = scrollId?.toLongOrNull(),
                    )
                },
            Screen.Albums.route to { LibraryCategoryScreen(LibraryCategory.ALBUMS) },
            Screen.AlbumSongs.route to
                { entry ->
                    val rawName = entry.arguments?.getString("albumName")
                    val albumName = rawName ?: UnknownAlbum
                    val scrollId = entry.arguments?.getString("scrollId")
                    AlbumSongsScreen(
                        albumName = albumName,
                        scrollTargetId = scrollId?.toLongOrNull(),
                    )
                },
            Screen.Playlists.route to { PlaylistsScreen() },
            Screen.PlaylistSongs.route to
                { entry ->
                    val playlistId =
                        entry.arguments?.getString("playlistId")?.toLongOrNull() ?: return@to
                    val scrollId = entry.arguments?.getString("scrollId")
                    PlaylistSongsScreen(
                        playlistId = playlistId,
                        scrollTargetId = scrollId?.toLongOrNull(),
                    )
                },
            Screen.Settings.route to
                {
                    SettingsScreen(additionalItems = settingsItems)
                },
            Screen.PlaybackSettings.route to { PlaybackSettingsScreen() },
            Screen.StorageSettings.route to { StorageSettingsScreen() },
        ) + additionalRoutes
    val nav = LocalNavController.current
    val currentRoutes = routes.mapValues { (route, content) ->
        key(route) { rememberUpdatedState(content) }
    }
    val currentFooter = rememberUpdatedState(footer)
    val currentOverlay = rememberUpdatedState(overlay)
    val currentContentModifier = rememberUpdatedState(contentModifier)
    fun NavGraphBuilder.page(route: String, content: @Composable (NavBackStackEntry) -> Unit) {
        composable(route) { entry ->
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Box(currentContentModifier.value.fillMaxSize()) { content(entry) }
                    currentOverlay.value()
                }
                if (route != Screen.NowPlaying.route) currentFooter.value()
            }
        }
    }
    val graph =
        remember(nav, startDestination, routes.keys) {
            nav.createGraph(startDestination = startDestination) {
                routes.keys.forEach { route ->
                    page(route) { entry -> currentRoutes.getValue(route).value(entry) }
                }
                page(Screen.SongInfo.route) { entry ->
                    entry.arguments?.getString("mediaId")?.toLongOrNull()?.let {
                        SongInfoScreen(it)
                    }
                }
                page(Screen.AddToPlaylist.route) { entry ->
                    entry.arguments?.getString("mediaId")?.toLongOrNull()?.let {
                        AddToPlaylistScreen(it)
                    }
                }
                page(Screen.OutputInfo.route) { OutputInfoScreen() }
                page(Screen.OutputInfoSection.route) { entry ->
                    OutputInfoScreen(entry.arguments?.getString("section"))
                }
                page(Screen.ScanStatus.route) {
                    ReadingScreen("Scan status", { nav.popBackStack() }) { StatusBento() }
                }
                navigation(
                    startDestination = Screen.VersionSettings.route,
                    route = Screen.VersionGraph.route,
                ) {
                    listOf(Screen.VersionSettings, Screen.Changelog).forEach { screen ->
                        page(screen.route) {
                            val model: VersionSettingsViewModel =
                                graphViewModel(Screen.VersionGraph.route)
                            VersionSettingsScreen(model = model, pageRoute = screen.route)
                        }
                    }
                }
                navigation(
                    startDestination = Screen.UsbSettings.route,
                    route = Screen.UsbGraph.route,
                ) {
                    listOf(
                            Screen.UsbSettings,
                            Screen.UsbDeviceReport,
                            Screen.BackgroundPlaybackHelp,
                        )
                        .forEach { screen ->
                            page(screen.route) { entry ->
                                val model: UsbAudioDiagnosticsViewModel =
                                    graphViewModel(Screen.UsbGraph.route)
                                val owner =
                                    remember(nav) { nav.getBackStackEntry(Screen.UsbGraph.route) }
                                LaunchedEffect(model, owner) {
                                    model.bindGraphLifecycle(owner.lifecycle)
                                }
                                UsbSettingsScreen(
                                    model = model,
                                    deviceName = entry.arguments?.getString("deviceName"),
                                    backgroundHelp = screen == Screen.BackgroundPlaybackHelp,
                                )
                            }
                        }
                }
                navigation(
                    startDestination = Screen.EQSettings.route,
                    route = Screen.EqGraph.route,
                ) {
                    listOf(
                            Screen.EQSettings,
                            Screen.EqPresets,
                            Screen.EqBands,
                            Screen.EqBand,
                            Screen.EqGraphPage,
                        )
                        .forEach { screen ->
                            page(screen.route) { entry ->
                                val model: EqViewModel = graphViewModel(Screen.EqGraph.route)
                                EQSettingsScreen(
                                    viewModel = model,
                                    pageRoute = screen.route,
                                    bandIndex =
                                        entry.arguments?.getString("bandIndex")?.toIntOrNull()
                                            ?: -1,
                                )
                            }
                        }
                    listOf(
                            Screen.HardwareEq,
                            Screen.HardwareEqPresets,
                            Screen.HardwareEqBands,
                            Screen.HardwareEqBand,
                            Screen.HardwareEqGraphPage,
                            Screen.HardwareEqDevice,
                            Screen.HardwareEqIssues,
                        )
                        .forEach { screen ->
                            page(screen.route) { entry ->
                                val playerModel: EqViewModel = graphViewModel(Screen.EqGraph.route)
                                val model: HardwareEqViewModel =
                                    graphViewModel(Screen.EqGraph.route)
                                val state by playerModel.uiState.collectAsState()
                                HardwareEqSettingsScreen(
                                    state.presets,
                                    onPlayer = {
                                        if (!nav.popBackStack(Screen.EQSettings.route, false))
                                            nav.navigate(Screen.EQSettings.route)
                                    },
                                    model = model,
                                    pageRoute = screen.route,
                                    bandIndex =
                                        entry.arguments?.getString("bandIndex")?.toIntOrNull()
                                            ?: -1,
                                )
                            }
                        }
                }
                navigation(
                    startDestination = Screen.LibrarySyncSettings.route,
                    route = Screen.SyncGraph.route,
                ) {
                    listOf(
                            Screen.LibrarySyncSettings,
                            Screen.SyncConnection,
                            Screen.SyncLibrary,
                            Screen.SyncBulkDownloads,
                            Screen.SyncAdvanced,
                            Screen.SyncSchedule,
                            Screen.SyncStatus,
                            Screen.SyncPlaylists,
                            Screen.SyncHelp,
                            Screen.SyncError,
                        )
                        .forEach { screen ->
                            page(screen.route) {
                                val model: LibrarySyncSettingsViewModel =
                                    graphViewModel(Screen.SyncGraph.route)
                                LibrarySyncSettingsScreen(model, pageRoute = screen.route)
                            }
                        }
                }
                navigation(
                    startDestination = Screen.LicensesSettings.route,
                    route = Screen.LicensesGraph.route,
                ) {
                    page(Screen.LicensesSettings.route) { LicensesSettingsScreen() }
                    page(Screen.LicenseGroupPage.route) { entry ->
                        val group =
                            LicenseGroup.entries.firstOrNull {
                                it.name == entry.arguments?.getString("group")
                            } ?: LicenseGroup.ROOT
                        LicensesSettingsScreen(group = group)
                    }
                    page(Screen.LicenseDocument.route) { entry ->
                        val group =
                            LicenseGroup.entries.firstOrNull {
                                it.name == entry.arguments?.getString("group")
                            } ?: LicenseGroup.ROOT
                        LicensesSettingsScreen(
                            group = group,
                            documentIndex =
                                entry.arguments?.getString("documentIndex")?.toIntOrNull(),
                        )
                    }
                }
            }
        }
    NavHost(
        navController = nav,
        graph = graph,
        modifier = modifier.clipToBounds(),
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    )
}

@Composable
private inline fun <reified T : ViewModel> graphViewModel(graph: String): T {
    val nav = LocalNavController.current
    val owner = remember(nav, graph) { nav.getBackStackEntry(graph) }
    return viewModel(viewModelStoreOwner = owner)
}
