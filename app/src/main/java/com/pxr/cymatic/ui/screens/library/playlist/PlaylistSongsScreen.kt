package com.pxr.cymatic.ui.screens.library.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.data.model.LibraryAvailability
import com.pxr.cymatic.sync.LibraryOfflineManager
import com.pxr.cymatic.ui.components.common.EmptyState
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen
import com.pxr.cymatic.ui.screens.library.SongListScreen

@Composable
fun PlaylistSongsScreen(
    playlistId: Long,
    modifier: Modifier = Modifier,
    scrollTargetId: Long? = null,
    viewModel: PlaylistSongsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val nav = LocalNavController.current
    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.audioFiles.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.errorMessage.collectAsState()
    val title = playlist?.name ?: "Playlist"
    LaunchedEffect(playlistId) { viewModel.loadPlaylist(playlistId) }
    SongListScreen(
        title = title,
        audioFiles = songs,
        isLoading = loading,
        errorMessage = error,
        queueSource = Screen.PlaylistSongs.createRoute(playlistId),
        state = viewModel.listState,
        onReload = { viewModel.loadPlaylist(playlistId) },
        modifier = modifier,
        scrollTargetId = scrollTargetId,
        needsStoragePermission = false,
        showMenuWhenEmpty = false,
        onKeepOffline =
            if (playlist?.availability != LibraryAvailability.OFFLINE && songs.isNotEmpty())
                ({
                    val source = playlist?.sourceId
                    val remoteId = playlist?.remoteId
                    if (source != null && remoteId != null)
                        LibraryOfflineManager.keepPlaylist(context, source, remoteId, title)
                    else LibraryOfflineManager.keepPlaylist(context, playlistId, title)
                })
            else null,
    ) {
        EmptyState(
            title = "Playlist is Empty",
            message = "Add tracks to this playlist from the context menu of a track.",
            iconText = "( ! )",
            actionLabel = "GO TO SONGS",
            onActionClick = { nav.navigate(Screen.AllSongs.createRoute()) },
        )
    }
}
