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
            add(SyncSetting(
                "Keep offline", state.contentMode.label, state.canEdit,
                SyncContentMode.entries.map { it.label },
                onChoose = { value -> onMode(SyncContentMode.entries.first { it.label == value }) },
            ))
            if (state.contentMode == SyncContentMode.SELECTED_PLAYLISTS) {
                add(SyncSetting(
                    "Choose playlists",
                    if (state.refreshingCatalog) "Loading playlists…" else "${state.selection.size} selected",
                    enabled = state.canEdit,
                    onClick = onPlaylists,
                ))
            }
            if (state.contentMode != SyncContentMode.NONE) {
                add(SyncSetting(
                    "Automatic downloads",
                    if (state.network == SyncNetwork.NEVER) "Off · download manually"
                    else "${syncNetworkLabel(state.network)} · ${syncIntervals[state.interval] ?: "Every ${state.interval} hours"}",
                    onClick = onSchedule,
                ))
                add(SyncSetting(
                    "Download now",
                    if (state.nextStep == SyncSetupStep.FOLDER) "Restore access to your custom folder"
                    else if (state.nextStep == SyncSetupStep.MUSIC) "Choose playlists first"
                    else "Save ${state.musicLabel.lowercase()} for local playback",
                    enabled = state.canEdit,
                    onClick = onDownload,
                ))
            }
            add(SyncSetting(
                "About offline music",
                "Downloads appear in your local library. Turning this off pauses downloads and keeps existing files.",
                enabled = false,
            ))
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
            add(SyncSetting(
                "Custom download folder",
                if (state.directory.isBlank()) "Managed by Cymatic"
                else if (!state.folderAccessible) "Custom folder · access lost"
                else syncFolderLabel(state.directory),
                state.canEdit,
                onClick = onFolder,
            ))
            if (state.directory.isNotBlank()) add(SyncSetting(
                "Use Cymatic storage", "No folder permission needed", state.canEdit, onClick = onManagedStorage,
            ))
            add(SyncSetting(
                "Folder layout", state.layout.label, state.canEdit,
                SyncLayout.entries.map { it.label },
                onChoose = { value -> onLayout(SyncLayout.entries.first { it.label == value }) },
            ))
            add(SyncSetting("Automatic downloads", syncNetworkLabel(state.network), state.canEdit,
                onClick = onSchedule))
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
        Text("Enter your AudioStream server address and login. Connecting loads the song and playlist catalog without downloading audio. Use Library to browse it or refresh it later.")
        Text("Keep music offline")
        Text("Optional. Choose all music or selected playlists, then Download now. Cymatic manages storage automatically. Downloaded songs appear in your local library for playback. Streaming is not available yet.")
        Text("When you first enable offline music, automatic downloads use Wi-Fi with a daily check. Change this in Advanced → Automatic downloads.")
        Text("Storage and downloads")
        Text("Advanced contains custom folders, folder layout, and automatic downloads. Existing custom folders stay selected. Cymatic storage is private to this app and is removed if you uninstall it. Changing storage leaves existing files in place and may download another copy.")
        Text("Turning downloads off")
        Text("Nothing pauses future downloads and keeps existing files. While downloads are enabled, the next successful download sync can remove files it previously saved when they are no longer selected.")
    }
}
