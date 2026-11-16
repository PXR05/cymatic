package com.pxr.cymatic.ui.screens.library.playlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.library.libraryRefreshEvents
import com.pxr.cymatic.data.media.Playlist
import com.pxr.cymatic.data.media.PlaylistRepository
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.ui.screens.library.SongListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlaylistSongsViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
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

    private var currentPlaylistId = playlistId
    private var hasLoaded = false

    internal val listState = SongListState()

    init {
        val cachedPlaylist = repository.getCachedPlaylists()?.find { it.id == playlistId }
        val cachedAudio = repository.getCachedPlaylistAudio(playlistId)
        if (cachedPlaylist != null) {
            _playlist.value = cachedPlaylist
        }
        if (cachedAudio != null) {
            _audioFiles.value = LibraryFilterStore.audio(cachedAudio)
            _isLoading.value = false
            hasLoaded = true
        }
        viewModelScope.launch {
            libraryRefreshEvents(includeScans = false).collect {
                if (currentPlaylistId != -1L) loadPlaylist(currentPlaylistId, force = true)
            }
        }
    }

    fun loadPlaylist(playlistId: Long, force: Boolean = false) {
        if (!force && playlistId == currentPlaylistId && hasLoaded) return
        currentPlaylistId = playlistId
        repository.getCachedPlaylists()?.find { it.id == playlistId }?.let {
            _playlist.value = it
        }
        repository.getCachedPlaylistAudio(playlistId)?.let { cached ->
            _audioFiles.value = LibraryFilterStore.audio(cached)
            _isLoading.value = false
            hasLoaded = true
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (!hasLoaded) {
                _isLoading.value = true
            }
            _errorMessage.value = null
            try {
                _playlist.value = repository.getPlaylist(playlistId)
                _audioFiles.value =
                    LibraryFilterStore.audio(repository.getPlaylistAudio(playlistId))
                hasLoaded = true
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load playlist songs"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
