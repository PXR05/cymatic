package com.pxr.cymatic.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pxr.cymatic.sync.SyncContentMode
import com.pxr.cymatic.sync.SyncLayout
import com.pxr.cymatic.sync.SyncNetwork

@Composable
internal fun SyncBulkDownloadsPage(
    state: LibrarySyncSettingsState,
    onDismiss: () -> Unit,
    onMode: (SyncContentMode) -> Unit,
    onPlaylists: () -> Unit,
    onDownload: () -> Unit,
    onSchedule: () -> Unit,
) {
    SyncSettingsPage("Bulk downloads", onDismiss) {
        SyncSettingsList(buildList {
            add(
                SyncSetting(
                    "Music to download", state.contentMode.label, state.canEdit,
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
    onBulkDownloads: () -> Unit,
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
                    "Bulk downloads", state.musicLabel, state.canEdit,
                    onClick = onBulkDownloads,
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
    SyncReadingPage("Remote library help", onDismiss) {
        SyncHelpSection(
            "Connect and listen",
            "Enter your AudioStream server address, username and password in Connection.",
            "Connecting adds songs, albums and playlists to your library without downloading audio. Tap a song to stream it. Playback uses its offline copy when one is available.",
        )
        SyncHelpSection(
            "Keep music offline",
            "Long press a song, playlist, album or artist and choose Keep offline. You can add more items while downloads are running.",
            "A small animated icon marks queued or processing music. Open Downloads to check progress, stop downloads or retry them.",
            "Hold Menu in the library and choose Show → Offline only to browse downloaded music. Choose Not offline to find music that still needs downloading.",
        )
        SyncHelpSection(
            "Library icons",
            "Download arrow: available offline. Half-filled circle: partially offline. Cloud: stream only.",
            "The animated loading icon returns to the availability icon when processing ends.",
        )
        SyncHelpSection(
            "Bulk and automatic downloads",
            "Advanced → Bulk downloads lets you download all music or selected playlists. Choose in library keeps individual Keep offline selections in control.",
            "When offline music is first enabled, automatic downloads default to a daily Wi-Fi check. Change the network and frequency in Advanced → Automatic downloads.",
        )
        SyncHelpSection(
            "Download storage",
            "Cymatic manages storage automatically. Advanced contains custom folders and folder layout settings.",
            "Cymatic storage is private to this app and is removed when you uninstall it. Changing storage leaves existing files in place and may download another copy.",
        )
        SyncHelpSection(
            "One-way sync",
            "Library changes are imported from AudioStream. Local files and playlist edits are not uploaded. Refreshing may replace local edits to server playlists.",
        )
    }
}

@Composable
private fun SyncHelpSection(title: String, vararg paragraphs: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        paragraphs.forEach { Text(it) }
    }
}
