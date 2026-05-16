package com.pxr.cymatic.ui.components.primitives

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PixelConfirmDialog(
    fontFamily: FontFamily,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    PixelDialog(
        title = "Confirm",
        onDismissRequest = onDismiss,
        content = {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontFamily = fontFamily,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        },
        buttons = {
            PixelDialogButton(
                text = "Cancel",
                onClick = onDismiss
            )
            PixelDialogDivider(fontFamily = fontFamily)
            PixelDialogButton(
                text = "Delete",
                onClick = onConfirm,
                color = MaterialTheme.colorScheme.error
            )
        }
    )
}
