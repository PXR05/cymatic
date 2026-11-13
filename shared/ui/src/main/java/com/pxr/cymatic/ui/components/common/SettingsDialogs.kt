package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.primitives.CymaticConfirmDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDialogButton

@Composable
fun SettingsReadingDialog(
    title: String,
    onDismiss: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    if (LocalWheelNavigation.current != null) WheelReadingPage(title, onDismiss, content = content)
    else
        CymaticDialog(
            title = title,
            onDismissRequest = onDismiss,
            content = {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(contentPadding)) {
                    content()
                }
            },
            buttons = { CymaticDialogButton("Close", onDismiss) },
        )
}

@Composable
fun SettingsChoicesDialog(title: String, onDismiss: () -> Unit, items: List<NavigationItem>) {
    if (LocalWheelNavigation.current != null) WheelContextMenu(title, items, onDismiss)
    else
        CymaticDialog(
            title = title,
            onDismissRequest = onDismiss,
            content = { NavigationList(items) },
            buttons = { CymaticDialogButton("Close", onDismiss) },
        )
}

@Composable
fun SettingsConfirmationDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (LocalWheelNavigation.current != null)
        WheelContextMenu(
            title,
            listOf(NavigationItem("Confirm", text, subLabelMaxLines = 8, onClick = onConfirm)),
            onDismiss,
        )
    else
        CymaticConfirmDialog(
            message = "$title\n\n$text",
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
}
