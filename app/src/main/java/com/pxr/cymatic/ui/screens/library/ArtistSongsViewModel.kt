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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ArtistSongsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AudioRepository.getInstance(application)

    private val _songs = MutableStateFlow<List<AudioFile>>(emptyList())
    val songs: StateFlow<List<AudioFile>> = _songs

    var contextMenuFile by mutableStateOf<AudioFile?>(null)
    var infoDialogId by mutableStateOf<Long?>(null)

    private var currentArtistName: String? = null

    init {
        viewModelScope.launch {
            SettingsStore.lastScanTimeMsFlow.collect {
                currentArtistName?.let { loadArtistSongs(it) }
            }
        }
    }

    fun loadArtistSongs(artistName: String) {
        currentArtistName = artistName
        viewModelScope.launch(Dispatchers.IO) {
            val allSongs = repository.getAllAudio()
            _songs.value = filterByArtist(allSongs, artistName)
        }
    }
}
