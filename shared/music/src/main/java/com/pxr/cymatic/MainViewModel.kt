package com.pxr.cymatic

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel

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
        com.pxr.cymatic.data.media.LocalLibraryScanner.scan(context)
    }
}
