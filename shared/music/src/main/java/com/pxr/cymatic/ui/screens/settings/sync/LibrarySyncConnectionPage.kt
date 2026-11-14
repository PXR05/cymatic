package com.pxr.cymatic.ui.screens.settings.sync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pxr.cymatic.sync.AudioStreamClient
import com.pxr.cymatic.sync.RemoteLibrarySource
import com.pxr.cymatic.ui.components.common.WheelTextEditor
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

@Composable
internal fun SyncConnectionPage(
    state: LibrarySyncSettingsState,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onCheck: () -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(state.url) }
    var username by rememberSaveable { mutableStateOf(state.username) }
    var field by remember { mutableStateOf<String?>(null) }
    val nav = LocalNavController.current
    val normalized = runCatching { AudioStreamClient.normalizeBaseUrl(url) }.getOrNull()
    val sameAccount =
        normalized != null &&
            runCatching {
                    RemoteLibrarySource("audiostream", normalized, username).id ==
                        RemoteLibrarySource("audiostream", state.url, state.username).id
                }
                .getOrDefault(false)
    val keepPassword = state.hasPassword && sameAccount
    val valid =
        normalized != null && username.isNotBlank() && (password.isNotEmpty() || keepPassword)
    val enabled = !state.saving && !state.running
    SyncSettingsPage("AudioStream", onDismiss) {
        Column(Modifier.fillMaxSize()) {
            SyncSettingsList(
                buildList {
                    add(
                        SyncSetting(
                            "Server address",
                            url,
                            enabled,
                            onClick = { field = "Server address" },
                        )
                    )
                    add(
                        SyncSetting(
                            "Username",
                            username,
                            enabled,
                            onClick = { field = "Username" },
                        )
                    )
                    add(
                        SyncSetting(
                            "Password",
                            when {
                                password.isNotEmpty() -> "New password entered"
                                keepPassword -> "Saved · leave blank to keep"
                                else -> "Required"
                            },
                            enabled,
                            onClick = { field = "Password" },
                        )
                    )
                    add(
                        SyncSetting(
                            if (state.saving) "Connecting…" else "Connect",
                            enabled = enabled && valid,
                            onClick = { onSave(url, username, password) },
                        )
                    )
                    if (state.connected)
                        add(
                            SyncSetting(
                                "Check saved connection",
                                enabled = enabled,
                                onClick = onCheck,
                            )
                        )
                    add(SyncSetting("Help", onClick = { nav.navigate(Screen.SyncHelp.route) }))
                },
                Modifier.weight(1f),
            )
        }
    }
    field?.let { title ->
        WheelTextEditor(
            title,
            when (title) {
                "Server address" -> url
                "Username" -> username
                else -> password
            },
            password = title == "Password",
            onSave = {
                when (title) {
                    "Server address" -> url = it
                    "Username" -> username = it
                    else -> onPasswordChange(it)
                }
            },
            onDismiss = { field = null },
        )
    }
}
