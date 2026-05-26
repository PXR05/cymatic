package com.pxr.cymatic.ui.screens.library

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.media.Playlist
import com.pxr.cymatic.data.media.PlaylistRepository
import com.pxr.cymatic.data.model.AudioFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlaylistSongsViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val repository = PlaylistRepository.getInstance(application)
    private val playlistId: Long = savedStateHandle.get<String>("playlistId")?.toLongOrNull() ?: -1L

    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist

    private val _audioFiles = MutableStateFlow<List<AudioFile>>(emptyList())
    val audioFiles: StateFlow<List<AudioFile>> = _audioFiles
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    var contextMenuFile by mutableStateOf<AudioFile?>(null)
    var infoDialogId by mutableStateOf<Long?>(null)

    init {
        val cachedPlaylist = repository.getCachedPlaylists()?.find { it.id == playlistId }
        val cachedAudio = repository.getCachedPlaylistAudio(playlistId)
        if (cachedPlaylist != null && cachedAudio != null) {
            _playlist.value = cachedPlaylist
            _audioFiles.value = cachedAudio
            _isLoading.value = false
        }
        if (playlistId != -1L) {
            loadPlaylist(playlistId)
        }
    }

    fun loadPlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_audioFiles.value.isEmpty()) {
                _isLoading.value = true
            }
            _errorMessage.value = null
            try {
                _playlist.value = repository.getPlaylist(playlistId)
                _audioFiles.value = repository.getPlaylistAudio(playlistId)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load playlist songs"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
