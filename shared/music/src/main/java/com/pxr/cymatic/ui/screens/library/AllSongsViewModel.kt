package com.pxr.cymatic.ui.screens.library

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.store.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AllSongsState(
    val isLoading: Boolean = true,
    val songs: List<AudioFile> = emptyList(),
    val errorMessage: String? = null
)

class AllSongsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AudioRepository.getInstance(application)

    private val _rawAudioFiles = MutableStateFlow<List<AudioFile>?>(repository.getCachedAudio())
    private val _isLoading = MutableStateFlow(repository.getCachedAudio() == null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    var searchQuery by mutableStateOf("")
        private set
    var isSearchActive by mutableStateOf(false)
        private set

    private val _searchQueryFlow = MutableStateFlow("")
    private val _isSearchActiveFlow = MutableStateFlow(false)

    var contextMenuFile by mutableStateOf<AudioFile?>(null)
    var infoDialogId by mutableStateOf<Long?>(null)

    val uiState: StateFlow<AllSongsState> = combine(
        _rawAudioFiles,
        _isLoading,
        _errorMessage,
        _searchQueryFlow,
        _isSearchActiveFlow
    ) { raw, loading, error, query, active ->
        val filtered = if (raw != null) {
            if (active && query.isNotEmpty()) {
                raw.filter { file ->
                    val title = file.metadata.title.orEmpty()
                    val artist = file.metadata.artist.orEmpty()
                    val album = file.metadata.album.orEmpty()
                    title.contains(query, ignoreCase = true) ||
                            artist.contains(query, ignoreCase = true) ||
                            album.contains(query, ignoreCase = true)
                }
            } else {
                raw
            }
        } else {
            emptyList()
        }
        AllSongsState(
            isLoading = loading && raw == null,
            songs = filtered,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AllSongsState(
            isLoading = repository.getCachedAudio() == null,
            songs = repository.getCachedAudio().orEmpty()
        )
    )

    init {
        viewModelScope.launch {
            SettingsStore.lastScanTimeMsFlow.collect {
                loadAudioFiles()
            }
        }
    }

    fun loadAudioFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                _rawAudioFiles.value = repository.getAllAudio()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load audio files"
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
