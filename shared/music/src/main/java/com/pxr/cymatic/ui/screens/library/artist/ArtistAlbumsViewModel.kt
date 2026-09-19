package com.pxr.cymatic.ui.screens.library.artist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.screens.library.albumDisplayName
import com.pxr.cymatic.ui.screens.library.filterByArtist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ArtistAlbum(val name: String, val trackCount: Int)

data class ArtistAlbumsState(
    val isLoading: Boolean = true,
    val albums: List<ArtistAlbum> = emptyList(),
    val totalTracks: Int = 0,
    val errorMessage: String? = null,
)

class ArtistAlbumsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AudioRepository.getInstance(application)
    private val _uiState = MutableStateFlow(ArtistAlbumsState())
    val uiState: StateFlow<ArtistAlbumsState> = _uiState
    private var currentArtist: String? = null

    init {
        viewModelScope.launch {
            SettingsStore.lastScanTimeMsFlow.collect {
                currentArtist?.let(::load)
            }
        }
    }

    fun load(artistName: String) {
        currentArtist = artistName
        repository.getCachedAudio()?.let { cached ->
            _uiState.value = buildState(cached, artistName, isLoading = false)
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (_uiState.value.albums.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            }
            runCatching { repository.getAllAudio() }
                .onSuccess { _uiState.value = buildState(it, artistName, isLoading = false) }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = it.message ?: "Failed to load albums",
                    )
                }
        }
    }

    private fun buildState(songs: List<AudioFile>, artistName: String, isLoading: Boolean): ArtistAlbumsState {
        val artistSongs = filterByArtist(songs, artistName)
        val albums = artistSongs
            .groupingBy(::albumDisplayName)
            .eachCount()
            .map { (name, count) -> ArtistAlbum(name, count) }
            .sortedBy { it.name.lowercase() }
        return ArtistAlbumsState(
            isLoading = isLoading,
            albums = albums,
            totalTracks = artistSongs.size,
        )
    }
}
