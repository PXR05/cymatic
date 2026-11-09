package com.pxr.cymatic

import android.app.Application
import com.pxr.cymatic.data.store.PlaybackStore
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.sync.LibrarySyncJobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

open class CymaticApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        com.pxr.cymatic.ui.screens.library.LibraryFilterStore.initialize()
        PlaybackStore.init(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { com.pxr.cymatic.sync.RemoteLibraryRepository.restore(this@CymaticApp) }
                .onFailure { android.util.Log.w("RemoteLibrary", "Could not restore remote libraries", it) }
            LibrarySyncJobService.reschedule(this@CymaticApp)
        }
    }
}
