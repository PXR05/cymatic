package com.pxr.cymatic

import com.pxr.cymatic.data.store.LauncherStore

class LauncherApp : CymaticApp() {
    override fun onCreate() {
        super.onCreate()
        LauncherStore.init(this)
    }
}
