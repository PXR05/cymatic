package com.pxr.cymatic.ui.components.list

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
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.ui.components.common.AddToPlaylistDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDialogButton

@Composable
fun AudioFileContextMenu(
    audioFile: AudioFile,
    onDismiss: () -> Unit,
    onPlay: (AudioFile) -> Unit,
    onPlayNext: (AudioFile) -> Unit,
    onAddToQueue: (AudioFile) -> Unit,
    onTrackInfo: (AudioFile) -> Unit,
) {
    val title = audioFile.metadata.title ?: "Unknown Title"

    var showPlaylistPicker by remember { mutableStateOf(false) }

    if (showPlaylistPicker) {
        AddToPlaylistDialog(
            audioId = audioFile.id,
            onDismiss = {
                showPlaylistPicker = false
                onDismiss()
            }
        )
        return
    }

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
                    iconRes = R.drawable.ic_pixel_play,
                    label = "Play",
                    onClick = {
                        onPlay(audioFile)
                        onDismiss()
                    }
                )
                ContextMenuAction(
                    iconRes = R.drawable.ic_pixel_next,
                    label = "Play Next",
                    onClick = {
                        onPlayNext(audioFile)
                        onDismiss()
                    }
                )
                ContextMenuAction(
                    iconRes = R.drawable.ic_pixel_queue,
                    label = "Add to Queue",
                    onClick = {
                        onAddToQueue(audioFile)
                        onDismiss()
                    }
                )
                ContextMenuAction(
                    iconRes = R.drawable.ic_pixel_add_playlist,
                    label = "Add to Playlist",
                    onClick = { showPlaylistPicker = true }
                )
                ContextMenuAction(
                    iconRes = R.drawable.ic_pixel_info,
                    label = "Track Info",
                    onClick = {
                        onTrackInfo(audioFile)
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
