package com.pxr.cymatic.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.R
import com.pxr.cymatic.data.media.Playlist
import com.pxr.cymatic.data.media.PlaylistRepository
import com.pxr.cymatic.ui.components.common.BaseScreen
import com.pxr.cymatic.ui.components.common.NavigationItem
import com.pxr.cymatic.ui.components.common.NavigationList
import com.pxr.cymatic.ui.components.common.PlaylistContextMenu
import com.pxr.cymatic.ui.components.primitives.PixelInputDialog
import com.pxr.cymatic.ui.locals.LocalNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PlaylistsScreen(modifier: Modifier = Modifier) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fontFamily = FontFamily(Font(R.font.pixel))

    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }

    fun loadPlaylists() {
        scope.launch {
            playlists = withContext(Dispatchers.IO) {
                PlaylistRepository.getInstance(context).getPlaylists()
            }
        }
    }

    LaunchedEffect(Unit) { loadPlaylists() }

    val items = playlists.map { playlist ->
        NavigationItem(
            playlist.name,
            onClick = {
                navController.navigate("playlist/${playlist.id}")
            },
            onLongClick = { selectedPlaylist = playlist }
        )
    }

    BaseScreen(
        title = "Playlists",
        onBackClick = { navController.popBackStack() },
        modifier = modifier,
        actions = {
            Text(
                text = "+",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontFamily = fontFamily,
                modifier = Modifier
                    .clickable(
                        onClick = { showCreateDialog = true },
                        indication = null,
                        interactionSource = null
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }
    ) {
        NavigationList(items = items)
    }

    if (showCreateDialog) {
        PixelInputDialog(
            fontFamily = fontFamily,
            title = "New Playlist",
            hint = "Playlist name",
            value = newPlaylistName,
            onValueChange = { newPlaylistName = it },
            onConfirm = {
                val name = newPlaylistName.trim()
                if (name.isNotEmpty()) {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            PlaylistRepository.getInstance(context).createPlaylist(name)
                        }
                        newPlaylistName = ""
                        showCreateDialog = false
                        loadPlaylists()
                    }
                }
            },
            onDismiss = {
                newPlaylistName = ""
                showCreateDialog = false
            }
        )
    }

    selectedPlaylist?.let { playlist ->
        PlaylistContextMenu(
            playlist = playlist,
            onDismiss = { selectedPlaylist = null },
            onRename = { playlistId, newName ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        PlaylistRepository.getInstance(context).renamePlaylist(playlistId, newName)
                    }
                    loadPlaylists()
                }
            },
            onDelete = { playlistId ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        PlaylistRepository.getInstance(context).deletePlaylist(playlistId)
                    }
                    loadPlaylists()
                }
            }
        )
    }
}
