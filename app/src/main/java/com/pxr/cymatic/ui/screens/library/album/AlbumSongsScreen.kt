package com.pxr.cymatic.ui.screens.library.album

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.data.library.filterByArtist
import com.pxr.cymatic.sync.LibraryOfflineManager
import com.pxr.cymatic.ui.components.common.EmptyState
import com.pxr.cymatic.ui.navigation.Screen
import com.pxr.cymatic.ui.screens.library.LibraryGroup
import com.pxr.cymatic.ui.screens.library.SongListScreen
import com.pxr.cymatic.ui.screens.library.rememberLibraryGroups

@Composable
fun AlbumSongsScreen(
    albumName: String,
    artistName: String? = null,
    sourceKey: String? = null,
    modifier: Modifier = Modifier,
    scrollTargetId: Long? = null,
    viewModel: AlbumSongsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val songs by viewModel.songs.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.errorMessage.collectAsState()
    val group =
        rememberLibraryGroups().albums[albumName]?.let { album ->
            val filteredByArtist =
                if (artistName == null) album.songs else filterByArtist(album.songs, artistName)
            val filteredBySource =
                when (sourceKey) {
                    null -> filteredByArtist
                    "local" -> filteredByArtist.filter { it.sourceId == null }
                    else -> filteredByArtist.filter { it.sourceId == sourceKey }
                }
            LibraryGroup(filteredBySource)
        }
    LaunchedEffect(albumName, artistName, sourceKey) {
        viewModel.loadAlbumSongs(albumName, artistName, sourceKey)
    }
    SongListScreen(
        title = albumName,
        audioFiles = songs,
        isLoading = loading,
        errorMessage = error,
        queueSource =
            if (artistName == null) Screen.AlbumSongs.createRoute(albumName, sourceKey = sourceKey)
            else Screen.ArtistAlbumSongs.createRoute(artistName, albumName, sourceKey = sourceKey),
        state = viewModel.listState,
        onReload = { viewModel.loadAlbumSongs(albumName, artistName, sourceKey, force = true) },
        modifier = modifier,
        scrollTargetId = scrollTargetId,
        onKeepOffline =
            group
                ?.takeIf { it.canKeepOffline }
                ?.let {
                    { LibraryOfflineManager.keepSongs(context, it.songs, albumName) }
                },
    ) {
        EmptyState(
            "No Songs Found",
            "Cymatic did not find any songs for '$albumName'.",
            iconText = "( ! )",
        )
    }
}
