package com.pxr.cymatic.ui.screens.settings.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.sync.RemoteProvider
import com.pxr.cymatic.sync.isCleartextPublicUrl
import com.pxr.cymatic.sync.normalizeCandidateUrls
import com.pxr.cymatic.ui.components.common.EditorSubmitHint
import com.pxr.cymatic.ui.components.common.LoadingState
import com.pxr.cymatic.ui.components.common.ScreenBackHandler
import com.pxr.cymatic.ui.components.common.WheelEditorSubmit
import com.pxr.cymatic.ui.components.common.WheelTextEditor
import com.pxr.cymatic.ui.components.common.submitOnEnter
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

private enum class AddConnectionStep {
    ADDRESS,
    USERNAME,
    PASSWORD,
    NAME,
}

@Composable
internal fun AddRemoteSourceConnectionPage(
    provider: RemoteProvider,
    saving: Boolean,
    running: Boolean,
    onDismiss: () -> Unit,
    onConnect: (url: String, username: String, password: String, displayName: String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var url by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    val step = AddConnectionStep.entries[stepIndex.coerceIn(0, AddConnectionStep.entries.lastIndex)]
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }

    val canAdvance =
        !saving &&
                !running &&
                when (step) {
                    AddConnectionStep.ADDRESS -> runCatching { normalizeCandidateUrls(url) }.isSuccess
                    AddConnectionStep.USERNAME -> username.isNotBlank()
                    AddConnectionStep.PASSWORD -> password.isNotEmpty()
                    AddConnectionStep.NAME -> true
                }

    fun goBack() {
        if (saving) return
        keyboard?.hide()
        focus.clearFocus()
        if (stepIndex > 0) stepIndex-- else onDismiss()
    }

    fun submitStep() {
        if (!canAdvance) return
        when (step) {
            AddConnectionStep.ADDRESS,
            AddConnectionStep.USERNAME,
            AddConnectionStep.PASSWORD -> {
                stepIndex++
            }

            AddConnectionStep.NAME -> {
                keyboard?.hide()
                focus.clearFocus()
                onConnect(url, username, password, displayName)
            }
        }
    }

    ScreenBackHandler(onBack = ::goBack)

    val title =
        if (saving) "Connecting…"
        else
            when (step) {
                AddConnectionStep.ADDRESS -> "Server address (1/4)"
                AddConnectionStep.USERNAME -> "Username (2/4)"
                AddConnectionStep.PASSWORD -> "Password (3/4)"
                AddConnectionStep.NAME -> "Library name (4/4)"
            }

    SyncSettingsPage(title, ::goBack) {
        if (saving) {
            LoadingState()
        } else {
            WheelEditorSubmit(::submitStep)
            LaunchedEffect(step) {
                requester.requestFocus()
                keyboard?.show()
            }
            val currentValue =
                when (step) {
                    AddConnectionStep.ADDRESS -> url
                    AddConnectionStep.USERNAME -> username
                    AddConnectionStep.PASSWORD -> password
                    AddConnectionStep.NAME -> displayName
                }
            val defaultName =
                runCatching {
                    syncHostLabel(normalizeCandidateUrls(url).first())
                }.getOrDefault(provider.label)
            val instruction =
                when (step) {
                    AddConnectionStep.ADDRESS ->
                        "Enter your ${provider.label} server address."

                    AddConnectionStep.USERNAME ->
                        "Enter the username for your ${provider.label} account."

                    AddConnectionStep.PASSWORD ->
                        "Enter the password for your ${provider.label} account."

                    AddConnectionStep.NAME ->
                        "Optional name shown in Cymatic. Leave blank to use $defaultName."
                }
            val label =
                when (step) {
                    AddConnectionStep.ADDRESS -> "Server address"
                    AddConnectionStep.USERNAME -> "Username"
                    AddConnectionStep.PASSWORD -> "Password"
                    AddConnectionStep.NAME -> "Name"
                }
            val hint =
                when (step) {
                    AddConnectionStep.ADDRESS -> "music.example.com"
                    AddConnectionStep.USERNAME -> "Username"
                    AddConnectionStep.PASSWORD -> "Password"
                    AddConnectionStep.NAME -> defaultName
                }
            val isPassword = step == AddConnectionStep.PASSWORD
            val actionVerb = if (step == AddConnectionStep.NAME) "connect" else "continue"

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = instruction,
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 13.sp,
                )
                if (step == AddConnectionStep.ADDRESS && isCleartextPublicUrl(url)) {
                    Text(
                        text = "Your password will be sent unencrypted over HTTP.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
                OutlinedTextField(
                    value = currentValue,
                    label = { Text(label) },
                    placeholder = { Text(hint) },
                    onValueChange = { updated ->
                        when (step) {
                            AddConnectionStep.ADDRESS -> url = updated
                            AddConnectionStep.USERNAME -> username = updated
                            AddConnectionStep.PASSWORD -> onPasswordChange(updated)
                            AddConnectionStep.NAME -> displayName = updated
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .submitOnEnter(::submitStep)
                            .focusRequester(requester),
                    singleLine = true,
                    shape = RectangleShape,
                    supportingText = { EditorSubmitHint(actionVerb) },
                    visualTransformation =
                        if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                when (step) {
                                    AddConnectionStep.ADDRESS -> KeyboardType.Uri
                                    AddConnectionStep.PASSWORD -> KeyboardType.Password
                                    else -> KeyboardType.Text
                                },
                            imeAction =
                                if (step == AddConnectionStep.NAME) ImeAction.Done
                                else ImeAction.Next,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onNext = { submitStep() },
                            onDone = { submitStep() },
                        ),
                )
            }
        }
    }
}

@Composable
internal fun SyncConnectionPage(
    provider: RemoteProvider,
    initialUrl: String,
    initialUsername: String,
    initialDisplayName: String,
    hasSavedCredentials: Boolean,
    saving: Boolean,
    running: Boolean,
    connected: Boolean,
    onDismiss: () -> Unit,
    onSave: (url: String, username: String, password: String, displayName: String) -> Unit,
    onDisplayNameSave: (String) -> Unit,
    onCheck: (() -> Unit)?,
    password: String,
    onPasswordChange: (String) -> Unit,
) {
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var username by rememberSaveable(initialUsername) { mutableStateOf(initialUsername) }
    var displayName by rememberSaveable(initialDisplayName) { mutableStateOf(initialDisplayName) }
    var field by remember { mutableStateOf<String?>(null) }
    val nav = LocalNavController.current
    fun isValid(candidateUrl: String, candidateUser: String, candidatePass: String): Boolean {
        val okUrl = runCatching { normalizeCandidateUrls(candidateUrl) }.isSuccess
        val canKeep = hasSavedCredentials && candidateUser.trim() == initialUsername.trim()
        return okUrl && candidateUser.isNotBlank() && (candidatePass.isNotEmpty() || canKeep)
    }

    val keepPassword = hasSavedCredentials && username.trim() == initialUsername.trim()
    val enabled = !saving && !running
    val cleartextWarning = isCleartextPublicUrl(url)

    SyncSettingsPage(provider.label, onDismiss) {
        Column(Modifier.fillMaxSize()) {
            if (cleartextWarning) {
                SyncHint("Your password is sent unencrypted over HTTP.")
            }
            SyncSettingsList(
                buildList {
                    add(
                        SyncSetting(
                            "Name",
                            displayName.ifBlank { initialDisplayName.ifBlank { provider.label } },
                            enabled,
                            onClick = { field = "Name" },
                        )
                    )
                    add(
                        SyncSetting(
                            "Server address",
                            url.ifBlank { "Required" },
                            enabled,
                            onClick = { field = "Server address" },
                        )
                    )
                    add(
                        SyncSetting(
                            "Username",
                            username.ifBlank { "Required" },
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
                    if (saving) {
                        add(SyncSetting("Saving…", enabled = false))
                    } else if (connected && onCheck != null) {
                        add(
                            SyncSetting(
                                "Check saved connection",
                                enabled = enabled,
                                onClick = onCheck,
                            )
                        )
                    }
                    add(SyncSetting("Help", onClick = { nav.navigate(Screen.SyncHelp.route) }))
                },
                Modifier.weight(1f),
            )
        }
    }
    field?.let { title ->
        WheelTextEditor(
            title = title,
            value =
                when (title) {
                    "Name" -> displayName
                    "Server address" -> url
                    "Username" -> username
                    else -> password
                },
            password = title == "Password",
            hint =
                when (title) {
                    "Name" -> "Display name"
                    "Server address" -> "music.example.com"
                    "Username" -> "Enter username"
                    else -> if (keepPassword) "Leave blank to keep saved" else "Enter password"
                },
            instruction =
                when (title) {
                    "Name" -> "Name shown for this library in Cymatic."
                    "Server address" -> "Enter the server address."
                    "Username" -> "Enter the username for your ${provider.label} account."
                    else ->
                        if (keepPassword) "Enter a new password, or leave blank to keep the saved one."
                        else "Enter the password for your ${provider.label} account."
                },
            onSave = { newValue ->
                val preservedName = displayName.ifBlank { initialDisplayName }
                when (title) {
                    "Name" -> {
                        displayName = newValue
                        if (newValue.trim() != initialDisplayName.trim()) {
                            onDisplayNameSave(newValue)
                        }
                    }

                    "Server address" -> {
                        url = newValue
                        if (newValue.trim() != initialUrl.trim() &&
                            isValid(newValue, username, password)
                        ) {
                            onSave(newValue, username, password, preservedName)
                        }
                    }

                    "Username" -> {
                        username = newValue
                        if (newValue.trim() != initialUsername.trim() &&
                            isValid(url, newValue, password)
                        ) {
                            onSave(url, newValue, password, preservedName)
                        }
                    }

                    else -> {
                        onPasswordChange(newValue)
                        if (newValue.isNotEmpty() && isValid(url, username, newValue)) {
                            onSave(url, username, newValue, preservedName)
                        }
                    }
                }
            },
            onDismiss = { field = null },
        )
    }
}
