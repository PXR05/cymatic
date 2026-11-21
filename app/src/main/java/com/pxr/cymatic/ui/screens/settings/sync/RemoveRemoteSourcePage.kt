package com.pxr.cymatic.ui.screens.settings.sync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun RemoveRemoteSourcePage(
    state: RemoteSourceState,
    onDismiss: () -> Unit,
    onRemove: (deleteDownloads: Boolean) -> Unit,
) {
    val hasCustomFolder = state.directory.isNotBlank()
    SyncSettingsPage("Remove ${state.displayName}", onDismiss) {
        Column(Modifier.fillMaxSize()) {
            SyncHint(
                buildString {
                    append("Songs from this library will be removed from Cymatic and from any local playlists.")
                    if (hasCustomFolder) {
                        append(" You can keep or delete files saved in your custom folder.")
                    }
                }
            )
            SyncSettingsList(
                buildList {
                    if (hasCustomFolder) {
                        add(
                            SyncSetting(
                                title = "Remove, keep downloaded files",
                                detail = "Downloaded files in your folder are kept",
                                enabled = !state.saving,
                                onClick = { onRemove(false) },
                            )
                        )
                        add(
                            SyncSetting(
                                title = "Remove library and downloads",
                                detail = "Deletes downloaded files for this library",
                                enabled = !state.saving,
                                onClick = { onRemove(true) },
                            )
                        )
                    } else {
                        add(
                            SyncSetting(
                                title = if (state.saving) "Removing…" else "Remove library",
                                detail = "Removes this library and its Cymatic downloads",
                                enabled = !state.saving,
                                onClick = { onRemove(true) },
                            )
                        )
                    }
                    add(
                        SyncSetting(
                            title = "Cancel",
                            enabled = !state.saving,
                            onClick = onDismiss,
                        )
                    )
                },
                Modifier.weight(1f),
            )
        }
    }
}
