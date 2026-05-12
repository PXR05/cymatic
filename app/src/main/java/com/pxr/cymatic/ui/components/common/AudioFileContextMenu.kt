package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.R
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.ui.components.primitives.PixelDialog
import com.pxr.cymatic.ui.components.primitives.PixelDialogButton

@Composable
fun AudioFileContextMenu(
    audioFile: AudioFile,
    onDismiss: () -> Unit,
    onPlay: (AudioFile) -> Unit,
    onAddToPlaylist: (AudioFile) -> Unit = {},
    onTrackInfo: (AudioFile) -> Unit,
) {
    val title = audioFile.metadata.title ?: "Unknown Title"

    PixelDialog(
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
                        onPlay(audioFile)
                        onDismiss()
                    }
                )
                ContextMenuAction(
                    label = "Add to Playlist",

                    onClick = {
                        onAddToPlaylist(audioFile)
                        onDismiss()
                    }
                )
                ContextMenuAction(
                    label = "Track Info",

                    onClick = {
                        onTrackInfo(audioFile)
                        onDismiss()
                    }
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
private fun ContextMenuAction(
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
