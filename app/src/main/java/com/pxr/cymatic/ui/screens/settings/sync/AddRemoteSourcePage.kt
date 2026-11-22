package com.pxr.cymatic.ui.screens.settings.sync

import androidx.compose.runtime.Composable
import com.pxr.cymatic.sync.RemoteProvider

@Composable
internal fun AddRemoteSourcePage(
    onDismiss: () -> Unit,
    onSelect: (RemoteProvider) -> Unit,
) {
    SyncSettingsPage("Add library", onDismiss) {
        SyncSettingsList(
            listOf(
                SyncSetting(
                    title = "AudioStream",
                    detail = "Self-hosted AudioStream server",
                    onClick = { onSelect(RemoteProvider.AUDIOSTREAM) },
                ),
                SyncSetting(
                    title = "Subsonic",
                    detail = "Subsonic compatible API like Navidrome",
                    onClick = { onSelect(RemoteProvider.SUBSONIC) },
                ),
            )
        )
    }
}
