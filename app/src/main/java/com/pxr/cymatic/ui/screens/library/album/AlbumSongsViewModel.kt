package com.pxr.cymatic.ui.screens.library.album

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.screens.library.filterByAlbum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AlbumSongsViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val repository = AudioRepository.getInstance(application)
    private val albumName: String = Uri.decode(savedStateHandle.get<String>("albumName").orEmpty())

    private val _songs = MutableStateFlow<List<AudioFile>>(emptyList())
    val songs: StateFlow<List<AudioFile>> = _songs
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    var contextMenuFile by mutableStateOf<AudioFile?>(null)
    var infoDialogId by mutableStateOf<Long?>(null)

    init {
        val cached = repository.getCachedAudio()
        if (cached != null) {
            _songs.value = filterByAlbum(cached, albumName)
            _isLoading.value = false
        }
        viewModelScope.launch {
            SettingsStore.lastScanTimeMsFlow.collect {
                loadAlbumSongs(albumName)
            }
        }
    }

    fun loadAlbumSongs(albumName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_songs.value.isEmpty()) {
                _isLoading.value = true
            }
            _errorMessage.value = null
            try {
                val allSongs = repository.getAllAudio()
                _songs.value = filterByAlbum(allSongs, albumName)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load album songs"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
