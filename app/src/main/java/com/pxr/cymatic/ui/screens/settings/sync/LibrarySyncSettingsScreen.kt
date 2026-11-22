package com.pxr.cymatic.ui.screens.settings.sync

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.R
import com.pxr.cymatic.data.model.LibraryFilter
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.sync.RemoteProvider
import com.pxr.cymatic.sync.SyncContentMode
import com.pxr.cymatic.sync.SyncProgress
import com.pxr.cymatic.ui.components.common.ScreenBackHandler
import com.pxr.cymatic.ui.components.pixelIcon
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

@Composable
internal fun LibrarySyncSettingsScreen(
    model: LibrarySyncSettingsViewModel,
    pageRoute: String = Screen.LibrarySyncSettings.route,
    sourceId: String? = null,
    providerArg: String? = null,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsState()
    val nav = LocalNavController.current
    val application = LocalContext.current.applicationContext as Application
    val sourceModel: RemoteSourceViewModel? =
        sourceId?.let { id ->
            viewModel(
                key = "remote_source_$id",
                factory = RemoteSourceViewModel.factory(application, id),
            )
        }
    val sourceState = sourceModel?.state?.collectAsState()?.value

    val error =
        sourceState?.message
            ?: state.message
            ?: (state.progress as? SyncProgress.Failed)?.message
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            it?.let(model::setDirectory)
        }

    fun open(route: String) {
        nav.navigate(route) { launchSingleTop = true }
    }

    fun close() {
        val saving = state.saving || sourceState?.saving == true
        if (
            pageRoute == Screen.SyncAddSourceConnection.route ||
            pageRoute == Screen.SyncSourceConnection.route ||
            pageRoute == Screen.SyncSourcePlaylists.route
        ) {
            if (saving) return
            if (pageRoute == Screen.SyncSourcePlaylists.route) sourceModel?.commitPlaylists()
            else {
                model.connectionPassword = ""
                model.connectionApiKey = ""
                sourceModel?.connectionPassword = ""
                sourceModel?.connectionApiKey = ""
            }
        }
        nav.popBackStack()
    }
    if (pageRoute != Screen.SyncAddSourceConnection.route) {
        ScreenBackHandler(onBack = ::close)
    }
    LaunchedEffect(state.loading, error, pageRoute) {
        if (!state.loading && error != null && error != model.lastPresentedError) {
            model.lastPresentedError = error
            model.errorDetails = error
            if (pageRoute != Screen.SyncError.route) open(Screen.SyncError.route)
        }
        if (error == null && pageRoute != Screen.SyncError.route) {
            model.lastPresentedError = null
        }
    }
    fun chooseFolder() {
        folderPicker.launch(state.directory.takeIf(String::isNotBlank)?.let(Uri::parse))
    }

    fun downloadAll() {
        when (state.nextStep) {
            SyncSetupStep.CONNECTION -> open(Screen.SyncAddSource.route)
            SyncSetupStep.FOLDER -> chooseFolder()
            SyncSetupStep.MUSIC -> open(Screen.AllSongs.createRoute())
            null -> model.startSyncAll()
        }
    }

    fun downloadSource() {
        val current = sourceState ?: return
        val sm = sourceModel ?: return
        val id = sm.sourceId
        when (current.nextStep) {
            SyncSetupStep.CONNECTION -> open(Screen.SyncSourceConnection.createRoute(id))
            SyncSetupStep.FOLDER -> chooseFolder()
            SyncSetupStep.MUSIC ->
                if (current.contentMode == SyncContentMode.NONE) open(Screen.AllSongs.createRoute())
                else open(Screen.SyncSourcePlaylists.createRoute(id))

            null -> sm.startSync()
        }
    }

    when (pageRoute) {
        Screen.LibrarySyncSettings.route -> {
            BaseScreen("Remote libraries", { nav.popBackStack() }, modifier = modifier) {
                SyncSettingsList(
                    buildList {
                        if (state.loading) {
                            add(SyncSetting("Loading…", enabled = false))
                        } else {
                            val count = state.sources.size
                            add(
                                SyncSetting(
                                    title = "Connections",
                                    detail =
                                        when (count) {
                                            0 -> "Add a remote music server"
                                            1 -> "1 library"
                                            else -> "$count libraries"
                                        },
                                    enabled = !state.saving,
                                    icon = pixelIcon(R.drawable.ic_pixel_server),
                                    onClick = { open(Screen.SyncConnections.route) },
                                )
                            )
                            if (state.sources.isNotEmpty()) {
                                add(
                                    SyncSetting(
                                        "Downloads",
                                        state.statusSummary,
                                        icon = pixelIcon(R.drawable.ic_pixel_download),
                                        onClick = { open(Screen.SyncStatus.route) },
                                    )
                                )
                                add(
                                    SyncSetting(
                                        "Advanced",
                                        "Storage, folder layout and scheduling",
                                        state.canEdit,
                                        icon = pixelIcon(R.drawable.ic_pixel_settings),
                                        onClick = { open(Screen.SyncAdvanced.route) },
                                    )
                                )
                            }
                            add(
                                SyncSetting(
                                    "Help",
                                    icon = pixelIcon(R.drawable.ic_pixel_help),
                                    onClick = { open(Screen.SyncHelp.route) },
                                )
                            )
                        }
                    }
                )
            }
        }

        Screen.SyncConnections.route -> {
            SyncSettingsPage("Connections", ::close) {
                SyncSettingsList(
                    buildList {
                        if (state.loading) {
                            add(SyncSetting("Loading…", enabled = false))
                        } else {
                            state.sources.forEach { summary ->
                                add(
                                    SyncSetting(
                                        title = summary.config.displayName,
                                        detail = summary.subtitle,
                                        enabled = !state.saving,
                                        key = summary.config.id,
                                        icon = pixelIcon(R.drawable.ic_pixel_server),
                                        onClick = {
                                            open(Screen.SyncSource.createRoute(summary.config.id))
                                        },
                                    )
                                )
                            }
                            add(
                                SyncSetting(
                                    title = "Add library",
                                    detail =
                                        if (state.sources.isEmpty()) "Connect a remote music server"
                                        else "Connect another remote library",
                                    enabled = state.canEdit,
                                    icon = pixelIcon(R.drawable.ic_pixel_add),
                                    onClick = { open(Screen.SyncAddSource.route) },
                                )
                            )
                        }
                    }
                )
            }
        }

        Screen.SyncAddSource.route -> {
            AddRemoteSourcePage(
                onDismiss = ::close,
                onSelect = { provider ->
                    model.connectionPassword = ""
                    model.connectionApiKey = ""
                    open(Screen.SyncAddSourceConnection.createRoute(provider.value))
                },
            )
        }

        Screen.SyncAddSourceConnection.route -> {
            val provider = providerArg?.let(RemoteProvider::from) ?: RemoteProvider.AUDIOSTREAM
            AddRemoteSourceConnectionPage(
                provider = provider,
                saving = state.saving,
                running = state.running,
                onDismiss = ::close,
                onConnect = { url, username, password, apiKey, displayName ->
                    model.addSource(
                        provider,
                        url,
                        username,
                        password,
                        apiKey,
                        displayName
                    ) { created ->
                        nav.popBackStack(Screen.SyncConnections.route, inclusive = false)
                        open(Screen.SyncSource.createRoute(created.id))
                    }
                },
                password = model.connectionPassword,
                onPasswordChange = { model.connectionPassword = it },
                apiKey = model.connectionApiKey,
                onApiKeyChange = { model.connectionApiKey = it },
            )
        }

        Screen.SyncSource.route -> {
            val current = sourceState
            val sm = sourceModel
            if (current != null && sm != null) {
                val id = sm.sourceId
                RemoteSourcePage(
                    state = current,
                    onDismiss = ::close,
                    onConnection = { open(Screen.SyncSourceConnection.createRoute(id)) },
                    onLibrary = {
                        open(Screen.SyncSourceLibrary.createRoute(id))
                        if (current.catalog == null) sm.refreshLibrary()
                    },
                    onMode = sm::setContentMode,
                    onPlaylists = {
                        open(Screen.SyncSourcePlaylists.createRoute(id))
                        if (current.catalog == null) sm.refreshLibrary()
                    },
                    onDownload = ::downloadSource,
                    onStreamingQuality = sm::setStreamingQuality,
                    onToggleEnabled = { sm.setEnabled(it) },
                    onRemove = { open(Screen.SyncSourceRemove.createRoute(id)) },
                )
            }
        }

        Screen.SyncSourceConnection.route -> {
            val current = sourceState
            val sm = sourceModel
            if (current != null && sm != null) {
                SyncConnectionPage(
                    provider = current.provider,
                    initialUrl = current.url,
                    initialUsername = current.username,
                    initialDisplayName = current.config?.displayName.orEmpty(),
                    hasSavedCredentials = current.hasCredentials,
                    saving = current.saving,
                    running = current.running,
                    connected = current.connected,
                    onDismiss = ::close,
                    onSave = { url, username, password, apiKey, displayName ->
                        sm.saveConnection(url, username, password, apiKey, displayName)
                    },
                    onDisplayNameSave = sm::updateDisplayName,
                    onCheck = sm::refreshLibrary,
                    password = sm.connectionPassword,
                    onPasswordChange = { sm.connectionPassword = it },
                    apiKey = sm.connectionApiKey,
                    onApiKeyChange = { sm.connectionApiKey = it },
                )
            }
        }

        Screen.SyncSourceLibrary.route -> {
            val current = sourceState
            val sm = sourceModel
            if (current != null && sm != null) {
                SyncRemoteLibraryPage(current, ::close, sm::refreshLibrary, sm::cancel)
            }
        }

        Screen.SyncSourceBulkDownloads.route -> {
            val current = sourceState
            val sm = sourceModel
            if (current != null && sm != null) {
                val id = sm.sourceId
                SyncBulkDownloadsPage(
                    state = current,
                    onDismiss = ::close,
                    onMode = sm::setContentMode,
                    onPlaylists = {
                        open(Screen.SyncSourcePlaylists.createRoute(id))
                        if (current.catalog == null) sm.refreshLibrary()
                    },
                    onDownload = ::downloadSource,
                    onSchedule = { open(Screen.SyncSchedule.route) },
                )
            }
        }

        Screen.SyncSourcePlaylists.route -> {
            val current = sourceState
            val sm = sourceModel
            if (current != null && sm != null) {
                SyncPlaylistsPage(current, ::close, sm::setPlaylists, sm::refreshLibrary)
            }
        }

        Screen.SyncSourceRemove.route -> {
            val current = sourceState
            val sm = sourceModel
            if (current != null && sm != null) {
                RemoveRemoteSourcePage(
                    state = current,
                    onDismiss = ::close,
                    onRemove = { deleteDownloads ->
                        sm.remove(deleteDownloads) {
                            nav.popBackStack(Screen.SyncConnections.route, inclusive = false)
                        }
                    },
                )
            }
        }

        Screen.SyncAdvanced.route ->
            SyncAdvancedPage(
                state,
                ::close,
                ::chooseFolder,
                model::useManagedStorage,
                model::setLayout,
                { open(Screen.SyncSchedule.route) },
            )

        Screen.SyncSchedule.route ->
            SyncSchedulePage(state, ::close, ::downloadAll, model::setNetwork, model::setInterval)

        Screen.SyncStatus.route ->
            SyncStatusPage(
                state,
                ::close,
                ::downloadAll,
                model::cancel,
                onOfflineLibrary = {
                    LibraryFilterStore.select(LibraryFilter.OFFLINE)
                    open(Screen.AllSongs.createRoute())
                },
            )

        Screen.SyncHelp.route -> SyncHelpPage(::close)
        Screen.SyncError.route ->
            SyncErrorPage(
                model.errorDetails ?: error ?: "No error details available",
                ::close,
            )
    }
}
