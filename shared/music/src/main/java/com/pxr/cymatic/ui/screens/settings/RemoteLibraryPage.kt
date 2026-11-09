package com.pxr.cymatic.ui.screens.settings

import androidx.compose.runtime.Composable
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

@Composable
internal fun SyncRemoteLibraryPage(
    state: LibrarySyncSettingsState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onCancel: () -> Unit,
) {
    val nav = LocalNavController.current
    SyncSettingsPage("Library", onDismiss) {
        SyncSettingsList(
            listOf(
                SyncSetting("Songs", "${state.catalog?.tracks?.size ?: 0}", onClick = {
                    onDismiss()
                    nav.navigate(Screen.AllSongs.createRoute())
                }),
                SyncSetting("Playlists", "${state.catalog?.playlists?.size ?: 0}", onClick = {
                    onDismiss()
                    nav.navigate(Screen.Playlists.route)
                }),
                SyncSetting(
                    if (state.refreshingCatalog) "Stop refresh" else "Refresh library",
                    if (state.refreshingCatalog) "Loading songs and playlists…" else "Update the library without downloading audio",
                    enabled = state.canEdit || state.refreshingCatalog,
                    onClick = if (state.refreshingCatalog) onCancel else onRefresh,
                ),
            )
        )
    }
}
