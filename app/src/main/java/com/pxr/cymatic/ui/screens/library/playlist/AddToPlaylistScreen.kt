package com.pxr.cymatic.ui.screens.library.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pxr.cymatic.data.media.Playlist
import com.pxr.cymatic.data.media.PlaylistRepository
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AddToPlaylistScreen(audioId: Long) {
    val nav = LocalNavController.current
    val onDismiss: () -> Unit = { nav.popBackStack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(context) { PlaylistRepository.getInstance(context) }

    val cachedPlaylists = remember(repo, audioId) {
        repo.getCachedPlaylists()?.filter { it.sourceId == null }
    }
    var playlists by remember(audioId) { mutableStateOf(cachedPlaylists.orEmpty()) }
    var memberIds by remember(audioId) {
        mutableStateOf(
            cachedPlaylists
                ?.filter { playlist ->
                    repo.getCachedPlaylistAudio(playlist.id)?.any { it.id == audioId } == true
                }
                ?.map { it.id }
                ?.toSet()
                .orEmpty()
        )
    }
    var loaded by remember(audioId) {
        mutableStateOf(
            cachedPlaylists != null &&
                    cachedPlaylists.all { repo.getCachedPlaylistAudio(it.id) != null }
        )
    }

    val toggle: (Playlist) -> Unit = { playlist ->
        val isMember = playlist.id in memberIds
        scope.launch {
            withContext(Dispatchers.IO) {
                if (isMember) repo.removeAudioFromPlaylist(playlist.id, audioId)
                else repo.addAudioToPlaylist(playlist.id, audioId)
            }
            memberIds = if (isMember) memberIds - playlist.id else memberIds + playlist.id
        }
    }

    LaunchedEffect(audioId) {
        withContext(Dispatchers.IO) {
            val all = repo.getPlaylists().filter { it.sourceId == null }
            val members =
                all.filter { playlist ->
                    repo.getPlaylistAudio(playlist.id).any { it.id == audioId }
                }
                    .map { it.id }
                    .toSet()
            playlists = all
            memberIds = members
            loaded = true
        }
    }

    BaseScreen("Add to Playlist", onDismiss) {
        NavigationList(
            buildList {
                if (loaded && playlists.isEmpty()) add(
                    NavigationItem(
                        "No playlists yet",
                        enabled = false
                    )
                )
                playlists.forEach { playlist ->
                    add(
                        NavigationItem(
                            playlist.name,
                            if (playlist.id in memberIds) "Added" else "Not added",
                            key = playlist.id,
                            checked = playlist.id in memberIds,
                        ) {
                            toggle(playlist)
                        }
                    )
                }
                add(NavigationItem("Done", key = "done", onClick = onDismiss))
            }
        )
    }
}
