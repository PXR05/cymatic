package com.pxr.cymatic.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pxr.cymatic.sync.RemotePlaylist
import com.pxr.cymatic.ui.components.common.WheelContextMenu
import com.pxr.cymatic.ui.components.common.WheelSettingsOverlay
import com.pxr.cymatic.ui.components.common.WheelTextEditor
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList

internal data class WheelSetting(
    val title: String,
    val value: String? = null,
    val enabled: Boolean = true,
    val options: List<String> = emptyList(),
    val onChoose: (String) -> Unit = {},
    val onClick: () -> Unit = {}
)

@Composable
internal fun WheelSettingsList(settings: List<WheelSetting>) {
    var choiceTitle by remember { mutableStateOf<String?>(null) }
    NavigationList(settings.map { setting ->
        NavigationItem(setting.title, setting.value, enabled = setting.enabled) {
            if (setting.options.isEmpty()) setting.onClick() else choiceTitle = setting.title
        }
    })
    settings.firstOrNull { it.title == choiceTitle }?.let { setting ->
        WheelContextMenu(setting.title, setting.options.map { option ->
            NavigationItem(option, if (option == setting.value) "Selected" else null) {
                setting.onChoose(option)
                choiceTitle = null
            }
        }, { choiceTitle = null })
    }
}

@Composable
internal fun WheelSyncConnection(
    initialUrl: String,
    initialUsername: String,
    hasSavedPassword: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    var field by remember { mutableStateOf<String?>(null) }
    WheelSettingsOverlay("AudioStream", onDismiss) {
        NavigationList(listOf(
            NavigationItem("Server URL", url) { field = "Server URL" },
            NavigationItem("Username", username) { field = "Username" },
            NavigationItem("Password", if (password.isNotEmpty()) "New password entered" else if (hasSavedPassword) "Saved" else "Required") { field = "Password" },
            NavigationItem("Save", enabled = url.isNotBlank()) { onSave(url, username, password) },
            NavigationItem("Cancel", onClick = onDismiss)
        ))
    }
    field?.let { title ->
        WheelTextEditor(title, when (title) { "Server URL" -> url; "Username" -> username; else -> password },
            password = title == "Password", onSave = {
                when (title) { "Server URL" -> url = it; "Username" -> username = it; else -> password = it }
            }, onDismiss = { field = null })
    }
}

@Composable
internal fun WheelSyncPlaylists(
    playlists: List<RemotePlaylist>,
    initialSelection: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var selection by remember(playlists, initialSelection) {
        mutableStateOf(initialSelection.intersect(playlists.mapTo(mutableSetOf()) { it.id }))
    }
    WheelSettingsOverlay("Offline playlists", onDismiss) {
        NavigationList(buildList {
            add(NavigationItem("Save selection", "${selection.size} selected") { onSave(selection) })
            add(NavigationItem(if (selection.size == playlists.size) "Clear all" else "Select all", key = "selection") {
                selection = if (selection.size == playlists.size) emptySet() else playlists.mapTo(mutableSetOf()) { it.id }
            })
            playlists.forEach { playlist ->
                add(NavigationItem(playlist.name.ifBlank { "Unnamed playlist" },
                    "${if (playlist.id in selection) "Selected" else "Unselected"} · ${playlist.trackIds.size} tracks", key = playlist.id) {
                    selection = if (playlist.id in selection) selection - playlist.id else selection + playlist.id
                })
            }
            add(NavigationItem("Cancel", onClick = onDismiss))
        })
    }
}
