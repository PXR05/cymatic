package com.pxr.cymatic.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pxr.cymatic.sync.AudioStreamClient
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation
import com.pxr.cymatic.ui.components.common.WheelTextEditor

@Composable
internal fun SyncConnectionPage(
    state: LibrarySyncSettingsState,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onCheck: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(state.url) }
    var username by rememberSaveable { mutableStateOf(state.username) }
    var password by remember { mutableStateOf("") }
    var field by remember { mutableStateOf<String?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    val normalized = runCatching { AudioStreamClient.normalizeBaseUrl(url) }.getOrNull()
    val sameAccount = normalized == state.url && username.trim() == state.username
    val keepPassword = state.hasPassword && sameAccount
    val valid =
        normalized != null && username.isNotBlank() && (password.isNotEmpty() || keepPassword)
    val enabled = !state.saving && !state.running
    SyncSettingsPage("AudioStream", onDismiss) {
        Column(
            Modifier.fillMaxSize()
                .then(if (LocalWheelNavigation.current == null) Modifier.imePadding() else Modifier)
        ) {
            if (LocalWheelNavigation.current != null) {
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
                                if (state.saving) "Saving…" else "Save & connect",
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
                        add(SyncSetting("Help", onClick = { showHelp = true }))
                    },
                    Modifier.weight(1f),
                )
            } else {
                Column(
                    Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                ) {
                    SyncConnectionField(
                        "Server address",
                        url,
                        { url = it },
                        enabled,
                        KeyboardType.Uri,
                    )
                    SyncHint(
                        "The address you use in your browser, for example https://music.example.com/."
                    )
                    SyncConnectionField("Username", username, { username = it }, enabled)
                    SyncConnectionField(
                        if (keepPassword) "New password (optional)" else "Password",
                        password,
                        { password = it },
                        enabled,
                        KeyboardType.Password,
                        password = true,
                    )
                    if (keepPassword) SyncHint("Leave blank to keep your saved password.")
                    if (state.connected)
                        Text(
                            "Check saved connection",
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = enabled, onClick = onCheck)
                                    .padding(vertical = 16.dp),
                        )
                }
                Text(
                    if (state.saving) "Saving…" else "Save & connect",
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable(enabled = enabled && valid) {
                                onSave(url, username, password)
                            }
                            .padding(16.dp),
                    color =
                        if (enabled && valid) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
    if (showHelp) SyncHelpPage { showHelp = false }
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
                    else -> password = it
                }
            },
            onDismiss = { field = null },
        )
    }
}

@Composable
private fun SyncConnectionField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    OutlinedTextField(
        value,
        onChange,
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation =
            if (password) PasswordVisualTransformation() else VisualTransformation.None,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                unfocusedBorderColor = MaterialTheme.colorScheme.secondary,
            ),
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
}
