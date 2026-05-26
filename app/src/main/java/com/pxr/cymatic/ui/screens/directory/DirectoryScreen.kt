package com.pxr.cymatic.ui.screens.directory

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.playback.handleItemClick
import com.pxr.cymatic.ui.components.list.AudioFileList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import java.io.File
import java.net.URLEncoder

@Composable
fun DirectoryScreen(
    directory: String,
    modifier: Modifier = Modifier,
    scrollTargetId: Long? = null,
    viewModel: DirectoryViewModel = viewModel()
) {
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current
    val dirName = File(directory).name.substringAfterLast(':').ifEmpty { "/" }

    LaunchedEffect(directory) {
        viewModel.loadDirectory(directory)
    }

    val directories by viewModel.directories.collectAsState()
    val files by viewModel.files.collectAsState()

    val items = directories.map { dir ->
        val encodedPath = URLEncoder.encode(dir.path, "UTF-8")
        NavigationItem(dir.name) {
            navController.navigate("directory/$encodedPath")
        }
    }

    Log.d("DirectoryScreen", "Directory: $directory")
    Log.d("DirectoryScreen", "Directories: ${directories.map { it.name }}")
    Log.d("DirectoryScreen", "Files: ${files.mapNotNull { it.metadata.title }}")

    BaseScreen(
        title = dirName,
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {
        NavigationList(
            items = items,
            modifier = Modifier
        )
        AudioFileList(
            audioFiles = files,
            scrollTargetId = scrollTargetId,
            onItemClick = { audioFile ->
                mediaController?.let {
                    handleItemClick(
                        mediaController = it,
                        audioFile,
                        queue = files,
                        queueSource = "directory/${directory.toUri()}"
                    )
                }
            }
        )
    }
}
