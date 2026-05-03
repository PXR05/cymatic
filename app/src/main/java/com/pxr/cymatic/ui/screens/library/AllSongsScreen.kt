package com.pxr.cymatic.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.playback.handleItemClick
import com.pxr.cymatic.ui.components.common.AudioFileList
import com.pxr.cymatic.ui.components.common.BaseScreen
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController

@Composable
fun AllSongsScreen(
    audioFiles: List<AudioFile>,
    modifier: Modifier = Modifier
) {
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current

    BaseScreen(
        title = "All Songs",
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {
        AudioFileList(
            audioFiles = audioFiles,
            onItemClick = { audioFile ->
                mediaController?.let {
                    handleItemClick(
                        mediaController = it,
                        audioFile,
                        queue = audioFiles,
                        queueSource = "all_songs"
                    )
                }
            }
        )
    }
}


