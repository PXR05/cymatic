package com.pxr.cymatic.ui.screens.library.artist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.data.model.LibraryFilter
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.data.store.RemoteSourceRegistry
import com.pxr.cymatic.sync.LibraryOfflineManager
import com.pxr.cymatic.ui.components.common.EmptyState
import com.pxr.cymatic.ui.components.common.ErrorState
import com.pxr.cymatic.ui.components.common.LibraryContextMenu
import com.pxr.cymatic.ui.components.common.LibraryFilterStatus
import com.pxr.cymatic.ui.components.common.LibraryFilteredEmptyState
import com.pxr.cymatic.ui.components.common.LoadingState
import com.pxr.cymatic.ui.components.common.PermissionDeniedState
import com.pxr.cymatic.ui.components.common.rememberStoragePermission
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen
import com.pxr.cymatic.ui.screens.library.LibraryGroupIndicator
import com.pxr.cymatic.ui.screens.library.LibraryGroups
import com.pxr.cymatic.ui.screens.library.rememberLibraryGroups

@Composable
fun ArtistAlbumsScreen(
    artistName: String,
    modifier: Modifier = Modifier,
    viewModel: ArtistAlbumsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val state by viewModel.uiState.collectAsState()
    val permission = rememberStoragePermission { granted ->
        if (granted) {
            viewModel.load(artistName, force = true)
        }
    }

    LaunchedEffect(artistName) { viewModel.load(artistName) }

    val availabilityFilter by LibraryFilterStore.filter.collectAsState()

    val groups = rememberLibraryGroups()
    val artistGroup = groups.artists[artistName]
    val albumsBySource =
        remember(artistGroup) { LibraryGroups(artistGroup?.songs.orEmpty()).albumsBySource }
    val sources by RemoteSourceRegistry.sourcesFlow.collectAsState(
        initial = RemoteSourceRegistry.currentSources
    )
    val sourceNames = remember(sources) { sources.associate { it.id to it.displayName } }
    val showSourceLabel = remember(state.albums) {
        state.albums.map { it.sourceId }.distinct().size > 1
    }
    var showHeaderMenu by remember { mutableStateOf(false) }
    var selectedAlbum by remember { mutableStateOf<ArtistAlbum?>(null) }
    BaseScreen(
        title = artistName,
        onMenuLongClick = { showHeaderMenu = true },
        onBackClick = { navController.popBackStack() },
        modifier = modifier,
    ) {
        LibraryFilterStatus(availabilityFilter)
        when {
            !permission.granted &&
                    availabilityFilter == LibraryFilter.ALL &&
                    state.albums.isEmpty() ->
                PermissionDeniedState(
                    onGrantClick = {
                        permission.request()
                    }
                )

            state.errorMessage != null ->
                ErrorState(
                    message = state.errorMessage ?: "Unknown error",
                    onRetry = { viewModel.load(artistName, force = true) },
                )

            state.isLoading -> LoadingState()
            availabilityFilter != LibraryFilter.ALL && state.totalTracks == 0 ->
                LibraryFilteredEmptyState()

            state.totalTracks == 0 ->
                EmptyState(
                    title = "No Music Found",
                    message = "Cymatic did not find any music for '$artistName'.",
                    iconText = "( ! )",
                )

            else ->
                NavigationList(
                    items =
                        listOf(
                            NavigationItem(
                                label = "All",
                                trailingContent = { color ->
                                    LibraryGroupIndicator(artistGroup, color)
                                },
                                onLongClick = { showHeaderMenu = true },
                                subLabel = "${state.totalTracks} ${trackLabel(state.totalTracks)}",
                                onClick = {
                                    navController.navigate(
                                        Screen.ArtistSongs.createRoute(artistName)
                                    )
                                },
                            )
                        ) +
                                state.albums.map { album ->
                                    val countText =
                                        "${album.trackCount} ${trackLabel(album.trackCount)}"
                                    val subLabel =
                                        if (showSourceLabel) {
                                            val src =
                                                album.sourceId?.let { sourceNames[it] ?: "Remote" }
                                                    ?: "This device"
                                            "$countText · $src"
                                        } else countText
                                    NavigationItem(
                                        label = album.name,
                                        key = album.key,
                                        trailingContent = { color ->
                                            LibraryGroupIndicator(
                                                albumsBySource[album.name to album.sourceId],
                                                color,
                                            )
                                        },
                                        onLongClick = { selectedAlbum = album },
                                        subLabel = subLabel,
                                        onClick = {
                                            navController.navigate(
                                                Screen.ArtistAlbumSongs.createRoute(
                                                    artistName,
                                                    album.name,
                                                    sourceKey = album.sourceKey,
                                                )
                                            )
                                        },
                                    )
                                }
                )
        }
    }
    if (showHeaderMenu)
        LibraryContextMenu(
            title = artistName,
            onDismiss = { showHeaderMenu = false },
            showLibraryFilter = true,
            onKeepOffline =
                artistGroup
                    ?.takeIf { it.canKeepOffline }
                    ?.let { group ->
                        { LibraryOfflineManager.keepSongs(context, group.songs, artistName) }
                    },
        )
    selectedAlbum?.let { album ->
        LibraryContextMenu(
            title = album.name,
            onDismiss = { selectedAlbum = null },
            onKeepOffline =
                albumsBySource[album.name to album.sourceId]
                    ?.takeIf { it.canKeepOffline }
                    ?.let { group ->
                        { LibraryOfflineManager.keepSongs(context, group.songs, album.name) }
                    },
        )
    }
}

private fun trackLabel(count: Int) = if (count == 1) "track" else "tracks"
