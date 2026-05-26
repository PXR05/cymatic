package com.pxr.cymatic.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.common.PlaylistContextMenu
import com.pxr.cymatic.ui.components.common.LoadingState
import com.pxr.cymatic.ui.components.common.EmptyState
import com.pxr.cymatic.ui.components.common.ErrorState
import com.pxr.cymatic.ui.components.primitives.PixelInputDialog
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

@Composable
fun PlaylistsScreen(
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = viewModel()
) {
    val navController = LocalNavController.current

    val uiState by viewModel.uiState.collectAsState()

    val items = uiState.playlists.map { playlist ->
        NavigationItem(
            playlist.name,
            onClick = {
                navController.navigate(Screen.PlaylistSongs.createRoute(playlist.id))
            },
            onLongClick = { viewModel.selectedPlaylist = playlist }
        )
    }

    BaseScreen(
        title = "Playlists",
        onBackClick = { navController.popBackStack() },
        modifier = modifier,
        searchQuery = viewModel.searchQuery,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        isSearchActive = viewModel.isSearchActive,
        onSearchActiveChange = viewModel::onSearchActiveChange,
        actions = {
            Text(
                text = "+",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                modifier = Modifier
                    .clickable(
                        onClick = { viewModel.showCreateDialog = true },
                        indication = null,
                        interactionSource = null
                    )
                    .padding(vertical = 16.dp)
            )
        }
    ) {
        if (uiState.errorMessage != null) {
            ErrorState(
                message = uiState.errorMessage ?: "Unknown error",
                onRetry = { viewModel.loadPlaylists() }
            )
        } else if (uiState.isLoading) {
            LoadingState()
        } else if (uiState.playlists.isEmpty()) {
            if (viewModel.isSearchActive && viewModel.searchQuery.isNotEmpty()) {
                EmptyState(
                    title = "No Matches Found",
                    message = "No playlists match '${viewModel.searchQuery}'",
                    iconText = "( ? )"
                )
            } else {
                EmptyState(
                    title = "No Playlists",
                    message = "Tap the '+' icon on the top right to create your first playlist.",
                    iconText = "( ! )"
                )
            }
        } else {
            NavigationList(items = items)
        }
    }

    if (viewModel.showCreateDialog) {
        PixelInputDialog(
            title = "New Playlist",
            hint = "Playlist name",
            value = viewModel.newPlaylistName,
            onValueChange = { viewModel.newPlaylistName = it },
            onConfirm = {
                viewModel.createPlaylist(viewModel.newPlaylistName)
                viewModel.newPlaylistName = ""
                viewModel.showCreateDialog = false
            },
            onDismiss = {
                viewModel.newPlaylistName = ""
                viewModel.showCreateDialog = false
            }
        )
    }

    viewModel.selectedPlaylist?.let { playlist ->
        PlaylistContextMenu(
            playlist = playlist,
            onDismiss = { viewModel.selectedPlaylist = null },
            onRename = { playlistId, newName ->
                viewModel.renamePlaylist(playlistId, newName)
            },
            onDelete = { playlistId ->
                viewModel.deletePlaylist(playlistId)
            }
        )
    }
}
