package com.pxr.cymatic

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.media.syncAudioFilesToDb
import com.pxr.cymatic.data.store.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    fun performInitialScan() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val start = System.currentTimeMillis()
            val scanDirectories = SettingsStore.getScanDirectories()
            val scanAllMedia = SettingsStore.getScanAllMedia()

            val syncedFiles = withContext(Dispatchers.IO) {
                syncAudioFilesToDb(context, scanDirectories, scanAllMedia)
            }
            
            val end = System.currentTimeMillis()
            SettingsStore.setLastScanTimeMs(end)
            SettingsStore.setLastScanCount(syncedFiles.size.toLong())
            SettingsStore.setLastScanDurationMs(end - start)
            Log.d("MainViewModel", "Scanned ${syncedFiles.size} audio files in ${end - start} ms")
            _isReady.value = true
        }
    }
}
