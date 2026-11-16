package com.pxr.cymatic.ui.screens.library.artist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.sync.LibraryOfflineManager
import com.pxr.cymatic.ui.components.common.EmptyState
import com.pxr.cymatic.ui.navigation.Screen
import com.pxr.cymatic.ui.screens.library.SongListScreen
import com.pxr.cymatic.ui.screens.library.rememberLibraryGroups

@Composable
fun ArtistSongsScreen(
    artistName: String,
    modifier: Modifier = Modifier,
    scrollTargetId: Long? = null,
    viewModel: ArtistSongsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val songs by viewModel.songs.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.errorMessage.collectAsState()
    val group = rememberLibraryGroups().artists[artistName]
    LaunchedEffect(artistName) { viewModel.loadArtistSongs(artistName) }
    SongListScreen(
        title = artistName,
        audioFiles = songs,
        isLoading = loading,
        errorMessage = error,
        queueSource = Screen.ArtistSongs.createRoute(artistName),
        state = viewModel.listState,
        onReload = { viewModel.loadArtistSongs(artistName, force = true) },
        modifier = modifier,
        scrollTargetId = scrollTargetId,
        onKeepOffline =
            group
                ?.takeIf { it.canKeepOffline }
                ?.let {
                    { LibraryOfflineManager.keepSongs(context, it.songs, artistName) }
                },
    ) {
        EmptyState(
            "No Songs Found",
            "Cymatic did not find any songs for '$artistName'.",
            iconText = "( ! )",
        )
    }
}
