package com.pxr.cymatic.ui.screens.settings.sync

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pxr.cymatic.data.model.LibraryFilter
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.sync.SyncContentMode
import com.pxr.cymatic.sync.SyncProgress
import com.pxr.cymatic.ui.components.common.ScreenBackHandler
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

@Composable
internal fun LibrarySyncSettingsScreen(
    model: LibrarySyncSettingsViewModel,
    pageRoute: String = Screen.LibrarySyncSettings.route,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsState()
    val nav = LocalNavController.current
    val error = state.message ?: (state.progress as? SyncProgress.Failed)?.message
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            it?.let(model::setDirectory)
        }
    fun open(route: String) {
        nav.navigate(route) { launchSingleTop = true }
    }
    fun close() {
        if (pageRoute == Screen.SyncConnection.route || pageRoute == Screen.SyncPlaylists.route) {
            if (state.saving) return
            if (pageRoute == Screen.SyncPlaylists.route) model.commitPlaylists()
            else model.connectionPassword = ""
        }
        nav.popBackStack()
    }
    ScreenBackHandler(onBack = ::close)
    LaunchedEffect(state.loading, error) {
        if (!state.loading && error != null && error != model.lastPresentedError) {
            model.lastPresentedError = error
            model.errorDetails = error
            if (pageRoute != Screen.SyncError.route) open(Screen.SyncError.route)
        }
        if (error == null) model.lastPresentedError = null
    }
    fun chooseFolder() {
        folderPicker.launch(state.directory.takeIf(String::isNotBlank)?.let(Uri::parse))
    }
    fun openBulkDownloads() {
        open(Screen.SyncBulkDownloads.route)
        if (state.catalog == null) model.refreshLibrary()
    }
    fun download() {
        when (state.nextStep) {
            SyncSetupStep.CONNECTION -> open(Screen.SyncConnection.route)
            SyncSetupStep.FOLDER -> chooseFolder()
            SyncSetupStep.MUSIC ->
                if (state.contentMode == SyncContentMode.NONE) open(Screen.AllSongs.createRoute())
                else openBulkDownloads()
            null -> model.startSync()
        }
    }
    when (pageRoute) {
        Screen.LibrarySyncSettings.route -> {
            BaseScreen("Remote library", { nav.popBackStack() }, modifier = modifier) {
                SyncSettingsList(
                    buildList {
                        if (state.loading) add(SyncSetting("Loading…", enabled = false))
                        else {
                            add(
                                SyncSetting(
                                    if (state.connected) "Connection" else "Connect to AudioStream",
                                    if (state.connected)
                                        "${state.username} · ${syncHostLabel(state.url)}"
                                    else "Sign in to load your library",
                                    enabled = state.canEdit,
                                    onClick = { open(Screen.SyncConnection.route) },
                                )
                            )
                            if (state.connected) {
                                add(
                                    SyncSetting(
                                        "Library",
                                        if (state.refreshingCatalog) "Refreshing…"
                                        else
                                            state.catalog?.let {
                                                "${it.tracks.size} songs · ${it.playlists.size} playlists"
                                            } ?: "Load songs and playlists",
                                        enabled = !state.saving,
                                        onClick = {
                                            open(Screen.SyncLibrary.route)
                                            if (state.catalog == null) model.refreshLibrary()
                                        },
                                    )
                                )
                                add(
                                    SyncSetting(
                                        "Downloads",
                                        state.statusSummary,
                                        onClick = { open(Screen.SyncStatus.route) },
                                    )
                                )
                                add(
                                    SyncSetting(
                                        "Advanced",
                                        "Storage, bulk downloads and scheduling",
                                        state.canEdit,
                                        onClick = { open(Screen.SyncAdvanced.route) },
                                    )
                                )
                            }
                            add(SyncSetting("Help", onClick = { open(Screen.SyncHelp.route) }))
                        }
                    }
                )
            }
        }
        Screen.SyncLibrary.route ->
            SyncRemoteLibraryPage(state, ::close, model::refreshLibrary, model::cancel)
        Screen.SyncBulkDownloads.route ->
            SyncBulkDownloadsPage(
                state,
                ::close,
                model::setContentMode,
                onPlaylists = {
                    open(Screen.SyncPlaylists.route)
                    if (state.catalog == null) model.refreshLibrary()
                },
                onDownload = ::download,
                onSchedule = { open(Screen.SyncSchedule.route) },
            )
        Screen.SyncAdvanced.route ->
            SyncAdvancedPage(
                state,
                ::close,
                ::chooseFolder,
                model::useManagedStorage,
                model::setLayout,
                ::openBulkDownloads,
                { open(Screen.SyncSchedule.route) },
            )
        Screen.SyncSchedule.route ->
            SyncSchedulePage(state, ::close, ::download, model::setNetwork, model::setInterval)
        Screen.SyncStatus.route ->
            SyncStatusPage(
                state,
                ::close,
                ::download,
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
        Screen.SyncConnection.route ->
            SyncConnectionPage(
                state,
                ::close,
                onSave = { url, username, password ->
                    model.saveConnection(url, username, password) {
                        if (nav.currentDestination?.route == Screen.SyncConnection.route)
                            nav.popBackStack()
                    }
                },
                onCheck = model::refreshLibrary,
                password = model.connectionPassword,
                onPasswordChange = { model.connectionPassword = it },
            )
        Screen.SyncPlaylists.route ->
            SyncPlaylistsPage(state, ::close, model::setPlaylists, model::refreshLibrary)
    }
}
