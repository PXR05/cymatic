package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.ui.components.primitives.CymaticDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDialogButton

@Composable
fun AlbumArtistContextMenu(
    title: String,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit
) {
    CymaticDialog(
        title = title,
        onDismissRequest = onDismiss,
        maxHeightRatio = 0.7f,
        widthRatio = 0.8f,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                ContextMenuAction(
                    label = "Play",
                    onClick = {
                        onPlay()
                        onDismiss()
                    }
                )
                ContextMenuAction(
                    label = "Play Next",
                    onClick = {
                        onPlayNext()
                        onDismiss()
                    }
                )
                ContextMenuAction(
                    label = "Add to Queue",
                    onClick = {
                        onAddToQueue()
                        onDismiss()
                    }
                )
            }
        },
        buttons = {
            CymaticDialogButton(
                text = "Cancel",
                onClick = onDismiss,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    )
}

@Composable
private fun ContextMenuAction(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 16.dp,
                horizontal = 24.dp
            )
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = null
            )
    )
}
