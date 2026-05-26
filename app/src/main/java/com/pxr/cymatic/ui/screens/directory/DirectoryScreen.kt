package com.pxr.cymatic.ui.screens.directory

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

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.pxr.cymatic.ui.components.common.PermissionDeniedState
import com.pxr.cymatic.ui.components.common.LoadingState
import com.pxr.cymatic.ui.components.common.EmptyState
import com.pxr.cymatic.ui.components.common.ErrorState
import com.pxr.cymatic.ui.components.common.hasStoragePermission

@Composable
fun DirectoryScreen(
    directory: String,
    modifier: Modifier = Modifier,
    scrollTargetId: Long? = null,
    viewModel: DirectoryViewModel = viewModel()
) {
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current
    val context = LocalContext.current
    val dirName = File(directory).name.substringAfterLast(':').ifEmpty { "/" }

    var hasPermission by remember { mutableStateOf(hasStoragePermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.loadDirectory(directory)
        }
    }

    LaunchedEffect(directory) {
        viewModel.loadDirectory(directory)
    }

    val directories by viewModel.directories.collectAsState()
    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val items = directories.map { dir ->
        val encodedPath = URLEncoder.encode(dir.path, "UTF-8")
        NavigationItem(dir.name) {
            navController.navigate("directory/$encodedPath")
        }
    }

    BaseScreen(
        title = dirName,
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {
        if (!hasPermission) {
            PermissionDeniedState(
                onGrantClick = {
                    permissionLauncher.launch(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_AUDIO
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                    )
                }
            )
        } else if (errorMessage != null) {
            ErrorState(
                message = errorMessage ?: "Unknown error",
                onRetry = { viewModel.loadDirectory(directory) }
            )
        } else if (isLoading) {
            LoadingState()
        } else if (directories.isEmpty() && files.isEmpty()) {
            EmptyState(
                title = "Folder is Empty",
                message = "No audio files or folders were found in this directory.",
                iconText = "( ! )"
            )
        } else {
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
}
