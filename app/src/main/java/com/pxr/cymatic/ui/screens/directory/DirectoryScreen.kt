package com.pxr.cymatic.ui.screens.directory

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.playback.handleItemClick
import com.pxr.cymatic.ui.components.common.AudioFileList
import com.pxr.cymatic.ui.components.common.BaseScreen
import com.pxr.cymatic.ui.components.common.NavigationItem
import com.pxr.cymatic.ui.components.common.NavigationList
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import java.io.File
import java.net.URLEncoder

@Composable
fun DirectoryScreen(
    directory: String,
    audioFiles: List<AudioFile>,
    modifier: Modifier = Modifier,
    scrollTargetId: Long? = null,
) {
    val navController = LocalNavController.current
    val mediaController = LocalMediaController.current
    val context = LocalContext.current
    val dirName = File(directory).name.substringAfterLast(':').ifEmpty { "/" }

    // get items in path
    // directories on top, then files
    // directories use navigation list, files use audio file list

    val directories = remember(directory) {
        buildNavigationItems(context, navController, directory)
    }
    val files = remember(directory, audioFiles) {
        buildAudioFilesForDirectory(context, directory, audioFiles)
    }

    Log.d("DirectoryScreen", "Directory: $directory")
    Log.d("DirectoryScreen", "Directories: ${directories.map { it.label }}")
    Log.d("DirectoryScreen", "Files: ${files.mapNotNull { it.metadata.title }}")

    BaseScreen(
        title = dirName,
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {
        NavigationList(
            items = directories,
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

private data class ChildDocument(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean
)

private fun buildNavigationItems(
    context: Context,
    navController: androidx.navigation.NavController,
    directory: String
): List<NavigationItem> {
    val encodedTargets = mutableListOf<NavigationItem>()
    if (directory.startsWith("content://")) {
        val treeUri = directory.toUri()
        val children = queryChildDocuments(context, treeUri)
        val dirs = children.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
        for (child in dirs) {
            val encodedPath = URLEncoder.encode(child.uri.toString(), "UTF-8")
            encodedTargets += NavigationItem(child.name) {
                navController.navigate("directory/$encodedPath")
            }
        }
    } else {
        val file = File(directory)
        val children = file.listFiles().orEmpty()
        val dirs = children.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
        for (child in dirs) {
            val encodedPath = URLEncoder.encode(child.path, "UTF-8")
            encodedTargets += NavigationItem(child.name) {
                navController.navigate("directory/$encodedPath")
            }
        }
    }
    return encodedTargets
}

private fun buildAudioFilesForDirectory(
    context: Context,
    directory: String,
    audioFiles: List<AudioFile>
): List<AudioFile> {
    if (directory.startsWith("content://")) {
        val relativePath = directory.toRelativePath() ?: return emptyList()
        val ids = queryAudioIdsForRelativePath(context, relativePath)
        return audioFiles
            .filter { ids.contains(it.id) }
            .sortedBy { it.metadata.title?.lowercase() ?: "" }
    }

    val directoryFile = File(directory)
    return audioFiles
        .filter { it.uri.scheme == "file" }
        .filter { audioFile ->
            val path = audioFile.uri.path ?: return@filter false
            File(path).parentFile == directoryFile
        }
        .sortedBy { it.metadata.title?.lowercase() ?: "" }
}

private fun queryChildDocuments(
    context: Context,
    treeUri: Uri
): List<ChildDocument> {
    val results = mutableListOf<ChildDocument>()
    val docId = DocumentsContract.getTreeDocumentId(treeUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE
    )

    context.contentResolver.query(
        childrenUri,
        projection,
        null,
        null,
        null
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

        while (cursor.moveToNext()) {
            val childId = cursor.getString(idColumn)
            val name = cursor.getString(nameColumn)
            val mime = cursor.getString(mimeColumn)
            val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
            val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
            results += ChildDocument(childUri, name, isDirectory)
        }
    }

    return results
}

private fun queryAudioIdsForRelativePath(
    context: Context,
    relativePath: String
): Set<Long> {
    val ids = mutableSetOf<Long>()
    val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val projection = arrayOf(MediaStore.Audio.Media._ID)
    val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
    val args = arrayOf(relativePath)

    context.contentResolver.query(
        collection,
        projection,
        selection,
        args,
        null
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        while (cursor.moveToNext()) {
            ids += cursor.getLong(idColumn)
        }
    }

    return ids
}

private fun String.toRelativePath(): String? {
    return runCatching {
        val uri = toUri()
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(':', limit = 2)
        if (parts.size < 2) return@runCatching null
        val path = parts[1]
        if (path.isBlank()) null else if (path.endsWith("/")) path else "$path/"
    }.getOrNull()
}
