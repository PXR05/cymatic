package com.pxr.cymatic.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation
import com.pxr.cymatic.ui.components.common.LocalWheelOverlay
import com.pxr.cymatic.ui.components.common.WheelActions
import com.pxr.cymatic.ui.components.common.WheelSettingsOverlay

private class WheelDialogAction(val click: () -> Unit)

private class WheelDialogActions {
    val items = mutableStateListOf<WheelDialogAction>()
    var selected by mutableIntStateOf(0)

    fun move(steps: Int) {
        selected = (selected + steps).coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }

    fun select() {
        items.getOrNull(selected)?.click?.invoke()
    }
}

private val LocalWheelDialogActions = staticCompositionLocalOf<WheelDialogActions?> { null }

@Composable
fun CymaticDialog(
    title: String,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    buttons: @Composable RowScope.() -> Unit,
) {
    WheelDialogPage(title, onDismissRequest, content, buttons)
}

@Composable
private fun WheelDialogPage(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    buttons: @Composable RowScope.() -> Unit,
) {
    WheelSettingsOverlay(title, onDismiss) {
        val wheel = LocalWheelNavigation.current
        val overlay = LocalWheelOverlay.current
        val dialogActions = remember { WheelDialogActions() }
        val actions =
            remember(dialogActions) {
                WheelActions(dialogActions::move, dialogActions::select)
            }
        DisposableEffect(wheel, overlay, actions) {
            wheel?.register(actions, overlay)
            onDispose { wheel?.unregister(actions) }
        }
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth().weight(1f).clipToBounds().padding(vertical = 12.dp),
                content = content,
            )
            CompositionLocalProvider(LocalWheelDialogActions provides dialogActions) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    content = buttons,
                )
            }
        }
    }
}

@Composable
fun RowScope.CymaticDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
    weight: Float = 1f,
) {
    val wheelActions = LocalWheelDialogActions.current
    val click by rememberUpdatedState(onClick)
    val action = remember { WheelDialogAction { click() } }
    DisposableEffect(wheelActions, action) {
        wheelActions?.items?.add(action)
        onDispose { wheelActions?.items?.remove(action) }
    }
    val selected =
        wheelActions != null && wheelActions.items.getOrNull(wheelActions.selected) === action
    Text(
        text = text,
        color = color ?: MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        modifier =
            modifier
                .weight(weight)
                .then(
                    if (selected) Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    else Modifier
                )
                .clickable(
                    onClick = onClick,
                    indication = null,
                    interactionSource = null,
                )
                .then(
                    if (wheelActions != null) Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                    else Modifier
                ),
    )
}

@Composable
fun CymaticDialogDivider() {
    Text(
        text = "|",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp,
    )
}
