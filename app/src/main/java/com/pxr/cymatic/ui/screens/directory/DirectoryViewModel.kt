package com.pxr.cymatic.ui.screens.directory

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.store.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DirectoryItem(
    val name: String,
    val path: String
)

class DirectoryViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val repository = AudioRepository.getInstance(application)
    private val directory: String = Uri.decode(savedStateHandle.get<String>("directory").orEmpty())

    private val _directories = MutableStateFlow<List<DirectoryItem>>(emptyList())
    val directories: StateFlow<List<DirectoryItem>> = _directories

    private val _files = MutableStateFlow<List<AudioFile>>(emptyList())
    val files: StateFlow<List<AudioFile>> = _files

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        if (directory.isNotEmpty()) {
            loadDirectory(directory)
        }
        viewModelScope.launch {
            SettingsStore.lastScanTimeMsFlow.collect {
                loadDirectory(directory)
            }
        }
    }

    fun loadDirectory(directory: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val context = getApplication<Application>()

                val dirs = withContext(Dispatchers.IO) {
                    buildNavigationItems(context, directory)
                }
                _directories.value = dirs

                val allAudio = withContext(Dispatchers.IO) {
                    repository.getAllAudio()
                }
                val filteredFiles = withContext(Dispatchers.IO) {
                    buildAudioFilesForDirectory(context, directory, allAudio)
                }
                _files.value = filteredFiles
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load directory"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private data class ChildDocument(
        val uri: Uri,
        val name: String,
        val isDirectory: Boolean
    )

    private fun buildNavigationItems(
        context: Context,
        directory: String
    ): List<DirectoryItem> {
        val targets = mutableListOf<DirectoryItem>()
        if (directory.startsWith("content://")) {
            val treeUri = directory.toUri()
            val children = queryChildDocuments(context, treeUri)
            val dirs = children.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
            for (child in dirs) {
                targets += DirectoryItem(child.name, child.uri.toString())
            }
        } else {
            val file = File(directory)
            val children = file.listFiles().orEmpty()
            val dirs = children.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
            for (child in dirs) {
                targets += DirectoryItem(child.name, child.path)
            }
        }
        return targets
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
}
