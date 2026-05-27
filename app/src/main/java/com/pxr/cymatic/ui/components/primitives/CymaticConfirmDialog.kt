package com.pxr.cymatic.ui.components.primitives

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CymaticConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    CymaticDialog(
        title = "Confirm",
        onDismissRequest = onDismiss,
        content = {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        },
        buttons = {
            CymaticDialogButton(
                text = "Cancel",
                onClick = onDismiss
            )
            CymaticDialogDivider()
            CymaticDialogButton(
                text = "Delete",
                onClick = onConfirm,
                color = MaterialTheme.colorScheme.error
            )
        }
    )
}
