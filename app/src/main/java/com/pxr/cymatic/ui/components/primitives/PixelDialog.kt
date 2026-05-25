package com.pxr.cymatic.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun PixelDialog(
    title: String,
    onDismissRequest: () -> Unit,
    maxHeightRatio: Float = 0.9f,
    widthRatio: Float = 0.8f,
    content: @Composable ColumnScope.() -> Unit,
    buttons: @Composable RowScope.() -> Unit
) {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current
    val dialogWidth = with(window) { (containerSize.width * widthRatio) }
    val dialogWidthDp = with(density) { dialogWidth.toDp() }
    val dialogHeight = with(window) { (containerSize.height * maxHeightRatio) }
    val dialogHeightDp = with(density) { dialogHeight.toDp() }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.secondary)
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 24.dp)
                .width(dialogWidthDp)
                .heightIn(max = dialogHeightDp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            content()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                buttons()
            }
        }
    }
}

@Composable
fun RowScope.PixelDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
    weight: Float = 1f
) {
    Text(
        text = text,
        color = color ?: MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .weight(weight)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = null
            )
    )
}

@Composable
fun PixelDialogDivider() {
    Text(
        text = "|",
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp
    )
}
