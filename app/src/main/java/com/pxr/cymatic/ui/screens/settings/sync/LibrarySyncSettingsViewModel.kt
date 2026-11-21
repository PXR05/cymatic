package com.pxr.cymatic.ui.screens.settings.sync

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
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
import com.pxr.cymatic.sync.RemoteProvider
import com.pxr.cymatic.sync.RemoteSourceConfig
import com.pxr.cymatic.sync.RemoteSourceConnector
import com.pxr.cymatic.sync.RemoteSourceMigration
import com.pxr.cymatic.sync.SyncCatalogStore
import com.pxr.cymatic.sync.SyncCredentialStore
import com.pxr.cymatic.sync.SyncLayout
import com.pxr.cymatic.sync.SyncNetwork
import com.pxr.cymatic.sync.SyncProgress
import com.pxr.cymatic.sync.connectionError
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
    private val mutableState = MutableStateFlow(initialState())
    val state = mutableState.asStateFlow()
    private var requestBaseline: SyncProgress? = null

    internal var connectionPassword by mutableStateOf("")
    internal var lastPresentedError: String? = null
    internal var errorDetails by mutableStateOf<String?>(null)

    private fun initialState(): LibrarySyncSettingsState {
        if (com.pxr.cymatic.data.store.SettingsStore.currentPreferences == null) {
            return LibrarySyncSettingsState()
        }
        val directory = LibrarySyncSettings.currentSyncDirectory
        val configs = RemoteSourceRegistry.currentSources
        return LibrarySyncSettingsState(
            loading = false,
            sources = configs.map { cachedSummary(it) },
            directory = directory,
            folderAccessible = hasFolderAccess(directory),
            network = SyncNetwork.from(LibrarySyncSettings.currentSyncNetwork),
            interval = LibrarySyncSettings.currentSyncIntervalHours,
            layout = SyncLayout.from(LibrarySyncSettings.currentSyncLayout),
            progress = LibrarySyncManager.progress.value,
            catalogProgress = LibraryCatalogManager.progress.value,
            lastTime = LibrarySyncSettings.currentSyncLastTime,
            lastResult = LibrarySyncSettings.currentSyncLastResult,
        )
    }

    private fun cachedSummary(config: RemoteSourceConfig) =
        RemoteSourceSummary(
            config = config,
            hasCredentials = SyncCredentialStore.cachedHas(config.id) ?: false,
            catalog = SyncCatalogStore.cached(config.id),
            hasPinnedMusic = OfflineSelectionStore.cached(config.id)?.let { !it.isEmpty } ?: false,
        )

    private suspend fun loadSummaries(configs: List<RemoteSourceConfig>): List<RemoteSourceSummary> =
        withContext(Dispatchers.IO) {
            configs.map { config ->
                RemoteSourceSummary(
                    config = config,
                    hasCredentials = SyncCredentialStore.has(context, config.id),
                    catalog = SyncCatalogStore.read(context, config.id),
                    hasPinnedMusic = !OfflineSelectionStore.read(context, config.id).isEmpty,
                )
            }
        }

    init {
        viewModelScope.launch {
            runCatching { RemoteSourceMigration.migrate(context) }
            val directory = LibrarySyncSettings.getSyncDirectory()
            val configs = RemoteSourceRegistry.all(context)
            mutableState.value =
                LibrarySyncSettingsState(
                    loading = false,
                    sources = loadSummaries(configs),
                    directory = directory,
                    folderAccessible = hasFolderAccess(directory),
                    network = SyncNetwork.from(LibrarySyncSettings.getSyncNetwork()),
                    interval = LibrarySyncSettings.getSyncIntervalHours(),
                    layout = SyncLayout.from(LibrarySyncSettings.getSyncLayout()),
                    progress = LibrarySyncManager.progress.value,
                    catalogProgress = LibraryCatalogManager.progress.value,
                    lastTime = LibrarySyncSettings.currentSyncLastTime,
                    lastResult = LibrarySyncSettings.currentSyncLastResult,
                )

            launch {
                RemoteSourceRegistry.sourcesFlow.collect { updatedConfigs ->
                    val summaries = loadSummaries(updatedConfigs)
                    mutableState.update { it.copy(sources = summaries) }
                }
            }
            launch {
                LibraryChanges.version.collect {
                    val configsNow = RemoteSourceRegistry.all(context)
                    val summaries = loadSummaries(configsNow)
                    mutableState.update { it.copy(sources = summaries) }
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
                LibrarySyncSettings.syncLayoutFlow.collect { layout ->
                    mutableState.update { it.copy(layout = SyncLayout.from(layout)) }
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
                    val summaries =
                        if (progress is CatalogProgress.Complete) {
                            loadSummaries(RemoteSourceRegistry.all(context))
                        } else null
                    mutableState.update {
                        it.copy(
                            catalogProgress = progress,
                            sources = summaries ?: it.sources,
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
                    val summaries = loadSummaries(RemoteSourceRegistry.all(context))
                    mutableState.update { it.copy(sources = summaries) }
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

    fun addSource(
        provider: RemoteProvider,
        url: String,
        username: String,
        password: String,
        displayName: String,
        onAdded: (RemoteSourceConfig) -> Unit,
    ) {
        if (state.value.running || state.value.saving) return
        var created: RemoteSourceConfig? = null
        save(
            onSaved = {
                connectionPassword = ""
                created?.let { config ->
                    onAdded(config)
                    LibraryCatalogManager.refresh(context, config.id)
                }
            }
        ) {
            created =
                try {
                    RemoteSourceConnector(
                        context,
                        ConnectionInput(
                            provider = provider,
                            address = url,
                            username = username,
                            password = password,
                            displayName = displayName,
                        ),
                    )
                        .connect()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw IllegalArgumentException(connectionError(error, provider))
                }
        }
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

    fun useManagedStorage() = save {
        LibrarySyncSettings.setSyncDirectory("")
        mutableState.update { it.copy(directory = "", folderAccessible = true) }
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

    fun startSyncAll() {
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
}
