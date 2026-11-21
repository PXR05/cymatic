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
        com.pxr.cymatic.data.store.LibraryFilterStore.initialize()
        PlaybackStore.init(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                com.pxr.cymatic.data.media.AudioRepository.getInstance(this@CymaticApp)
                    .getAllAudio()
                com.pxr.cymatic.data.media.PlaylistRepository.getInstance(this@CymaticApp)
                    .getPlaylists()
            }
            runCatching {
                com.pxr.cymatic.data.licenses.LicenseCatalog.getInstance(this@CymaticApp.assets)
            }
            runCatching {
                val source = com.pxr.cymatic.sync.RemoteLibraries.currentSource()
                com.pxr.cymatic.sync.SyncCredentialStore.hasPassword(this@CymaticApp, source.id)
                com.pxr.cymatic.sync.OfflineSelectionStore.read(this@CymaticApp, source.id)
                com.pxr.cymatic.sync.SyncCatalogStore.read(this@CymaticApp, source)
            }
            runCatching { com.pxr.cymatic.sync.RemoteLibraryRepository.restore(this@CymaticApp) }
                .onFailure {
                    android.util.Log.w(
                        "RemoteLibrary",
                        "Could not restore remote libraries",
                        it,
                    )
                }
            LibrarySyncJobService.reschedule(this@CymaticApp)
        }
    }
}
