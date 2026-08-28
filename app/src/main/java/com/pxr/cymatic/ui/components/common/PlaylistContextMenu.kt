package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.R
import com.pxr.cymatic.data.media.Playlist
import com.pxr.cymatic.ui.components.primitives.CymaticDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDialogButton
import com.pxr.cymatic.ui.components.primitives.CymaticInputDialog

@Composable
fun PlaylistContextMenu(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onRename: (playlistId: Long, newName: String) -> Unit,
    onDelete: (playlistId: Long) -> Unit,
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(playlist.name) }

    if (showRenameDialog) {
        CymaticInputDialog(
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
        CymaticDialog(
            title = "Delete \"${playlist.name}\"?",
            onDismissRequest = { showDeleteConfirm = false },
            maxHeightRatio = 0.7f,
            widthRatio = 0.8f,
            content = {
                Text(
                    text = "This will permanently delete the playlist.",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            },
            buttons = {
                CymaticDialogButton(
                    text = "Cancel",
                    onClick = { showDeleteConfirm = false },
                    color = MaterialTheme.colorScheme.secondary
                )
                CymaticDialogButton(
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

    CymaticDialog(
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
                    iconRes = R.drawable.ic_pixel_play,
                    label = "Play",
                    onClick = {
                        onPlay()
                        onDismiss()
                    }
                )
                PlaylistContextMenuAction(
                    iconRes = R.drawable.ic_pixel_next,
                    label = "Play Next",
                    onClick = {
                        onPlayNext()
                        onDismiss()
                    }
                )
                PlaylistContextMenuAction(
                    iconRes = R.drawable.ic_pixel_queue,
                    label = "Add to Queue",
                    onClick = {
                        onAddToQueue()
                        onDismiss()
                    }
                )
                PlaylistContextMenuAction(
                    iconRes = R.drawable.ic_pixel_edit,
                    label = "Rename",
                    onClick = { showRenameDialog = true }
                )
                PlaylistContextMenuAction(
                    iconRes = R.drawable.ic_pixel_trash,
                    label = "Delete",
                    onClick = { showDeleteConfirm = true }
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
private fun PlaylistContextMenuAction(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (pressed) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
            )
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = interactionSource
            )
            .padding(
                vertical = 14.dp,
                horizontal = 24.dp
            )
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp
        )
    }
}
