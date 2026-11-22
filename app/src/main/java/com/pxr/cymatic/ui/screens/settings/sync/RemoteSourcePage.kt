package com.pxr.cymatic.ui.screens.settings.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pxr.cymatic.R
import com.pxr.cymatic.sync.SyncContentMode
import com.pxr.cymatic.ui.components.common.WheelContextMenu
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.pixelIcon

private val streamingQualityOptions =
    listOf(
        "Original" to (false to 192),
        "MP3 128 kbps on mobile data" to (true to 128),
        "MP3 192 kbps on mobile data" to (true to 192),
        "MP3 256 kbps on mobile data" to (true to 256),
        "MP3 320 kbps on mobile data" to (true to 320),
    )

@Composable
internal fun RemoteSourcePage(
    state: RemoteSourceState,
    onDismiss: () -> Unit,
    onConnection: () -> Unit,
    onLibrary: () -> Unit,
    onMode: (SyncContentMode) -> Unit,
    onPlaylists: () -> Unit,
    onDownload: () -> Unit,
    onStreamingQuality: (Boolean, Int) -> Unit,
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
                            icon = pixelIcon(if (state.connected) R.drawable.ic_pixel_link else R.drawable.ic_pixel_key),
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
                                icon = pixelIcon(R.drawable.ic_pixel_music_note),
                                onClick = onLibrary,
                            )
                        )
                        add(
                            SyncSetting(
                                title = "Downloads",
                                detail = state.musicLabel,
                                enabled = state.canEdit,
                                icon = pixelIcon(R.drawable.ic_pixel_download),
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
                                    icon = pixelIcon(R.drawable.ic_pixel_playlist),
                                    onClick = onPlaylists,
                                )
                            )
                        }
                        if (state.provider.supportsTranscoding) {
                            val transcode = state.config?.transcodeOnMetered == true
                            val bitrate = state.config?.transcodeBitrateKbps ?: 192
                            val currentLabel =
                                if (!transcode) "Original"
                                else "MP3 $bitrate kbps on mobile data"
                            add(
                                SyncSetting(
                                    title = "Streaming quality",
                                    detail = currentLabel,
                                    enabled = state.canEdit,
                                    options = streamingQualityOptions.map { it.first },
                                    selectedOption = currentLabel,
                                    icon = pixelIcon(R.drawable.ic_pixel_wifi),
                                    onChoose = { chosen ->
                                        val (metered, kbps) =
                                            streamingQualityOptions.first { it.first == chosen }.second
                                        onStreamingQuality(metered, kbps)
                                    },
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
                            icon = pixelIcon(R.drawable.ic_pixel_power),
                            onChoose = { onToggleEnabled(it == "On") },
                        )
                    )
                    add(
                        SyncSetting(
                            title = "Remove library",
                            detail = "Disconnect and delete cached metadata",
                            enabled = !state.saving,
                            icon = pixelIcon(R.drawable.ic_pixel_delete),
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
                                icon = pixelIcon(R.drawable.ic_pixel_download),
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
