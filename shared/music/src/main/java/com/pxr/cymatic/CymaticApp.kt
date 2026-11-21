package com.pxr.cymatic

import android.app.Application
import com.pxr.cymatic.data.licenses.LicenseCatalog
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.media.PlaylistRepository
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.data.store.PlaybackStore
import com.pxr.cymatic.data.store.RemoteSourceRegistry
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.sync.LibrarySyncJobService
import com.pxr.cymatic.sync.OfflineSelectionStore
import com.pxr.cymatic.sync.RemoteLibraryRepository
import com.pxr.cymatic.sync.RemoteSourceMigration
import com.pxr.cymatic.sync.SyncCatalogStore
import com.pxr.cymatic.sync.SyncCredentialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

open class CymaticApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        LibraryFilterStore.initialize()
        PlaybackStore.init(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                AudioRepository.getInstance(this@CymaticApp)
                    .getAllAudio()
                PlaylistRepository.getInstance(this@CymaticApp)
                    .getPlaylists()
            }
            runCatching {
                LicenseCatalog.getInstance(this@CymaticApp.assets)
            }
            runCatching {
                RemoteSourceMigration.migrate(this@CymaticApp)
            }.onFailure {
                android.util.Log.w("RemoteLibrary", "Could not migrate remote libraries", it)
            }
            runCatching {
                RemoteSourceRegistry.all(this@CymaticApp).forEach { config ->
                    val source = config.toSource()
                    SyncCredentialStore.has(this@CymaticApp, source.id)
                    OfflineSelectionStore.read(this@CymaticApp, source.id)
                    SyncCatalogStore.read(this@CymaticApp, source)
                }
            }
            runCatching { RemoteLibraryRepository.restore(this@CymaticApp) }
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
