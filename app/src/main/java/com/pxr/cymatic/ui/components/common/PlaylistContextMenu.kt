package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.R
import com.pxr.cymatic.data.media.Playlist
import com.pxr.cymatic.ui.components.primitives.PixelDialog
import com.pxr.cymatic.ui.components.primitives.PixelDialogButton
import com.pxr.cymatic.ui.components.primitives.PixelInputDialog

@Composable
fun PlaylistContextMenu(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onRename: (playlistId: Long, newName: String) -> Unit,
    onDelete: (playlistId: Long) -> Unit,
) {
    val fontFamily = FontFamily(Font(R.font.pixel))

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(playlist.name) }

    if (showRenameDialog) {
        PixelInputDialog(
            fontFamily = fontFamily,
            title = "Rename Playlist",
            hint = "Playlist name",
            value = renameValue,
            onValueChange = { renameValue = it },
            onConfirm = {
                val name = renameValue.trim()
                if (name.isNotEmpty()) {
                    onRename(playlist.id, name)
                    onDismiss()
                }
            },
            onDismiss = {
                renameValue = playlist.name
                showRenameDialog = false
            }
        )
        return
    }

    if (showDeleteConfirm) {
        PixelDialog(
            title = "Delete \"${playlist.name}\"?",
            onDismissRequest = { showDeleteConfirm = false },
            maxHeightRatio = 0.7f,
            widthRatio = 0.8f,
            content = {
                Text(
                    text = "This will permanently delete the playlist.",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontFamily = fontFamily,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            },
            buttons = {
                PixelDialogButton(
                    text = "Cancel",
                    onClick = { showDeleteConfirm = false },
                    color = MaterialTheme.colorScheme.secondary
                )
                PixelDialogButton(
                    text = "Delete",
                    onClick = {
                        onDelete(playlist.id)
                        onDismiss()
                    },
                    color = MaterialTheme.colorScheme.error
                )
            }
        )
        return
    }

    PixelDialog(
        title = playlist.name,
        onDismissRequest = onDismiss,
        maxHeightRatio = 0.7f,
        widthRatio = 0.8f,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                PlaylistContextMenuAction(
                    label = "Rename",
                    onClick = { showRenameDialog = true }
                )
                PlaylistContextMenuAction(
                    label = "Delete",
                    onClick = { showDeleteConfirm = true }
                )
            }
        },
        buttons = {
            PixelDialogButton(
                text = "Cancel",
                onClick = onDismiss,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    )
}

@Composable
private fun PlaylistContextMenuAction(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp,
        fontFamily = FontFamily(Font(R.font.pixel)),
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
