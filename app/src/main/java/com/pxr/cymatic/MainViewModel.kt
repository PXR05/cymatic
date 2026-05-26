package com.pxr.cymatic

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.media.loadCachedAudioFiles
import com.pxr.cymatic.data.media.syncAudioFilesToDb
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.store.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _audioFiles = MutableStateFlow<List<AudioFile>>(emptyList())
    val audioFiles: StateFlow<List<AudioFile>> = _audioFiles

    private val repository = AudioRepository.getInstance(application)

    init {
        viewModelScope.launch {
            SettingsStore.lastScanTimeMsFlow.collect { lastScanTimeMs ->
                loadAudioFiles()
            }
        }
    }

    fun performInitialScan() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val start = System.currentTimeMillis()
            val scanDirectories = SettingsStore.getScanDirectories()
            val scanAllMedia = SettingsStore.getScanAllMedia()
            
            // Load cache first so UI responds quickly
            _audioFiles.value = withContext(Dispatchers.IO) { loadCachedAudioFiles(context) }
            
            // Sync in background
            val syncedFiles = withContext(Dispatchers.IO) {
                syncAudioFilesToDb(context, scanDirectories, scanAllMedia)
            }
            _audioFiles.value = syncedFiles
            
            val end = System.currentTimeMillis()
            SettingsStore.setLastScanTimeMs(end)
            SettingsStore.setLastScanCount(syncedFiles.size.toLong())
            SettingsStore.setLastScanDurationMs(end - start)
            Log.d("MainViewModel", "Loaded ${syncedFiles.size} audio files in ${end - start} ms")
            _isReady.value = true
        }
    }

    private fun loadAudioFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _audioFiles.value = repository.getAllAudio()
        }
    }
}
