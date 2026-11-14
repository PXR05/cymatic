package com.pxr.cymatic.ui.components.primitives

import androidx.compose.runtime.Composable
import com.pxr.cymatic.ui.components.common.WheelTextEditor

@Composable
fun CymaticInputDialog(
    title: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    WheelTextEditor(
        title,
        value,
        onSave = {
            onValueChange(it)
            onConfirm()
        },
        onDismiss = onDismiss,
        hint = hint,
    )
}
