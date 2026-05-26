package com.pxr.cymatic.ui.screens.library

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.media.Playlist
import com.pxr.cymatic.data.media.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaylistsState(
    val isLoading: Boolean = true,
    val playlists: List<Playlist> = emptyList(),
    val errorMessage: String? = null
)

class PlaylistsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlaylistRepository.getInstance(application)

    private val _playlists = MutableStateFlow<List<Playlist>?>(repository.getCachedPlaylists())
    private val _isLoading = MutableStateFlow(repository.getCachedPlaylists() == null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    var searchQuery by mutableStateOf("")
        private set
    var isSearchActive by mutableStateOf(false)
        private set

    private val _searchQueryFlow = MutableStateFlow("")
    private val _isSearchActiveFlow = MutableStateFlow(false)

    var showCreateDialog by mutableStateOf(false)
    var selectedPlaylist by mutableStateOf<Playlist?>(null)
    var newPlaylistName by mutableStateOf("")

    val uiState: StateFlow<PlaylistsState> = combine(
        _playlists,
        _isLoading,
        _errorMessage,
        _searchQueryFlow,
        _isSearchActiveFlow
    ) { raw, loading, error, query, active ->
        val filtered = if (raw != null) {
            if (active && query.isNotEmpty()) {
                raw.filter { it.name.contains(query, ignoreCase = true) }
            } else {
                raw
            }
        } else {
            emptyList()
        }
        PlaylistsState(
            isLoading = loading && raw == null,
            playlists = filtered,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlaylistsState(
            isLoading = repository.getCachedPlaylists() == null,
            playlists = repository.getCachedPlaylists().orEmpty()
        )
    )

    init {
        loadPlaylists()
    }

    fun loadPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                _playlists.value = repository.getPlaylists()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load playlists"
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

    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.createPlaylist(trimmed)
            loadPlaylists()
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.renamePlaylist(playlistId, trimmed)
            loadPlaylists()
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePlaylist(playlistId)
            loadPlaylists()
        }
    }
}
