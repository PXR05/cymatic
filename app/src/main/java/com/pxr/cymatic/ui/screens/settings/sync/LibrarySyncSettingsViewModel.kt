package com.pxr.cymatic.ui.screens.settings.sync

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.store.LibrarySyncSettings
import com.pxr.cymatic.sync.CatalogProgress
import com.pxr.cymatic.sync.LibraryCatalogManager
import com.pxr.cymatic.sync.LibrarySyncJobService
import com.pxr.cymatic.sync.LibrarySyncManager
import com.pxr.cymatic.sync.RemoteLibraries
import com.pxr.cymatic.sync.SyncCatalogStore
import com.pxr.cymatic.sync.SyncContentMode
import com.pxr.cymatic.sync.SyncCredentialStore
import com.pxr.cymatic.sync.SyncLayout
import com.pxr.cymatic.sync.SyncNetwork
import com.pxr.cymatic.sync.SyncProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class LibrarySyncSettingsViewModel(application: Application) :
    AndroidViewModel(application) {
    private val context = application.applicationContext
    private val mutableState = MutableStateFlow(initialState())
    val state = mutableState.asStateFlow()
    private var requestBaseline: SyncProgress? = null
    private var playlistsDirty = false

    private fun initialState(): LibrarySyncSettingsState {
        if (com.pxr.cymatic.data.store.SettingsStore.currentPreferences == null) {
            return LibrarySyncSettingsState()
        }
        val directory = LibrarySyncSettings.currentSyncDirectory
        val source = RemoteLibraries.cachedCurrentSource()
        val configured = LibrarySyncSettings.currentSyncContentConfigured
        return LibrarySyncSettingsState(
            loading = false,
            url = LibrarySyncSettings.currentSyncUrl,
            username = LibrarySyncSettings.currentSyncUsername,
            hasPassword = SyncCredentialStore.cachedHasPassword(source.id) ?: false,
            directory = directory,
            folderAccessible = hasFolderAccess(directory),
            network = SyncNetwork.from(LibrarySyncSettings.currentSyncNetwork),
            interval = LibrarySyncSettings.currentSyncIntervalHours,
            layout = SyncLayout.from(LibrarySyncSettings.currentSyncLayout),
            contentMode =
                if (configured) SyncContentMode.from(LibrarySyncSettings.currentSyncContentMode)
                else SyncContentMode.NONE,
            musicConfigured = configured,
            hasPinnedMusic =
                com.pxr.cymatic.sync.OfflineSelectionStore.cached(source.id)?.let { !it.isEmpty }
                    ?: false,
            selectedPlaylists = LibrarySyncSettings.currentSyncSelectedPlaylists,
            catalog = SyncCatalogStore.cached(source),
            progress = LibrarySyncManager.progress.value,
            catalogProgress = LibraryCatalogManager.progress.value,
            lastTime = LibrarySyncSettings.currentSyncLastTime,
            lastResult = LibrarySyncSettings.currentSyncLastResult,
        )
    }

    init {
        viewModelScope.launch {
            val directory = LibrarySyncSettings.getSyncDirectory()
            val source = RemoteLibraries.currentSource()
            val migrationError = runCatching {
                LibrarySyncSettings.migrateSyncPlaylistIds(source.id)
                withContext(Dispatchers.IO) {
                    SyncCredentialStore.migrateLegacy(context, source.id)
                    SyncCatalogStore.migrateLegacy(context, source)
                }
            }
                .exceptionOrNull()
            val catalog = withContext(Dispatchers.IO) { SyncCatalogStore.read(context, source) }
            mutableState.value =
                LibrarySyncSettingsState(
                    loading = false,
                    message = migrationError?.message,
                    url = LibrarySyncSettings.getSyncUrl(),
                    username = LibrarySyncSettings.getSyncUsername(),
                    hasPassword =
                        withContext(Dispatchers.IO) {
                            SyncCredentialStore.hasPassword(
                                context,
                                source.id,
                            )
                        },
                    directory = directory,
                    folderAccessible = hasFolderAccess(directory),
                    network = SyncNetwork.from(LibrarySyncSettings.getSyncNetwork()),
                    interval = LibrarySyncSettings.getSyncIntervalHours(),
                    layout = SyncLayout.from(LibrarySyncSettings.getSyncLayout()),
                    contentMode =
                        if (LibrarySyncSettings.getSyncContentConfigured())
                            SyncContentMode.from(LibrarySyncSettings.getSyncContentMode())
                        else SyncContentMode.NONE,
                    musicConfigured = LibrarySyncSettings.getSyncContentConfigured(),
                    hasPinnedMusic =
                        !com.pxr.cymatic.sync.OfflineSelectionStore.read(
                                context,
                                source.id,
                            )
                            .isEmpty,
                    selectedPlaylists = LibrarySyncSettings.getSyncSelectedPlaylists(),
                    catalog = catalog,
                    progress = LibrarySyncManager.progress.value,
                    catalogProgress = LibraryCatalogManager.progress.value,
                    lastTime = LibrarySyncSettings.currentSyncLastTime,
                    lastResult = LibrarySyncSettings.currentSyncLastResult,
                )
            launch {
                com.pxr.cymatic.data.media.LibraryChanges.version.collect {
                    val pins =
                        withContext(Dispatchers.IO) {
                            com.pxr.cymatic.sync.OfflineSelectionStore.read(
                                context,
                                RemoteLibraries.currentSource().id,
                            )
                        }
                    mutableState.update { it.copy(hasPinnedMusic = !pins.isEmpty) }
                }
            }
            launch {
                LibrarySyncSettings.syncNetworkFlow.collect { network ->
                    mutableState.update { it.copy(network = SyncNetwork.from(network)) }
                }
            }
            launch {
                LibrarySyncSettings.syncLastTimeFlow.collect { time ->
                    mutableState.update { it.copy(lastTime = time) }
                }
            }
            launch {
                LibrarySyncSettings.syncLastResultFlow.collect { result ->
                    mutableState.update { it.copy(lastResult = result) }
                }
            }
            launch {
                LibraryCatalogManager.progress.collect { progress ->
                    val source = RemoteLibraries.currentSource()
                    mutableState.update {
                        val completed = progress as? CatalogProgress.Complete
                        it.copy(
                            catalogProgress = progress,
                            catalog =
                                if (
                                    completed?.source?.id == source.id &&
                                        completed.catalog.refreshedAt >=
                                            (it.catalog?.refreshedAt ?: 0L)
                                )
                                    completed.catalog
                                else it.catalog,
                            message =
                                if (progress is CatalogProgress.Failed) progress.message
                                else if (progress is CatalogProgress.Refreshing) null
                                else it.message,
                        )
                    }
                }
            }
            LibrarySyncManager.progress.collect { progress ->
                val active =
                    progress is SyncProgress.Preparing ||
                        progress is SyncProgress.Downloading ||
                        progress is SyncProgress.Cancelling
                val requestFinished =
                    requestBaseline != null && progress !== requestBaseline && !active
                mutableState.update {
                    it.copy(
                        progress = progress,
                        starting = if (active || requestFinished) false else it.starting,
                    )
                }
                if (progress is SyncProgress.Complete) {
                    val source = state.value.url to state.value.username
                    val updated =
                        withContext(Dispatchers.IO) {
                            SyncCatalogStore.read(
                                context,
                                RemoteLibraries.currentSource(),
                            )
                        }
                    mutableState.update {
                        if (source == (it.url to it.username)) it.copy(catalog = updated) else it
                    }
                }
                if (requestFinished) {
                    requestBaseline = null
                    mutableState.update { it.copy(starting = false) }
                }
            }
        }
    }

    private fun hasFolderAccess(directory: String): Boolean =
        directory.isBlank() ||
            context.contentResolver.persistedUriPermissions.any {
                it.uri.toString() == directory && it.isReadPermission && it.isWritePermission
            }

    fun setDirectory(uri: Uri) {
        save {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            LibrarySyncSettings.setSyncDirectory(uri.toString())
            mutableState.update {
                it.copy(
                    directory = uri.toString(),
                    folderAccessible = hasFolderAccess(uri.toString()),
                )
            }
        }
    }

    internal var connectionPassword by mutableStateOf("")
    internal var lastPresentedError: String? = null
    internal var errorDetails by mutableStateOf<String?>(null)

    fun saveConnection(url: String, username: String, password: String, onSaved: () -> Unit) {
        if (state.value.running) return
        save(
            onSaved = {
                connectionPassword = ""
                onSaved()
                refreshLibrary()
            }
        ) {
            val previous = RemoteLibraries.currentSource()
            val source =
                try {
                    com.pxr.cymatic.sync
                        .AudioStreamConnection(context, url, username, password)
                        .save()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw IllegalArgumentException(com.pxr.cymatic.sync.connectionError(error))
                }
            val normalized = source.address
            val changed = source.id != previous.id
            val catalog = withContext(Dispatchers.IO) { SyncCatalogStore.read(context, source) }
            mutableState.update {
                it.copy(
                    url = normalized,
                    username = username.trim(),
                    hasPassword = true,
                    catalog = catalog,
                    directory = if (changed) "" else it.directory,
                    folderAccessible = if (changed) true else it.folderAccessible,
                    network = if (changed) SyncNetwork.NEVER else it.network,
                    contentMode = if (changed) SyncContentMode.NONE else it.contentMode,
                    catalogProgress = CatalogProgress.Idle,
                    progress = if (changed) SyncProgress.Idle else it.progress,
                    selectedPlaylists = if (changed) emptySet() else it.selectedPlaylists,
                    musicConfigured = if (changed) false else it.musicConfigured,
                    hasPinnedMusic =
                        !com.pxr.cymatic.sync.OfflineSelectionStore.read(
                                context,
                                source.id,
                            )
                            .isEmpty,
                )
            }
        }
    }

    fun setContentMode(mode: SyncContentMode) =
        save(
            onSaved = {
                if (mode == SyncContentMode.SELECTED_PLAYLISTS && state.value.catalog == null)
                    refreshLibrary()
            }
        ) {
            val network =
                if (!state.value.musicConfigured && mode != SyncContentMode.NONE) SyncNetwork.WIFI
                else state.value.network
            LibrarySyncSettings.setSyncContentMode(mode.value)
            LibrarySyncSettings.setSyncNetwork(network.value)
            mutableState.update {
                it.copy(
                    contentMode = mode,
                    musicConfigured = it.musicConfigured || mode != SyncContentMode.NONE,
                    network = network,
                )
            }
        }

    fun setPlaylists(selection: Set<String>) {
        if (selection == state.value.selectedPlaylists) return
        playlistsDirty = true
        save(reschedule = false) {
            LibrarySyncSettings.setSyncSelectedPlaylists(selection)
            mutableState.update {
                it.copy(
                    selectedPlaylists = selection,
                    musicConfigured = it.musicConfigured || selection.isNotEmpty(),
                )
            }
        }
    }

    fun commitPlaylists() {
        if (!playlistsDirty) return
        playlistsDirty = false
        viewModelScope.launch {
            withContext(NonCancellable) { LibrarySyncJobService.reschedule(context) }
        }
    }

    fun setLayout(layout: SyncLayout) = save {
        LibrarySyncSettings.setSyncLayout(layout.value)
        mutableState.update { it.copy(layout = layout) }
    }

    fun setNetwork(network: SyncNetwork) = save {
        require(network == SyncNetwork.NEVER || state.value.nextStep == null) {
            "Finish setup before enabling automatic sync"
        }
        LibrarySyncSettings.setSyncNetwork(network.value)
        mutableState.update { it.copy(network = network) }
    }

    fun setInterval(interval: Long) = save {
        LibrarySyncSettings.setSyncIntervalHours(interval)
        mutableState.update { it.copy(interval = interval) }
    }

    private fun save(
        reschedule: Boolean = true,
        onSaved: () -> Unit = {},
        action: suspend () -> Unit,
    ) {
        if (!state.value.canEdit) return
        mutableState.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            var succeeded = false
            try {
                withContext(NonCancellable) {
                    action()
                    if (reschedule) {
                        playlistsDirty = false
                        LibrarySyncJobService.reschedule(context)
                    }
                }
                succeeded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(message = e.message ?: "Could not save. Try again.") }
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
            if (succeeded) onSaved()
        }
    }

    override fun onCleared() {
        if (playlistsDirty) {
            playlistsDirty = false
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                LibrarySyncJobService.reschedule(context)
            }
        }
        super.onCleared()
    }

    fun refreshLibrary() {
        if (!state.value.connected || state.value.running || state.value.saving) return
        mutableState.update { it.copy(message = null) }
        if (!LibraryCatalogManager.refresh(context)) {
            mutableState.update { it.copy(message = "A library refresh is already running") }
        }
    }

    fun useManagedStorage() = save {
        LibrarySyncSettings.setSyncDirectory("")
        mutableState.update { it.copy(directory = "", folderAccessible = true) }
    }

    fun startSync() {
        if (state.value.nextStep != null || state.value.running || state.value.saving) return
        requestBaseline = LibrarySyncManager.progress.value
        mutableState.update { it.copy(starting = true, message = null) }
        if (!LibrarySyncManager.start(context)) {
            requestBaseline = null
            mutableState.update { it.copy(starting = false, message = "A sync is already running") }
        }
    }

    fun cancel() {
        if (state.value.refreshingCatalog) LibraryCatalogManager.cancel()
        else LibrarySyncManager.cancel()
    }
}
