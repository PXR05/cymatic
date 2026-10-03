package com.pxr.cymatic.ui.screens.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.sync.AudioStreamClient
import com.pxr.cymatic.sync.LibrarySyncJobService
import com.pxr.cymatic.sync.LibrarySyncManager
import com.pxr.cymatic.sync.SyncCatalogStore
import com.pxr.cymatic.sync.SyncContentMode
import com.pxr.cymatic.sync.SyncCredentialStore
import com.pxr.cymatic.sync.SyncLayout
import com.pxr.cymatic.sync.SyncNetwork
import com.pxr.cymatic.sync.SyncProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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

    init {
        viewModelScope.launch {
            val directory = SettingsStore.getSyncDirectory()
            val catalog = withContext(Dispatchers.IO) { SyncCatalogStore.read(context) }
            mutableState.value =
                LibrarySyncSettingsState(
                    loading = false,
                    url = SettingsStore.getSyncUrl(),
                    username = SettingsStore.getSyncUsername(),
                    hasPassword =
                        withContext(Dispatchers.IO) { SyncCredentialStore.hasPassword(context) },
                    directory = directory,
                    folderAccessible = hasFolderAccess(directory),
                    network = SyncNetwork.from(SettingsStore.getSyncNetwork()),
                    interval = SettingsStore.getSyncIntervalHours(),
                    layout = SyncLayout.from(SettingsStore.getSyncLayout()),
                    contentMode = SyncContentMode.from(SettingsStore.getSyncContentMode()),
                    musicConfigured = SettingsStore.getSyncContentConfigured(),
                    selectedPlaylists = SettingsStore.getSyncSelectedPlaylists(),
                    catalog = catalog,
                    progress = LibrarySyncManager.progress.value,
                )
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
                    val updated = withContext(Dispatchers.IO) { SyncCatalogStore.read(context) }
                    mutableState.update {
                        if (source == (it.url to it.username)) it.copy(catalog = updated) else it
                    }
                }
                if (requestFinished) {
                    requestBaseline = null
                    mutableState.update { it.copy(refreshingCatalog = false, starting = false) }
                }
            }
        }
    }

    private fun hasFolderAccess(directory: String): Boolean =
        directory.isNotBlank() &&
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
            val changed = normalized != state.value.url || username.trim() != state.value.username
            require(password.isNotEmpty() || (state.value.hasPassword && !changed)) {
                "Enter your password for this account"
            }
            if (password.isNotEmpty())
                withContext(Dispatchers.IO) { SyncCredentialStore.savePassword(context, password) }
            SettingsStore.setSyncConnection(normalized, username, changed)
            if (changed) withContext(Dispatchers.IO) { SyncCatalogStore.clear(context) }
            mutableState.update {
                it.copy(
                    url = normalized,
                    username = username.trim(),
                    hasPassword = true,
                    catalog = if (changed) null else it.catalog,
                    selectedPlaylists = if (changed) emptySet() else it.selectedPlaylists,
                    musicConfigured = if (changed) false else it.musicConfigured,
                )
            }
            onSaved()
        }
    }

    fun setContentMode(mode: SyncContentMode) = save {
        SettingsStore.setSyncContentMode(mode.value)
        mutableState.update { it.copy(contentMode = mode, musicConfigured = true) }
    }

    fun setPlaylists(selection: Set<String>) = save {
        SettingsStore.setSyncSelectedPlaylists(selection)
        mutableState.update {
            it.copy(
                selectedPlaylists = selection,
                musicConfigured = it.musicConfigured || selection.isNotEmpty(),
            )
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

    private fun save(action: suspend () -> Unit) {
        if (!state.value.canEdit) return
        mutableState.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            try {
                withContext(NonCancellable) {
                    action()
                    LibrarySyncJobService.reschedule(context)
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

    fun refreshPlaylists() {
        if (!state.value.connected || state.value.running || state.value.saving) return
        requestBaseline = LibrarySyncManager.progress.value
        mutableState.update { it.copy(starting = true, refreshingCatalog = true, message = null) }
        if (!LibrarySyncManager.refreshMetadata(context)) {
            requestBaseline = null
            mutableState.update {
                it.copy(
                    starting = false,
                    refreshingCatalog = false,
                    message = "A sync is already running",
                )
            }
        }
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

    fun cancel() = LibrarySyncManager.cancel()
}
