package com.pxr.cymatic.setup

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.ui.components.common.EditorSubmitHint
import com.pxr.cymatic.ui.components.common.submitOnEnter
import com.pxr.cymatic.ui.components.list.ListItem

@Composable
internal fun SetupListItem(
    label: String,
    selected: Boolean,
    trailing: String? = null,
    subLabel: String? = null,
    onClick: () -> Unit,
) {
    ListItem(
        modifier =
            Modifier
                .heightIn(min = if (subLabel == null) 52.dp else 64.dp)
                .revealWhenSelected(selected),
        label = label,
        subLabel = subLabel,
        trailing = trailing,
        isActive = selected,
        onClick = onClick,
    )
}

@Composable
private fun Modifier.revealWhenSelected(selected: Boolean): Modifier {
    val bringIntoView = remember { BringIntoViewRequester() }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(selected, placed) { if (selected && placed) bringIntoView.bringIntoView() }
    return bringIntoViewRequester(bringIntoView).onGloballyPositioned { placed = true }
}

@Composable
internal fun SetupField(state: MusicSetupState, model: MusicSetupViewModel, onSubmit: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var visible by remember { mutableStateOf(false) }
    val password = state.step == MusicSetupStep.PASSWORD
    val value =
        when (state.step) {
            MusicSetupStep.ADDRESS -> state.address
            MusicSetupStep.USERNAME -> state.username
            else -> state.password
        }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    OutlinedTextField(
        value = value,
        onValueChange =
            when (state.step) {
                MusicSetupStep.ADDRESS -> model::editAddress
                MusicSetupStep.USERNAME -> model::editUsername
                else -> model::editPassword
            },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .submitOnEnter(onSubmit)
            .focusRequester(focus),
        shape = RectangleShape,
        placeholder = {
            Text(
                when (state.step) {
                    MusicSetupStep.ADDRESS -> "https://music.example.com"
                    MusicSetupStep.USERNAME -> "Username"
                    else -> "Password"
                },
                fontSize = 13.sp,
            )
        },
        visualTransformation =
            if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    when (state.step) {
                        MusicSetupStep.ADDRESS -> KeyboardType.Uri
                        MusicSetupStep.PASSWORD -> KeyboardType.Password
                        else -> KeyboardType.Text
                    },
                imeAction = if (password) ImeAction.Done else ImeAction.Next,
            ),
        keyboardActions = KeyboardActions(onNext = { onSubmit() }, onDone = { onSubmit() }),
        supportingText = { EditorSubmitHint(if (password) "connect" else "continue") },
        trailingIcon =
            if (password) {
                {
                    TextButton(onClick = { visible = !visible }) {
                        Text(
                            if (visible) "Hide" else "Show",
                            fontSize = 12.sp,
                        )
                    }
                }
            } else null,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                unfocusedBorderColor = MaterialTheme.colorScheme.secondary,
            ),
    )
}
