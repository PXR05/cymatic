package com.pxr.cymatic.ui.screens.library

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.playback.handleItemClick
import com.pxr.cymatic.ui.components.common.AudioFileList
import com.pxr.cymatic.ui.components.common.BaseScreen
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController

@Composable
fun ArtistSongsScreen(
    artistName: String,
    audioFiles: List<AudioFile>,
    modifier: Modifier = Modifier
) {
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current
    val filteredFiles = filterByArtist(audioFiles, artistName)

    BaseScreen(
        title = artistName,
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
                        queueSource = "artist/${Uri.encode(artistName)}"
                    )
                }
            }
        )
    }
}


