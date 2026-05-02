package com.pxr.cymatic.ui.screen

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.playback.handleItemClick
import com.pxr.cymatic.ui.components.AudioFileList
import com.pxr.cymatic.ui.components.BaseScreen
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController

@Composable
fun AlbumSongsScreen(
    albumName: String,
    audioFiles: List<AudioFile>,
    modifier: Modifier = Modifier
) {
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current
    val filteredFiles = filterByAlbum(audioFiles, albumName)

    BaseScreen(
        title = albumName,
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {
        AudioFileList(
            audioFiles = filteredFiles,
            onItemClick = { audioFile ->
                mediaController?.let {
                    handleItemClick(
                        mediaController = it,
                        audioFile,
                        queue = filteredFiles,
                        queueSource = "album/${Uri.encode(albumName)}"
                    )
                }
            }
        )
    }
}
