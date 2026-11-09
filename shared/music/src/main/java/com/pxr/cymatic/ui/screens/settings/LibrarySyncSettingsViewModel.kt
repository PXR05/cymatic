package com.pxr.cymatic.ui.screens.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.sync.AudioStreamClient
import com.pxr.cymatic.sync.CatalogProgress
import com.pxr.cymatic.sync.LibraryCatalogManager
import com.pxr.cymatic.sync.LibrarySyncJobService
import com.pxr.cymatic.sync.LibrarySyncManager
import com.pxr.cymatic.sync.RemoteLibraries
import com.pxr.cymatic.sync.RemoteLibrarySource
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
    private val mutableState = MutableStateFlow(LibrarySyncSettingsState())
    val state = mutableState.asStateFlow()
    private var requestBaseline: SyncProgress? = null
    private var playlistsDirty = false

    init {
        viewModelScope.launch {
            val directory = SettingsStore.getSyncDirectory()
            val source = RemoteLibraries.currentSource()
            val migrationError = runCatching {
                SettingsStore.migrateSyncPlaylistIds(source.id)
                withContext(Dispatchers.IO) {
                    SyncCredentialStore.migrateLegacy(context, source.id)
                    SyncCatalogStore.migrateLegacy(context, source)
                }
            }.exceptionOrNull()
            val catalog = withContext(Dispatchers.IO) { SyncCatalogStore.read(context, source) }
            mutableState.value =
                LibrarySyncSettingsState(
                    loading = false,
                    message = migrationError?.message,
                    url = SettingsStore.getSyncUrl(),
                    username = SettingsStore.getSyncUsername(),
                    hasPassword =
                        withContext(Dispatchers.IO) {
                            SyncCredentialStore.hasPassword(
                                context,
                                source.id
                            )
                        },
                    directory = directory,
                    folderAccessible = hasFolderAccess(directory),
                    network = SyncNetwork.from(SettingsStore.getSyncNetwork()),
                    interval = SettingsStore.getSyncIntervalHours(),
                    layout = SyncLayout.from(SettingsStore.getSyncLayout()),
                    contentMode = if (SettingsStore.getSyncContentConfigured())
                        SyncContentMode.from(SettingsStore.getSyncContentMode()) else SyncContentMode.NONE,
                    musicConfigured = SettingsStore.getSyncContentConfigured(),
                    hasPinnedMusic = !com.pxr.cymatic.sync.OfflineSelectionStore.read(
                        context,
                        source.id
                    ).isEmpty,
                    selectedPlaylists = SettingsStore.getSyncSelectedPlaylists(),
                    catalog = catalog,
                    progress = LibrarySyncManager.progress.value,
                    catalogProgress = LibraryCatalogManager.progress.value,
                )
            launch {
                com.pxr.cymatic.data.media.LibraryChanges.version.collect {
                    val pins = withContext(Dispatchers.IO) {
                        com.pxr.cymatic.sync.OfflineSelectionStore.read(
                            context,
                            RemoteLibraries.currentSource().id
                        )
                    }
                    mutableState.update { it.copy(hasPinnedMusic = !pins.isEmpty) }
                }
            }
            launch {
                SettingsStore.syncNetworkFlow.collect { network ->
                    mutableState.update { it.copy(network = SyncNetwork.from(network)) }
                }
            }
            launch {
                SettingsStore.syncLastTimeFlow.collect { time ->
                    mutableState.update { it.copy(lastTime = time) }
                }
            }
            launch {
                SettingsStore.syncLastResultFlow.collect { result ->
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
                            catalog = if (completed?.source?.id == source.id &&
                                completed.catalog.refreshedAt >= (it.catalog?.refreshedAt ?: 0L)
                            )
                                completed.catalog else it.catalog,
                            message = if (progress is CatalogProgress.Failed) progress.message
                            else if (progress is CatalogProgress.Refreshing) null else it.message,
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
                    val updated = withContext(Dispatchers.IO) {
                        SyncCatalogStore.read(
                            context,
                            RemoteLibraries.currentSource()
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
            SettingsStore.setSyncDirectory(uri.toString())
            mutableState.update {
                it.copy(
                    directory = uri.toString(),
                    folderAccessible = hasFolderAccess(uri.toString()),
                )
            }
        }
    }

    fun saveConnection(url: String, username: String, password: String, onSaved: () -> Unit) {
        if (state.value.running) return
        save {
            val normalized = AudioStreamClient.normalizeBaseUrl(url)
            require(username.isNotBlank()) { "Enter your AudioStream username" }
            val source = RemoteLibrarySource("audiostream", normalized, username.trim())
            val changed = source.id != RemoteLibraries.currentSource().id
            require(password.isNotEmpty() || (state.value.hasPassword && !changed)) {
                "Enter your password for this account"
            }
            if (password.isNotEmpty())
                withContext(Dispatchers.IO) {
                    SyncCredentialStore.savePassword(
                        context,
                        password,
                        source.id
                    )
                }
            SettingsStore.setSyncConnection(normalized, username, changed)
            if (changed) {
                LibrarySyncManager.clearStatus()
                LibraryCatalogManager.clearStatus()
            }
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
                    hasPinnedMusic = !com.pxr.cymatic.sync.OfflineSelectionStore.read(
                        context,
                        source.id
                    ).isEmpty,
                )
            }
            onSaved()
        }
    }

    fun setContentMode(mode: SyncContentMode) = save {
        val network = if (!state.value.musicConfigured && mode != SyncContentMode.NONE)
            SyncNetwork.WIFI else state.value.network
        SettingsStore.setSyncContentMode(mode.value)
        SettingsStore.setSyncNetwork(network.value)
        mutableState.update {
            it.copy(
                contentMode = mode,
                musicConfigured = it.musicConfigured || mode != SyncContentMode.NONE,
                network = network
            )
        }
    }

    fun setPlaylists(selection: Set<String>) {
        if (selection == state.value.selectedPlaylists) return
        playlistsDirty = true
        save(reschedule = false) {
            SettingsStore.setSyncSelectedPlaylists(selection)
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
        SettingsStore.setSyncLayout(layout.value)
        mutableState.update { it.copy(layout = layout) }
    }

    fun setNetwork(network: SyncNetwork) = save {
        require(network == SyncNetwork.NEVER || state.value.nextStep == null) {
            "Finish setup before enabling automatic sync"
        }
        SettingsStore.setSyncNetwork(network.value)
        mutableState.update { it.copy(network = network) }
    }

    fun setInterval(interval: Long) = save {
        SettingsStore.setSyncIntervalHours(interval)
        mutableState.update { it.copy(interval = interval) }
    }

    private fun save(reschedule: Boolean = true, action: suspend () -> Unit) {
        if (!state.value.canEdit) return
        mutableState.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    action()
                    if (reschedule) {
                        playlistsDirty = false
                        LibrarySyncJobService.reschedule(context)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(message = e.message ?: "Could not save. Try again.") }
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
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
        SettingsStore.setSyncDirectory("")
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
        if (state.value.refreshingCatalog) LibraryCatalogManager.cancel() else LibrarySyncManager.cancel()
    }
}
