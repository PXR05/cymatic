package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.data.media.Playlist
import com.pxr.cymatic.data.model.LibraryAvailability
import com.pxr.cymatic.sync.LibraryOfflineManager
import com.pxr.cymatic.ui.components.list.NavigationItem
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
    val context = LocalContext.current
    val keepOffline = {
        val sourceId = playlist.sourceId
        val remoteId = playlist.remoteId
        if (sourceId != null && remoteId != null)
            LibraryOfflineManager.keepPlaylist(
                context,
                sourceId,
                remoteId,
                playlist.name,
            )
        else LibraryOfflineManager.keepPlaylist(context, playlist.id, playlist.name)
        onDismiss()
    }
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
            },
        )
        return
    }

    if (showDeleteConfirm) {
        CymaticDialog(
            title = "Delete \"${playlist.name}\"?",
            onDismissRequest = { showDeleteConfirm = false },
            content = {
                Text(
                    text = "This will permanently delete the playlist.",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            },
            buttons = {
                CymaticDialogButton(
                    text = "Cancel",
                    onClick = { showDeleteConfirm = false },
                    color = MaterialTheme.colorScheme.secondary,
                )
                CymaticDialogButton(
                    text = "Delete",
                    onClick = {
                        onDelete(playlist.id)
                        onDismiss()
                    },
                    color = MaterialTheme.colorScheme.error,
                )
            },
        )
        return
    }

    val wheel = checkNotNull(LocalWheelNavigation.current)
    WheelContextMenu(
        title = playlist.name,
        items =
            listOfNotNull(
                NavigationItem(
                    "Play",
                    onClick = {
                        onPlay()
                        onDismiss()
                        wheel.onPlaybackRequested()
                    },
                ),
                NavigationItem(
                    "Play Next",
                    onClick = {
                        onPlayNext()
                        onDismiss()
                    },
                ),
                NavigationItem(
                    "Add to Queue",
                    onClick = {
                        onAddToQueue()
                        onDismiss()
                    },
                ),
                if (playlist.availability != LibraryAvailability.OFFLINE)
                    NavigationItem("Keep offline", onClick = keepOffline)
                else null,
                if (playlist.sourceId == null)
                    NavigationItem(
                        "Rename",
                        onClick = { showRenameDialog = true },
                    )
                else null,
                if (playlist.sourceId == null)
                    NavigationItem(
                        "Delete",
                        onClick = { showDeleteConfirm = true },
                    )
                else null,
            ),
        onDismiss = onDismiss,
    )
}
