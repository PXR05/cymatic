package com.pxr.cymatic.ui.screens.settings.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pxr.cymatic.sync.SyncContentMode
import com.pxr.cymatic.ui.components.common.WheelContextMenu
import com.pxr.cymatic.ui.components.list.NavigationItem

@Composable
internal fun RemoteSourcePage(
    state: RemoteSourceState,
    onDismiss: () -> Unit,
    onConnection: () -> Unit,
    onLibrary: () -> Unit,
    onMode: (SyncContentMode) -> Unit,
    onPlaylists: () -> Unit,
    onDownload: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var showDownloadsMenu by remember { mutableStateOf(false) }

    SyncSettingsPage(state.displayName, onDismiss) {
        SyncSettingsList(
            buildList {
                if (state.loading) {
                    add(SyncSetting("Loading…", enabled = false))
                } else {
                    add(
                        SyncSetting(
                            title = if (state.connected) "Connection" else "Sign in",
                            detail =
                                if (state.connected) {
                                    "${state.username} · ${syncHostLabel(state.url)}"
                                } else {
                                    "Sign in to load this library"
                                },
                            enabled = state.canEdit,
                            onClick = onConnection,
                        )
                    )
                    if (state.connected) {
                        add(
                            SyncSetting(
                                title = "Library",
                                detail =
                                    if (state.refreshingCatalog) "Refreshing…"
                                    else
                                        state.catalog?.let {
                                            "${it.tracks.size} songs · ${it.playlists.size} playlists"
                                        } ?: "Load songs and playlists",
                                enabled = !state.saving,
                                onClick = onLibrary,
                            )
                        )
                        add(
                            SyncSetting(
                                title = "Downloads",
                                detail = state.musicLabel,
                                enabled = state.canEdit,
                                onClick = { showDownloadsMenu = true },
                            )
                        )
                        if (state.contentMode == SyncContentMode.SELECTED_PLAYLISTS) {
                            add(
                                SyncSetting(
                                    title = "Choose playlists",
                                    detail =
                                        if (state.refreshingCatalog) "Loading playlists…"
                                        else "${state.selection.size} selected",
                                    enabled = state.canEdit,
                                    onClick = onPlaylists,
                                )
                            )
                        }
                    }
                    add(
                        SyncSetting(
                            title = "Enabled",
                            detail = if (state.enabled) "On" else "Off",
                            enabled = state.canEdit,
                            options = listOf("On", "Off"),
                            selectedOption = if (state.enabled) "On" else "Off",
                            onChoose = { onToggleEnabled(it == "On") },
                        )
                    )
                    add(
                        SyncSetting(
                            title = "Remove library",
                            detail = "Disconnect and delete cached metadata",
                            enabled = !state.saving,
                            onClick = onRemove,
                        )
                    )
                }
            }
        )
    }

    if (showDownloadsMenu) {
        WheelContextMenu(
            title = "Downloads",
            items =
                buildList {
                    SyncContentMode.entries.forEach { mode ->
                        val selected = mode == state.contentMode
                        add(
                            NavigationItem(
                                label = mode.label,
                                subLabel = if (selected) "Selected" else null,
                                checked = selected,
                                enabled = state.canEdit,
                                onClick = {
                                    onMode(mode)
                                    showDownloadsMenu = false
                                },
                            )
                        )
                    }
                    if (state.contentMode != SyncContentMode.NONE || state.hasPinnedMusic) {
                        add(
                            NavigationItem(
                                label = "Download now",
                                subLabel =
                                    when (state.nextStep) {
                                        SyncSetupStep.FOLDER ->
                                            "Restore access to your custom folder"

                                        SyncSetupStep.MUSIC -> "Choose playlists first"
                                        else ->
                                            "Save ${state.musicLabel.lowercase()} for offline playback"
                                    },
                                enabled = state.canEdit,
                                onClick = {
                                    showDownloadsMenu = false
                                    onDownload()
                                },
                            )
                        )
                    }
                },
            onDismiss = { showDownloadsMenu = false },
        )
    }
}
