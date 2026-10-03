package com.pxr.cymatic

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.media.PlaylistRepository
import com.pxr.cymatic.data.media.syncAudioFilesToDb
import com.pxr.cymatic.data.store.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var initialScanStarted = false

    fun performInitialScan() {
        val context = getApplication<Application>()
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE
        if (
            initialScanStarted ||
                ContextCompat.checkSelfPermission(context, permission) !=
                    PackageManager.PERMISSION_GRANTED
        )
            return
        initialScanStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AudioRepository.getInstance(context).getAllAudio()
                PlaylistRepository.getInstance(context).getPlaylists()
                val start = SystemClock.elapsedRealtime()
                val syncedFiles =
                    syncAudioFilesToDb(
                        context,
                        SettingsStore.getScanDirectories(),
                        SettingsStore.getScanAllMedia(),
                    )
                val duration = SystemClock.elapsedRealtime() - start
                SettingsStore.setLastScanResult(
                    System.currentTimeMillis(),
                    syncedFiles.size.toLong(),
                    duration,
                )
                Log.d("MainViewModel", "Scanned ${syncedFiles.size} audio files in $duration ms")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MainViewModel", "Initial library scan failed", e)
            }
        }
    }
}
