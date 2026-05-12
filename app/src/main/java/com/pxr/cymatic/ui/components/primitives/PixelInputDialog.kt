package com.pxr.cymatic.ui.components.primitives

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PixelInputDialog(
    fontFamily: FontFamily,
    title: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    PixelDialog(
        title = title,
        onDismissRequest = onDismiss,
        content = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(hint, fontFamily = fontFamily, fontSize = 14.sp) },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.secondary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.secondary,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        },
        buttons = {
            PixelDialogButton(
                text = "Cancel",
                fontFamily = fontFamily,
                onClick = onDismiss
            )
            PixelDialogDivider(fontFamily = fontFamily)
            PixelDialogButton(
                text = "OK",
                fontFamily = fontFamily,
                onClick = onConfirm
            )
        }
    )
}
