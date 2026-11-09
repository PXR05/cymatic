package com.pxr.cymatic.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pxr.cymatic.sync.RemotePlaylist
import com.pxr.cymatic.sync.RemoteTrack
import androidx.compose.material3.Text

@Composable
internal fun SyncRemoteLibraryPage(
    state: LibrarySyncSettingsState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onCancel: () -> Unit,
) {
    var section by remember { mutableStateOf<String?>(null) }
    var selectedPlaylist by remember { mutableStateOf<RemotePlaylist?>(null) }
    var selectedSong by remember { mutableStateOf<RemoteTrack?>(null) }
    val catalog = state.catalog
    val title = selectedPlaylist?.name ?: when (section) {
        "songs" -> "Songs"
        "playlists" -> "Playlists"
        else -> "Library"
    }
    SyncSettingsPage(title, {
        when {
            selectedPlaylist != null -> selectedPlaylist = null
            section != null -> section = null
            else -> onDismiss()
        }
    }) {
        SyncSettingsList(buildList {
            when {
                section == "songs" || selectedPlaylist != null -> {
                    val tracksById = catalog?.tracks.orEmpty().associateBy { it.id }
                    val songs = selectedPlaylist?.trackIds?.mapNotNull(tracksById::get)
                        ?: catalog?.tracks.orEmpty()
                    if (songs.isEmpty()) add(SyncSetting("No songs", enabled = false))
                    songs.forEach { track ->
                        add(SyncSetting(track.title, "${track.artist} · ${track.album}",
                            onClick = { selectedSong = track }))
                    }
                }
                section == "playlists" -> {
                    if (catalog?.playlists.isNullOrEmpty()) add(SyncSetting("No playlists", enabled = false))
                    catalog?.playlists.orEmpty().forEach { playlist ->
                        add(SyncSetting(playlist.name, "${playlist.trackIds.size} songs",
                            onClick = { selectedPlaylist = playlist }))
                    }
                }
                else -> {
                    add(SyncSetting(
                        if (state.refreshingCatalog) "Stop refresh" else "Refresh library",
                        if (state.refreshingCatalog) "Loading songs and playlists…"
                        else "Update songs and playlists without downloading audio",
                        enabled = state.canEdit || state.refreshingCatalog,
                        onClick = if (state.refreshingCatalog) onCancel else onRefresh,
                    ))
                    if (catalog != null) {
                        add(SyncSetting("Songs", "${catalog.tracks.size}", onClick = { section = "songs" }))
                        add(SyncSetting("Playlists", "${catalog.playlists.size}", onClick = { section = "playlists" }))
                    }
                }
            }
        })
    }
    selectedSong?.let { track ->
        SyncReadingPage(track.title, { selectedSong = null }) {
            Text("${track.artist}\n${track.album}")
            Text("To play this song, download it using Keep offline, then open it in your local library. Remote streaming is not available yet.")
        }
    }
}
