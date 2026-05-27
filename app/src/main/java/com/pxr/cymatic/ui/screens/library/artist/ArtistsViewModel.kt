package com.pxr.cymatic.ui.screens.library.artist

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.screens.library.artistDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ArtistsState(
    val isLoading: Boolean = true,
    val artists: List<String> = emptyList(),
    val errorMessage: String? = null
)

class ArtistsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AudioRepository.getInstance(application)

    private val initialArtists = repository.getCachedAudio()?.let { songs ->
        songs.map(::artistDisplayName).distinct().sortedBy { it.lowercase() }
    }

    private val _artists = MutableStateFlow<List<String>?>(initialArtists)
    private val _isLoading = MutableStateFlow(initialArtists == null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    var searchQuery by mutableStateOf("")
        private set
    var isSearchActive by mutableStateOf(false)
        private set

    private val _searchQueryFlow = MutableStateFlow("")
    private val _isSearchActiveFlow = MutableStateFlow(false)

    val uiState: StateFlow<ArtistsState> = combine(
        _artists,
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
        ArtistsState(
            isLoading = loading && raw == null,
            artists = filtered,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ArtistsState(
            isLoading = initialArtists == null,
            artists = initialArtists.orEmpty()
        )
    )

    init {
        viewModelScope.launch {
            SettingsStore.lastScanTimeMsFlow.collect {
                loadArtists()
            }
        }
    }

    fun loadArtists() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val songs = repository.getAllAudio()
                _artists.value = songs
                    .map(::artistDisplayName)
                    .distinct()
                    .sortedBy { it.lowercase() }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load artists"
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
}
