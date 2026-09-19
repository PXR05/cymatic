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
        PlaybackStore.init(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            LibrarySyncJobService.reschedule(this@CymaticApp)
        }
    }
}
