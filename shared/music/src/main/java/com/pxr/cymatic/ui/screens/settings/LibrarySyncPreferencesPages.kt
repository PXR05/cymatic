package com.pxr.cymatic.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pxr.cymatic.sync.SyncContentMode
import com.pxr.cymatic.sync.SyncLayout
import com.pxr.cymatic.sync.SyncNetwork

@Composable
internal fun SyncMusicPage(
    state: LibrarySyncSettingsState,
    onDismiss: () -> Unit,
    onMode: (SyncContentMode) -> Unit,
    onPlaylists: () -> Unit,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onLayout: (SyncLayout) -> Unit,
) {
    SyncSettingsPage("Offline music", onDismiss) {
        Column(Modifier.fillMaxSize()) {
            SyncSettingsList(
                buildList {
                    add(
                        SyncSetting(
                            "Keep offline",
                            state.contentMode.label,
                            state.canEdit,
                            SyncContentMode.entries.map { it.label },
                            onChoose = { value ->
                                onMode(SyncContentMode.entries.first { it.label == value })
                            },
                        )
                    )
                    if (!state.connected)
                        add(
                            SyncSetting(
                                "Connect to AudioStream",
                                "Sign in to load your playlists",
                                state.canEdit,
                                onClick = onConnect,
                            )
                        )
                    if (state.contentMode == SyncContentMode.SELECTED_PLAYLISTS) {
                        add(
                            SyncSetting(
                                "Choose playlists",
                                when {
                                    state.refreshingCatalog -> "Loading playlists…"
                                    state.catalog == null -> "Open to load your playlists"
                                    state.catalog.playlists.isEmpty() ->
                                        "No playlists found · refresh to retry"

                                    else ->
                                        "${state.selection.size} of ${state.catalog.playlists.size} selected"
                                },
                                enabled = state.connected && !state.saving,
                                onClick = onPlaylists,
                            )
                        )
                    }
                    if (state.connected)
                        add(
                            SyncSetting(
                                "Refresh playlists",
                                "Update the list without downloading music",
                                enabled = !state.running && !state.saving,
                                onClick = onRefresh,
                            )
                        )
                    add(
                        SyncSetting(
                            "Folder layout",
                            "${state.layout.label} · ${syncLayoutExample(state.layout)}",
                            state.canEdit,
                            SyncLayout.entries.map { it.label },
                            selectedOption = state.layout.label,
                            onChoose = { value ->
                                onLayout(SyncLayout.entries.first { it.label == value })
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
internal fun SyncSchedulePage(
    state: LibrarySyncSettingsState,
    onDismiss: () -> Unit,
    onSetup: () -> Unit,
    onNetwork: (SyncNetwork) -> Unit,
    onInterval: (Long) -> Unit,
) {
    SyncSettingsPage("Automatic sync", onDismiss) {
        Column(Modifier.fillMaxSize()) {
            SyncSettingsList(
                buildList {
                    if (state.nextStep != null)
                        add(
                            SyncSetting(
                                "Finish setup",
                                "Set up offline music before enabling automatic sync",
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
    SyncReadingPage("How sync works", onDismiss) {
        Text("1. Connect")
        Spacer(Modifier.height(8.dp))
        Text(
            "Use the AudioStream web address and the username and password. Save & connect loads the playlist list without downloading music."
        )
        Spacer(Modifier.height(16.dp))
        Text("2. Download folder")
        Spacer(Modifier.height(8.dp))
        Text(
            "Choose a dedicated folder on this device and allow access. Downloaded music is added to your local library for offline playback."
        )
        Spacer(Modifier.height(16.dp))
        Text("3. Offline music")
        Spacer(Modifier.height(8.dp))
        Text(
            "Choose all music or selected playlists. Playlist choices save automatically, Menu or Back returns to settings."
        )
        Spacer(Modifier.height(16.dp))
        Text("Start a sync")
        Spacer(Modifier.height(8.dp))
        Text(
            "Sync downloads your choices. Later syncs skip unchanged files. Files previously downloaded by sync can be removed on the next sync when they are no longer selected."
        )
        Spacer(Modifier.height(16.dp))
        Text("Automatic sync")
        Spacer(Modifier.height(8.dp))
        Text(
            "Optional. Choose an allowed network and how often to check. Wi-Fi only avoids downloads over mobile data."
        )
    }
}
