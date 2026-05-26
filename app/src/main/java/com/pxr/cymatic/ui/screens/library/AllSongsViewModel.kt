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

class AllSongsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AudioRepository.getInstance(application)

    private val _rawAudioFiles = MutableStateFlow<List<AudioFile>>(emptyList())

    var searchQuery by mutableStateOf("")
        private set
    var isSearchActive by mutableStateOf(false)
        private set

    private val _searchQueryFlow = MutableStateFlow("")
    private val _isSearchActiveFlow = MutableStateFlow(false)

    var contextMenuFile by mutableStateOf<AudioFile?>(null)
    var infoDialogId by mutableStateOf<Long?>(null)

    val filteredAudioFiles: StateFlow<List<AudioFile>> = combine(
        _rawAudioFiles,
        _searchQueryFlow,
        _isSearchActiveFlow
    ) { files, query, active ->
        if (active && query.isNotEmpty()) {
            files.filter { file ->
                val title = file.metadata.title.orEmpty()
                val artist = file.metadata.artist.orEmpty()
                val album = file.metadata.album.orEmpty()
                title.contains(query, ignoreCase = true) ||
                        artist.contains(query, ignoreCase = true) ||
                        album.contains(query, ignoreCase = true)
            }
        } else {
            files
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            SettingsStore.lastScanTimeMsFlow.collect {
                loadAudioFiles()
            }
        }
    }

    private fun loadAudioFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _rawAudioFiles.value = repository.getAllAudio()
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
