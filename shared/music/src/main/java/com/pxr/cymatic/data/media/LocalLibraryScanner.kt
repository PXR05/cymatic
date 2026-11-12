package com.pxr.cymatic.data.media

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.pxr.cymatic.data.store.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

sealed interface LocalScanProgress {
    data object Idle : LocalScanProgress
    data object Scanning : LocalScanProgress
    data class Complete(val count: Int) : LocalScanProgress
    data object Failed : LocalScanProgress
}

object LocalLibraryScanner {
    private val running = AtomicBoolean()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableProgress = MutableStateFlow<LocalScanProgress>(LocalScanProgress.Idle)
    val progress = mutableProgress.asStateFlow()

    fun scan(context: Context): Boolean {
        if (!running.compareAndSet(false, true)) return false
        val app = context.applicationContext
        mutableProgress.value = LocalScanProgress.Scanning
        scope.launch {
            try {
                val start = SystemClock.elapsedRealtime()
                val songs = syncAudioFilesToDb(
                    app,
                    SettingsStore.getScanDirectories(),
                    SettingsStore.getScanAllMedia()
                )
                val count = songs.count { it.sourceId == null }
                SettingsStore.setLastScanResult(
                    System.currentTimeMillis(),
                    count.toLong(),
                    SystemClock.elapsedRealtime() - start
                )
                LibraryChanges.notifyChanged()
                mutableProgress.value = LocalScanProgress.Complete(count)
            } catch (error: CancellationException) {
                mutableProgress.value = LocalScanProgress.Idle
                throw error
            } catch (error: Exception) {
                Log.e("LocalLibraryScanner", "Could not scan device music", error)
                mutableProgress.value = LocalScanProgress.Failed
            } finally {
                running.set(false)
            }
        }
        return true
    }
}
