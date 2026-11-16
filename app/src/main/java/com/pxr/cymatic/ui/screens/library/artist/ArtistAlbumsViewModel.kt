package com.pxr.cymatic.ui.screens.library.artist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.library.UnknownArtist
import com.pxr.cymatic.data.library.albumDisplayName
import com.pxr.cymatic.data.library.filterByArtist
import com.pxr.cymatic.data.library.libraryRefreshEvents
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.store.LibraryFilterStore
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

class ArtistAlbumsViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    private val repository = AudioRepository.getInstance(application)
    private var currentArtist: String? =
        savedStateHandle.get<String>("artistName")?.takeIf(String::isNotBlank) ?: UnknownArtist
    private val initialCached = repository.getCachedAudio()
    private var hasLoaded = initialCached != null && currentArtist != null
    private val _uiState =
        MutableStateFlow(
            if (initialCached != null && currentArtist != null) {
                buildState(initialCached, currentArtist!!, isLoading = false)
            } else {
                ArtistAlbumsState()
            }
        )
    val uiState: StateFlow<ArtistAlbumsState> = _uiState

    init {
        viewModelScope.launch {
            libraryRefreshEvents().collect {
                currentArtist?.let { load(it, force = true) }
            }
        }
    }

    fun load(artistName: String, force: Boolean = false) {
        if (!force && currentArtist == artistName && hasLoaded) return
        currentArtist = artistName
        repository.getCachedAudio()?.let { cached ->
            _uiState.value = buildState(cached, artistName, isLoading = false)
            hasLoaded = true
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (!hasLoaded) {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            }
            runCatching { repository.getAllAudio() }
                .onSuccess {
                    hasLoaded = true
                    _uiState.value = buildState(it, artistName, isLoading = false)
                }
                .onFailure {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            errorMessage = it.message ?: "Failed to load albums",
                        )
                }
        }
    }

    private fun buildState(
        songs: List<AudioFile>,
        artistName: String,
        isLoading: Boolean,
    ): ArtistAlbumsState {
        val artistSongs = filterByArtist(LibraryFilterStore.audio(songs), artistName)
        val albums =
            artistSongs
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
