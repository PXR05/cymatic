package com.pxr.cymatic.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pxr.cymatic.sync.SyncContentMode
import com.pxr.cymatic.sync.SyncLayout
import com.pxr.cymatic.sync.SyncNetwork

@Composable
internal fun SyncMusicPage(
    state: LibrarySyncSettingsState,
    onDismiss: () -> Unit,
    onMode: (SyncContentMode) -> Unit,
    onPlaylists: () -> Unit,
    onDownload: () -> Unit,
    onSchedule: () -> Unit,
) {
    SyncSettingsPage("Keep offline", onDismiss) {
        SyncSettingsList(buildList {
            add(
                SyncSetting(
                    "Keep offline", state.contentMode.label, state.canEdit,
                    SyncContentMode.entries.map { it.label },
                    onChoose = { value -> onMode(SyncContentMode.entries.first { it.label == value }) },
                )
            )
            if (state.contentMode == SyncContentMode.SELECTED_PLAYLISTS) {
                add(
                    SyncSetting(
                        "Choose playlists",
                        if (state.refreshingCatalog) "Loading playlists…" else "${state.selection.size} selected",
                        enabled = state.canEdit,
                        onClick = onPlaylists,
                    )
                )
            }
            if (state.contentMode != SyncContentMode.NONE || state.hasPinnedMusic) {
                add(
                    SyncSetting(
                        "Automatic downloads",
                        if (state.network == SyncNetwork.NEVER) "Off · download manually"
                        else "${syncNetworkLabel(state.network)} · ${syncIntervals[state.interval] ?: "Every ${state.interval} hours"}",
                        onClick = onSchedule,
                    )
                )
                add(
                    SyncSetting(
                        "Download now",
                        if (state.nextStep == SyncSetupStep.FOLDER) "Restore access to your custom folder"
                        else if (state.nextStep == SyncSetupStep.MUSIC) "Choose playlists first"
                        else "Save ${state.musicLabel.lowercase()} for local playback",
                        enabled = state.canEdit,
                        onClick = onDownload,
                    )
                )
            }
            add(
                SyncSetting(
                    "About offline music",
                    "Long press a song or playlist in the library and choose Keep offline. Playback uses the downloaded copy whenever it is available.",
                    enabled = false,
                )
            )
        })
    }
}

@Composable
internal fun SyncAdvancedPage(
    state: LibrarySyncSettingsState,
    onDismiss: () -> Unit,
    onFolder: () -> Unit,
    onManagedStorage: () -> Unit,
    onLayout: (SyncLayout) -> Unit,
    onSchedule: () -> Unit,
) {
    SyncSettingsPage("Advanced", onDismiss) {
        SyncSettingsList(buildList {
            add(
                SyncSetting(
                    "Custom download folder",
                    if (state.directory.isBlank()) "Managed by Cymatic"
                    else if (!state.folderAccessible) "Custom folder · access lost"
                    else syncFolderLabel(state.directory),
                    state.canEdit,
                    onClick = onFolder,
                )
            )
            if (state.directory.isNotBlank()) add(
                SyncSetting(
                    "Use Cymatic storage",
                    "No folder permission needed",
                    state.canEdit,
                    onClick = onManagedStorage,
                )
            )
            add(
                SyncSetting(
                    "Folder layout", state.layout.label, state.canEdit,
                    SyncLayout.entries.map { it.label },
                    onChoose = { value -> onLayout(SyncLayout.entries.first { it.label == value }) },
                )
            )
            add(
                SyncSetting(
                    "Automatic downloads", syncNetworkLabel(state.network), state.canEdit,
                    onClick = onSchedule
                )
            )
        })
    }
}

@Composable
internal fun SyncSchedulePage(
    state: LibrarySyncSettingsState,
    onDismiss: () -> Unit,
    onSetup: () -> Unit,
    onNetwork: (SyncNetwork) -> Unit,
    onInterval: (Long) -> Unit,
) {
    SyncSettingsPage("Automatic downloads", onDismiss) {
        Column(Modifier.fillMaxSize()) {
            SyncSettingsList(
                buildList {
                    if (state.nextStep != null)
                        add(
                            SyncSetting(
                                "Choose offline music",
                                "Select music before enabling automatic downloads",
                                enabled = state.canEdit && !state.running,
                                onClick = onSetup,
                            )
                        )
                    add(
                        SyncSetting(
                            "Allowed network",
                            syncNetworkLabel(state.network),
                            state.canEdit &&
                                    (state.nextStep == null || state.network != SyncNetwork.NEVER),
                            SyncNetwork.entries.map(::syncNetworkLabel),
                            onChoose = { value ->
                                onNetwork(
                                    SyncNetwork.entries.first { syncNetworkLabel(it) == value }
                                )
                            },
                        )
                    )
                    if (state.network != SyncNetwork.NEVER)
                        add(
                            SyncSetting(
                                "Check every",
                                syncIntervals[state.interval] ?: "Every ${state.interval} hours",
                                state.canEdit,
                                syncIntervals.values.toList(),
                                onChoose = { value ->
                                    onInterval(
                                        syncIntervals.entries.first { it.value == value }.key
                                    )
                                },
                            )
                        )
                },
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun SyncHelpPage(onDismiss: () -> Unit) {
    SyncReadingPage("Remote library", onDismiss) {
        Text("Connect your library")
        Text("Enter your AudioStream server address and login. Connecting adds songs and playlists to your app library without downloading audio. Tap a song to stream it; playback uses an offline copy when one is available.")
        Text("Keep music offline")
        Text("Long press a song or playlist and choose Keep offline. Cymatic manages storage automatically. You can also download all music or selected playlists here.")
        Text("When you first enable offline music, automatic downloads use Wi-Fi with a daily check. Change this in Advanced → Automatic downloads.")
        Text("Storage and downloads")
        Text("Advanced contains custom folders, folder layout, and automatic downloads. Existing custom folders stay selected. Cymatic storage is private to this app and is removed if you uninstall it. Changing storage leaves existing files in place and may download another copy.")
        Text("Library icons")
        Text("The download icon means available offline. The cloud with a download arrow means partially offline. The cloud means stream only. Choosing items in the library keeps their offline selections, including when you change the bulk download setting.")
    }
}
