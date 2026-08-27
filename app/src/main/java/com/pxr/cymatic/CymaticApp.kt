package com.pxr.cymatic

import android.app.Application
import com.pxr.cymatic.data.store.LauncherStore
import com.pxr.cymatic.data.store.PlaybackStore
import com.pxr.cymatic.data.store.SettingsStore

class CymaticApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        PlaybackStore.init(this)
        LauncherStore.init(this)
    }
}
