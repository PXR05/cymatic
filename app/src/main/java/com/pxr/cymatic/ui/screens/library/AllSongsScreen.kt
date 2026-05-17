package com.pxr.cymatic.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.playback.handleItemClick
import com.pxr.cymatic.ui.components.common.AudioFileContextMenu
import com.pxr.cymatic.ui.components.common.AudioFileList
import com.pxr.cymatic.ui.components.common.BaseScreen
import com.pxr.cymatic.ui.components.common.SongInfoDialog
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController

@Composable
fun AllSongsScreen(
    audioFiles: List<AudioFile>,
    modifier: Modifier = Modifier,
    scrollTargetId: Long? = null
) {
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current
    val queueSource = "all_songs"

    var contextMenuFile by remember { mutableStateOf<AudioFile?>(null) }
    var infoDialogId by remember { mutableStateOf<Long?>(null) }

    BaseScreen(
        title = "All Songs",
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {
        AudioFileList(
            audioFiles = audioFiles,
            scrollTargetId = scrollTargetId,
            onItemClick = { audioFile ->
                mediaController?.let {
                    handleItemClick(
                        mediaController = it,
                        audioFile,
                        queue = audioFiles,
                        queueSource = queueSource
                    )
                }
            },
            onItemLongClick = { audioFile -> contextMenuFile = audioFile }
        )
    }

    contextMenuFile?.let { audioFile ->
        AudioFileContextMenu(
            audioFile = audioFile,
            onDismiss = { contextMenuFile = null },
            onPlay = { file ->
                mediaController?.let {
                    handleItemClick(
                        mediaController = it,
                        audioFile = file,
                        queue = audioFiles,
                        queueSource = queueSource
                    )
                }
            },
            onTrackInfo = { file -> infoDialogId = file.id }
        )
    }

    infoDialogId?.let { id ->
        SongInfoDialog(
            showDialog = true,
            mediaId = id,
            onDismissRequest = { infoDialogId = null }
        )
    }
}


