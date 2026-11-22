package com.pxr.cymatic.ui.screens.settings.sync

import androidx.compose.runtime.Composable
import com.pxr.cymatic.R
import com.pxr.cymatic.sync.RemoteProvider
import com.pxr.cymatic.ui.components.pixelIcon

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
                    icon = pixelIcon(R.drawable.ic_pixel_server),
                    onClick = { onSelect(RemoteProvider.AUDIOSTREAM) },
                ),
                SyncSetting(
                    title = "Subsonic",
                    detail = "Subsonic compatible API like Navidrome",
                    icon = pixelIcon(R.drawable.ic_pixel_stream),
                    onClick = { onSelect(RemoteProvider.SUBSONIC) },
                ),
            )
        )
    }
}
