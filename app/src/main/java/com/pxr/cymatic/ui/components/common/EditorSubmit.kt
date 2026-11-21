package com.pxr.cymatic.ui.components.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

internal fun Modifier.submitOnEnter(onSubmit: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.key != Key.Enter && event.key != Key.NumPadEnter) {
        false
    } else {
        if (event.type == KeyEventType.KeyUp) onSubmit()
        true
    }
}

@Composable
internal fun WheelEditorSubmit(onSubmit: () -> Unit) {
    val wheel = LocalWheelNavigation.current
    val overlay = LocalWheelOverlay.current
    val submit by rememberUpdatedState(onSubmit)
    val actions = remember { WheelActions(onRotate = {}, onSelect = { submit() }) }
    DisposableEffect(wheel, actions, overlay) {
        wheel?.register(actions, overlay)
        onDispose { wheel?.unregister(actions) }
    }
}

@Composable
internal fun EditorSubmitHint(action: String, keyboardSubmit: Boolean = true) {
    val control = if (keyboardSubmit) "center or Enter" else "center"
    Text(
        "Press $control to $action",
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.bodySmall,
    )
}
