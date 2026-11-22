package com.pxr.cymatic.ui.screens.settings.sync

import androidx.compose.runtime.Composable
import com.pxr.cymatic.R
import com.pxr.cymatic.ui.components.pixelIcon
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

@Composable
internal fun SyncRemoteLibraryPage(
    state: RemoteSourceState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onCancel: () -> Unit,
) {
    val nav = LocalNavController.current
    val albumCount =
        state.catalog
            ?.tracks
            .orEmpty()
            .distinctBy { it.album.trim().ifBlank { "Unknown Album" } }
            .size
    SyncSettingsPage(state.displayName, onDismiss) {
        SyncSettingsList(
            listOf(
                SyncSetting(
                    "Songs",
                    "${state.catalog?.tracks?.size ?: 0}",
                    icon = pixelIcon(R.drawable.ic_pixel_music_note),
                    onClick = { nav.navigate(Screen.AllSongs.createRoute()) },
                ),
                SyncSetting(
                    "Albums",
                    "$albumCount",
                    icon = pixelIcon(R.drawable.ic_pixel_disc),
                    onClick = { nav.navigate(Screen.Albums.route) },
                ),
                SyncSetting(
                    "Playlists",
                    "${state.catalog?.playlists?.size ?: 0}",
                    icon = pixelIcon(R.drawable.ic_pixel_playlist),
                    onClick = { nav.navigate(Screen.Playlists.route) },
                ),
                SyncSetting(
                    if (state.refreshingCatalog) "Stop refresh" else "Refresh library",
                    if (state.refreshingCatalog) "Loading songs and playlists…"
                    else "Update the library without downloading audio",
                    enabled = state.canEdit || state.refreshingCatalog,
                    icon = pixelIcon(if (state.refreshingCatalog) R.drawable.ic_pixel_close else R.drawable.ic_pixel_refresh),
                    onClick = if (state.refreshingCatalog) onCancel else onRefresh,
                ),
            )
        )
    }
}
