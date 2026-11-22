package com.pxr.cymatic.ui.screens.settings.sync

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.media.LibraryChanges
import com.pxr.cymatic.data.store.LibrarySyncSettings
import com.pxr.cymatic.data.store.RemoteSourceRegistry
import com.pxr.cymatic.sync.CatalogProgress
import com.pxr.cymatic.sync.ConnectionInput
import com.pxr.cymatic.sync.LibraryCatalogManager
import com.pxr.cymatic.sync.LibrarySyncJobService
import com.pxr.cymatic.sync.LibrarySyncManager
import com.pxr.cymatic.sync.OfflineSelectionStore
import com.pxr.cymatic.sync.RemoteSourceConnector
import com.pxr.cymatic.sync.RemoteSourceRemover
import com.pxr.cymatic.sync.SyncCatalogStore
import com.pxr.cymatic.sync.SyncContentMode
import com.pxr.cymatic.sync.SyncCredentialStore
import com.pxr.cymatic.sync.SyncNetwork
import com.pxr.cymatic.sync.SyncProgress
import com.pxr.cymatic.sync.connectionError
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

internal class RemoteSourceViewModel(
    application: Application,
    val sourceId: String,
) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val mutableState = MutableStateFlow(initialState())
    val state = mutableState.asStateFlow()
    private var requestBaseline: SyncProgress? = null
    private var playlistsDirty = false

    internal var connectionPassword by mutableStateOf("")
    internal var connectionApiKey by mutableStateOf("")

    private fun initialState(): RemoteSourceState {
        val config = RemoteSourceRegistry.cached(sourceId) ?: return RemoteSourceState()
        val directory = LibrarySyncSettings.currentSyncDirectory
        return RemoteSourceState(
            loading = false,
            config = config,
            hasCredentials = SyncCredentialStore.cachedHas(sourceId) ?: false,
            catalog = SyncCatalogStore.cached(sourceId),
            hasPinnedMusic = OfflineSelectionStore.cached(sourceId)?.let { !it.isEmpty } ?: false,
            directory = directory,
            folderAccessible = hasFolderAccess(directory),
            network = SyncNetwork.from(LibrarySyncSettings.currentSyncNetwork),
            interval = LibrarySyncSettings.currentSyncIntervalHours,
            progress = LibrarySyncManager.progress.value,
            catalogProgress = LibraryCatalogManager.progress.value,
        )
    }

    init {
        viewModelScope.launch {
            val directory = LibrarySyncSettings.getSyncDirectory()
            val config = RemoteSourceRegistry.get(sourceId, context)
            val hasCreds =
                withContext(Dispatchers.IO) { SyncCredentialStore.has(context, sourceId) }
            val catalog = withContext(Dispatchers.IO) { SyncCatalogStore.read(context, sourceId) }
            val pins = withContext(Dispatchers.IO) { OfflineSelectionStore.read(context, sourceId) }
            mutableState.value =
                RemoteSourceState(
                    loading = false,
                    config = config,
                    hasCredentials = hasCreds,
                    catalog = catalog,
                    hasPinnedMusic = !pins.isEmpty,
                    directory = directory,
                    folderAccessible = hasFolderAccess(directory),
                    network = SyncNetwork.from(LibrarySyncSettings.getSyncNetwork()),
                    interval = LibrarySyncSettings.getSyncIntervalHours(),
                    progress = LibrarySyncManager.progress.value,
                    catalogProgress = LibraryCatalogManager.progress.value,
                )

            launch {
                RemoteSourceRegistry.sourcesFlow.collect { list ->
                    val updated = list.firstOrNull { it.id == sourceId }
                    val creds =
                        withContext(Dispatchers.IO) { SyncCredentialStore.has(context, sourceId) }
                    mutableState.update { it.copy(config = updated, hasCredentials = creds) }
                }
            }
            launch {
                LibraryChanges.version.collect {
                    val pinsNow =
                        withContext(Dispatchers.IO) {
                            OfflineSelectionStore.read(
                                context,
                                sourceId
                            )
                        }
                    val catNow =
                        withContext(Dispatchers.IO) { SyncCatalogStore.read(context, sourceId) }
                    mutableState.update {
                        it.copy(hasPinnedMusic = !pinsNow.isEmpty, catalog = catNow)
                    }
                }
            }
            launch {
                LibrarySyncSettings.syncNetworkFlow.collect { network ->
                    mutableState.update { it.copy(network = SyncNetwork.from(network)) }
                }
            }
            launch {
                LibrarySyncSettings.syncIntervalHoursFlow.collect { interval ->
                    mutableState.update { it.copy(interval = interval) }
                }
            }
            launch {
                LibrarySyncSettings.syncDirectoryFlow.collect { dir ->
                    mutableState.update {
                        it.copy(directory = dir, folderAccessible = hasFolderAccess(dir))
                    }
                }
            }
            launch {
                LibraryCatalogManager.progress.collect { progress ->
                    val updatedCatalog =
                        if (progress is CatalogProgress.Complete) {
                            withContext(Dispatchers.IO) { SyncCatalogStore.read(context, sourceId) }
                        } else null
                    mutableState.update {
                        it.copy(
                            catalogProgress = progress,
                            catalog = updatedCatalog ?: it.catalog,
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
                    val updated =
                        withContext(Dispatchers.IO) { SyncCatalogStore.read(context, sourceId) }
                    mutableState.update { it.copy(catalog = updated) }
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

    fun updateDisplayName(displayName: String) {
        val current = state.value
        val fallback = syncHostLabel(current.url).ifBlank { current.provider.label }
        val resolved = displayName.trim().ifBlank { fallback }
        if (resolved == current.config?.displayName) return
        save(reschedule = false) {
            RemoteSourceRegistry.update(sourceId) { it.copy(displayName = resolved) }
        }
    }

    fun saveConnection(
        url: String,
        username: String,
        password: String,
        apiKey: String = "",
        displayName: String,
        onSaved: () -> Unit = {},
    ) {
        val current = state.value
        if (current.running || current.saving) return
        save(
            onSaved = {
                connectionPassword = ""
                connectionApiKey = ""
                onSaved()
                refreshLibrary()
            }
        ) {
            val provider = current.provider
            val updated =
                try {
                    RemoteSourceConnector(
                        context,
                        ConnectionInput(
                            provider = provider,
                            address = url,
                            username = username,
                            password = password,
                            apiKey = apiKey,
                            displayName = displayName,
                            existingId = sourceId,
                        ),
                    )
                        .connect()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw IllegalArgumentException(connectionError(error, provider))
                }
            val catalog = withContext(Dispatchers.IO) { SyncCatalogStore.read(context, sourceId) }
            val pins = withContext(Dispatchers.IO) { OfflineSelectionStore.read(context, sourceId) }
            mutableState.update {
                it.copy(
                    config = updated,
                    hasCredentials = true,
                    catalog = catalog,
                    hasPinnedMusic = !pins.isEmpty,
                )
            }
        }
    }

    fun setStreamingQuality(transcodeOnMetered: Boolean, bitrateKbps: Int) =
        save(reschedule = false) {
            RemoteSourceRegistry.update(sourceId) {
                it.copy(
                    transcodeOnMetered = transcodeOnMetered,
                    transcodeBitrateKbps = bitrateKbps,
                )
            }
        }

    fun setEnabled(enabled: Boolean) = save {
        if (!enabled) {
            if (LibrarySyncManager.isRunning && LibrarySyncManager.activeSourceId == sourceId) {
                LibrarySyncManager.cancel()
            }
            if (LibraryCatalogManager.isRunning && LibraryCatalogManager.activeSourceId == sourceId) {
                LibraryCatalogManager.cancel()
            }
        }
        RemoteSourceRegistry.update(
            sourceId,
            invalidateClient = true
        ) { it.copy(enabled = enabled) }
        com.pxr.cymatic.data.media.AudioRepository.getInstance(context).invalidate()
        com.pxr.cymatic.data.media.PlaylistRepository.getInstance(context).invalidate()
        LibraryChanges.notifyChanged()
    }

    fun setContentMode(mode: SyncContentMode) =
        save(
            onSaved = {
                if (mode == SyncContentMode.SELECTED_PLAYLISTS && state.value.catalog == null)
                    refreshLibrary()
            }
        ) {
            val currentConfig = state.value.config ?: return@save
            val network =
                if (!currentConfig.contentConfigured && mode != SyncContentMode.NONE) SyncNetwork.WIFI
                else state.value.network
            RemoteSourceRegistry.update(sourceId) {
                it.copy(
                    contentMode = mode.value,
                    contentConfigured = it.contentConfigured || mode != SyncContentMode.NONE,
                )
            }
            LibrarySyncSettings.setSyncNetwork(network.value)
        }

    fun setPlaylists(selection: Set<String>) {
        if (selection == state.value.selectedPlaylists) return
        playlistsDirty = true
        save(reschedule = false) {
            RemoteSourceRegistry.update(sourceId) {
                it.copy(
                    selectedPlaylists = selection,
                    contentConfigured = it.contentConfigured || selection.isNotEmpty(),
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

    fun refreshLibrary() {
        if (!state.value.connected || state.value.running || state.value.saving) return
        mutableState.update { it.copy(message = null) }
        if (!LibraryCatalogManager.refresh(context, sourceId)) {
            mutableState.update { it.copy(message = "A library refresh is already running") }
        }
    }

    fun startSync() {
        if (state.value.nextStep != null || state.value.running || state.value.saving) return
        requestBaseline = LibrarySyncManager.progress.value
        mutableState.update { it.copy(starting = true, message = null) }
        if (!LibrarySyncManager.start(context, sourceId)) {
            requestBaseline = null
            mutableState.update { it.copy(starting = false, message = "A sync is already running") }
        }
    }

    fun remove(deleteDownloads: Boolean, onRemoved: () -> Unit) {
        if (state.value.saving) return
        mutableState.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            var succeeded = false
            try {
                withContext(NonCancellable) {
                    RemoteSourceRemover.remove(context, sourceId, deleteDownloads)
                }
                succeeded = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update {
                    it.copy(message = e.message ?: "Could not remove this library.")
                }
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
            if (succeeded) onRemoved()
        }
    }

    fun cancel() {
        if (state.value.refreshingCatalog) LibraryCatalogManager.cancel()
        else LibrarySyncManager.cancel()
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

    companion object {
        fun factory(application: Application, sourceId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RemoteSourceViewModel(application, sourceId) as T
            }
    }
}
