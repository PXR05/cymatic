package com.pxr.cymatic.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation
import com.pxr.cymatic.ui.components.common.WheelContextMenu
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList

@Composable
internal fun SyncPlaylistsPage(
    state: LibrarySyncSettingsState,
    onDismiss: () -> Unit,
    onChange: (Set<String>) -> Unit,
    onRefresh: () -> Unit,
) {
    var showActions by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val playlists = state.catalog?.playlists.orEmpty()
    val selection = state.selection
    val enabled = state.canEdit
    val ids = playlists.mapTo(mutableSetOf()) { it.id }
    val tracks =
        playlists.filter { it.id in selection }.flatMapTo(mutableSetOf()) { it.trackIds }.size
    val wheel = LocalWheelNavigation.current != null
    SyncSettingsPage("Offline playlists", onDismiss) {
        Column(Modifier.fillMaxSize()) {
            if (playlists.isEmpty()) {
                SyncSettingsList(
                    listOf(
                        SyncSetting(
                            if (state.refreshingCatalog) "Loading…" else "Reload playlists",
                            if (state.catalog == null)
                                "Connect to AudioStream to load your playlists"
                            else "No playlists found",
                            enabled = state.connected && !state.running && !state.saving,
                            onClick = onRefresh,
                        )
                    ),
                    Modifier.weight(1f),
                )
            } else {
                NavigationList(
                    playlists.map { playlist ->
                        NavigationItem(
                            playlist.name.ifBlank { "Unnamed playlist" },
                            "${if (playlist.id in selection) "Selected" else "Not selected"} · ${playlist.trackIds.size} tracks",
                            enabled = enabled,
                            key = playlist.id,
                            checked = playlist.id in selection,
                            onLongClick = { showActions = true },
                            onClick = {
                                onChange(
                                    if (playlist.id in selection) selection - playlist.id
                                    else selection + playlist.id
                                )
                            },
                        )
                    },
                    Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.secondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (state.saving) "Saving…" else "${selection.size} selected · $tracks tracks",
                    fontSize = 12.sp,
                    modifier =
                        Modifier.weight(1f).padding(start = 16.dp, top = 14.dp, bottom = 14.dp),
                )
                PlaylistAction("All", enabled && playlists.isNotEmpty()) { onChange(ids) }
                PlaylistAction("None", enabled && selection.isNotEmpty()) { onChange(emptySet()) }
                if (!wheel) PlaylistAction("Done", !state.saving, onDismiss)
            }
        }
    }
    if (showActions && wheel)
        WheelContextMenu(
            "Playlists",
            listOf(
                NavigationItem("Select all", enabled = enabled) {
                    onChange(ids)
                    showActions = false
                },
                NavigationItem("Clear selection", enabled = enabled) {
                    onChange(emptySet())
                    showActions = false
                },
                NavigationItem("Reload playlists", enabled = !state.running && !state.saving) {
                    onRefresh()
                    showActions = false
                },
                NavigationItem("Done", enabled = !state.saving) {
                    showActions = false
                    onDismiss()
                },
                NavigationItem("Help") {
                    showActions = false
                    showHelp = true
                },
            ),
            { showActions = false },
        )
    if (showHelp) SyncHelpPage { showHelp = false }
}

@Composable
private fun PlaylistAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        color =
            if (enabled) MaterialTheme.colorScheme.onBackground
            else MaterialTheme.colorScheme.secondary,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(14.dp),
    )
}
