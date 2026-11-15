package com.pxr.cymatic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.pxr.cymatic.ui.components.common.hasStoragePermission

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var initialScanStarted = false

    fun performInitialScan() {
        val context = getApplication<Application>()
        if (initialScanStarted || !hasStoragePermission(context)) return
        initialScanStarted = true
        com.pxr.cymatic.data.media.LocalLibraryScanner.scan(context)
    }
}
