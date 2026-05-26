package com.pxr.cymatic.ui.screens.library

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.store.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlbumsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AudioRepository.getInstance(application)

    private val _albums = MutableStateFlow<List<String>>(emptyList())

    var searchQuery by mutableStateOf("")
        private set
    var isSearchActive by mutableStateOf(false)
        private set

    private val _searchQueryFlow = MutableStateFlow("")
    private val _isSearchActiveFlow = MutableStateFlow(false)

    val filteredAlbums: StateFlow<List<String>> = combine(
        _albums,
        _searchQueryFlow,
        _isSearchActiveFlow
    ) { albums, query, active ->
        if (active && query.isNotEmpty()) {
            albums.filter { it.contains(query, ignoreCase = true) }
        } else {
            albums
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            SettingsStore.lastScanTimeMsFlow.collect {
                loadAlbums()
            }
        }
    }

    private fun loadAlbums() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = repository.getAllAudio()
            _albums.value = songs
                .map(::albumDisplayName)
                .distinct()
                .sortedBy { it.lowercase() }
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
