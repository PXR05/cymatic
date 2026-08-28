package com.pxr.cymatic.ui.components.player

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
import androidx.media3.common.MediaItem
import com.pxr.cymatic.R
import com.pxr.cymatic.ui.components.common.AddToPlaylistDialog
import com.pxr.cymatic.ui.components.common.SongInfoDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDialogButton

@Composable
fun QueueItemContextMenu(
    index: Int,
    totalCount: Int,
    mediaItem: MediaItem,
    isCurrent: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onRemove: () -> Unit,
) {
    val title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown Title"
    val audioId = mediaItem.mediaId.toLongOrNull()

    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showSongInfo by remember { mutableStateOf(false) }

    if (showPlaylistPicker && audioId != null) {
        AddToPlaylistDialog(
            audioId = audioId,
            onDismiss = {
                showPlaylistPicker = false
                onDismiss()
            }
        )
        return
    }

    if (showSongInfo && audioId != null) {
        SongInfoDialog(
            mediaId = audioId,
            showDialog = true,
            onDismissRequest = {
                showSongInfo = false
                onDismiss()
            }
        )
        return
    }

    CymaticDialog(
        title = title,
        onDismissRequest = onDismiss,
        maxHeightRatio = 0.8f,
        widthRatio = 0.85f,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                if (!isCurrent) {
                    QueueContextMenuAction(
                        iconRes = R.drawable.ic_pixel_play,
                        label = "Play Now",
                        onClick = {
                            onPlay()
                            onDismiss()
                        }
                    )
                }

                if (index > 0) {
                    QueueContextMenuAction(
                        iconRes = R.drawable.ic_pixel_arrow_up,
                        label = "Move Up",
                        onClick = {
                            onMoveUp()
                            onDismiss()
                        }
                    )
                    if (index > 1) {
                        QueueContextMenuAction(
                            iconRes = R.drawable.ic_pixel_arrow_up,
                            label = "Move to Top",
                            onClick = {
                                onMoveToTop()
                                onDismiss()
                            }
                        )
                    }
                }

                if (index < totalCount - 1) {
                    QueueContextMenuAction(
                        iconRes = R.drawable.ic_pixel_arrow_down,
                        label = "Move Down",
                        onClick = {
                            onMoveDown()
                            onDismiss()
                        }
                    )
                    if (index < totalCount - 2) {
                        QueueContextMenuAction(
                            iconRes = R.drawable.ic_pixel_arrow_down,
                            label = "Move to Bottom",
                            onClick = {
                                onMoveToBottom()
                                onDismiss()
                            }
                        )
                    }
                }

                QueueContextMenuAction(
                    iconRes = R.drawable.ic_pixel_trash,
                    label = "Remove from Queue",
                    onClick = {
                        onRemove()
                        onDismiss()
                    }
                )

                if (audioId != null) {
                    QueueContextMenuAction(
                        iconRes = R.drawable.ic_pixel_add_playlist,
                        label = "Add to Playlist",
                        onClick = { showPlaylistPicker = true }
                    )
                    QueueContextMenuAction(
                        iconRes = R.drawable.ic_pixel_info,
                        label = "Track Info",
                        onClick = { showSongInfo = true }
                    )
                }
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
private fun QueueContextMenuAction(
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
