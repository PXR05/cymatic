package com.pxr.cymatic.setup

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SetupChoice(
    title: String,
    detail: String?,
    selected: Boolean,
    folder: Boolean = false,
    onClick: () -> Unit,
) {
    val foreground = MaterialTheme.colorScheme.onBackground
    val background = MaterialTheme.colorScheme.background
    val bringIntoView = remember { BringIntoViewRequester() }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(selected, placed) { if (selected && placed) bringIntoView.bringIntoView() }
    val textColor = if (selected) background else foreground
    Row(
        Modifier.fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .onGloballyPositioned { placed = true }
            .background(if (selected) foreground else background)
            .border(1.dp, foreground.copy(alpha = if (selected) 0.8f else 0.2f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (folder)
            Canvas(Modifier.size(20.dp)) {
                val unit = size.width / 20f
                drawRect(
                    textColor,
                    Offset(2 * unit, 5 * unit),
                    Size(16 * unit, 12 * unit),
                    style = Stroke(2 * unit),
                )
                drawRect(textColor, Offset(2 * unit, 3 * unit), Size(7 * unit, 4 * unit))
            }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = textColor, fontSize = 15.sp, lineHeight = 21.sp)
            if (detail != null)
                Text(
                    detail,
                    color =
                        if (selected) background.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.secondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
        }
        if (folder) Text(">", color = textColor, fontSize = 18.sp)
    }
}

@Composable
internal fun SetupField(state: MusicSetupState, model: MusicSetupViewModel) {
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
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
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
        keyboardActions =
            KeyboardActions(
                onNext = { model.next() },
                onDone = {
                    keyboard?.hide()
                    model.next()
                },
            ),
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
