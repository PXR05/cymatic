package com.pxr.cymatic.ui.screens.library.album

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.screens.library.albumDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AlbumsState(
    val isLoading: Boolean = true,
    val albums: List<String> = emptyList(),
    val errorMessage: String? = null
)

class AlbumsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AudioRepository.getInstance(application)

    private val initialAlbums = repository.getCachedAudio()?.let { songs ->
        songs.map(::albumDisplayName).distinct().sortedBy { it.lowercase() }
    }

    private val _albums = MutableStateFlow<List<String>?>(initialAlbums)
    private val _isLoading = MutableStateFlow(initialAlbums == null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    var searchQuery by mutableStateOf("")
        private set
    var isSearchActive by mutableStateOf(false)
        private set

    private val _searchQueryFlow = MutableStateFlow("")
    private val _isSearchActiveFlow = MutableStateFlow(false)

    val uiState: StateFlow<AlbumsState> = combine(
        _albums,
        _isLoading,
        _errorMessage,
        _searchQueryFlow,
        _isSearchActiveFlow
    ) { raw, loading, error, query, active ->
        val filtered = if (raw != null) {
            if (active && query.isNotEmpty()) {
                raw.filter { it.contains(query, ignoreCase = true) }
            } else {
                raw
            }
        } else {
            emptyList()
        }
        AlbumsState(
            isLoading = loading && raw == null,
            albums = filtered,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AlbumsState(
            isLoading = initialAlbums == null,
            albums = initialAlbums.orEmpty()
        )
    )

    init {
        viewModelScope.launch {
            SettingsStore.lastScanTimeMsFlow.collect {
                loadAlbums()
            }
        }
    }

    fun loadAlbums() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val songs = repository.getAllAudio()
                _albums.value = songs
                    .map(::albumDisplayName)
                    .distinct()
                    .sortedBy { it.lowercase() }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load albums"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQuery = query
        _searchQueryFlow.value = query
    }

    fun onSearchActiveChange(active: Boolean) {
        isSearchActive = active
        _isSearchActiveFlow.value = active
    }

    fun getSongsForAlbum(albumName: String, onResult: (List<com.pxr.cymatic.data.model.AudioFile>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = repository.getAllAudio()
            val filtered = com.pxr.cymatic.ui.screens.library.filterByAlbum(all, albumName)
            withContext(Dispatchers.Main) {
                onResult(filtered)
            }
        }
    }
}
