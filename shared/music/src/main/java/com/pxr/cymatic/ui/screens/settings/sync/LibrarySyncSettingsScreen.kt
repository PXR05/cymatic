package com.pxr.cymatic.ui.screens.settings.sync

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.data.model.LibraryFilter
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.sync.SyncContentMode
import com.pxr.cymatic.sync.SyncProgress
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

private enum class SyncPage {
    LIBRARY,
    BULK_DOWNLOADS,
    ADVANCED,
    SCHEDULE,
    STATUS,
    HELP,
}

@Composable
fun LibrarySyncSettingsScreen(modifier: Modifier = Modifier) {
    val model: LibrarySyncSettingsViewModel = viewModel()
    val state by model.state.collectAsState()
    val nav = LocalNavController.current
    var page by rememberSaveable { mutableStateOf<SyncPage?>(null) }
    var bulkReturnPage by rememberSaveable { mutableStateOf(SyncPage.ADVANCED) }
    var scheduleReturnPage by rememberSaveable { mutableStateOf(SyncPage.ADVANCED) }
    var showConnection by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }
    var refreshAfterSave by remember { mutableStateOf(false) }
    var shownError by remember { mutableStateOf<String?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }
    val error = state.message ?: (state.progress as? SyncProgress.Failed)?.message
    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            it?.let(model::setDirectory)
        }
    LaunchedEffect(refreshAfterSave, state.saving) {
        if (refreshAfterSave && !state.saving) {
            refreshAfterSave = false
            model.refreshLibrary()
        }
    }
    LaunchedEffect(state.loading, error) {
        if (!state.loading) {
            if (error != null && error != lastError) shownError = error
            lastError = error
        }
    }
    fun chooseFolder() {
        folderPicker.launch(state.directory.takeIf { it.isNotBlank() }?.let(Uri::parse))
    }

    fun openBulkDownloads() {
        if (page != SyncPage.BULK_DOWNLOADS) bulkReturnPage = page ?: SyncPage.ADVANCED
        page = SyncPage.BULK_DOWNLOADS
        if (state.catalog == null) model.refreshLibrary()
    }

    fun download() {
        when (state.nextStep) {
            SyncSetupStep.CONNECTION -> showConnection = true
            SyncSetupStep.FOLDER -> chooseFolder()
            SyncSetupStep.MUSIC ->
                if (state.contentMode == SyncContentMode.NONE) {
                    nav.navigate(Screen.AllSongs.createRoute())
                } else openBulkDownloads()

            null -> model.startSync()
        }
    }

    BaseScreen("Remote library", { nav.popBackStack() }, modifier = modifier) {
        SyncSettingsList(
            buildList {
                if (state.loading) add(SyncSetting("Loading…", enabled = false))
                else {
                    add(
                        SyncSetting(
                            if (state.connected) "Connection" else "Connect to AudioStream",
                            if (state.connected) "${state.username} · ${syncHostLabel(state.url)}"
                            else "Sign in to load your library",
                            enabled = state.canEdit,
                            onClick = { showConnection = true },
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
                                    page = SyncPage.LIBRARY
                                    if (state.catalog == null) model.refreshLibrary()
                                },
                            )
                        )
                        add(
                            SyncSetting(
                                "Downloads",
                                state.statusSummary,
                                onClick = { page = SyncPage.STATUS },
                            )
                        )
                        add(
                            SyncSetting(
                                "Advanced",
                                "Storage, bulk downloads and scheduling",
                                state.canEdit,
                                onClick = { page = SyncPage.ADVANCED },
                            )
                        )
                    }
                    add(SyncSetting("Help", onClick = { page = SyncPage.HELP }))
                }
            }
        )
    }
    when (page) {
        SyncPage.LIBRARY ->
            SyncRemoteLibraryPage(
                state,
                { page = null },
                model::refreshLibrary,
                model::cancel,
            )

        SyncPage.BULK_DOWNLOADS ->
            SyncBulkDownloadsPage(
                state,
                { page = bulkReturnPage },
                onMode = { mode ->
                    model.setContentMode(mode)
                    if (mode == SyncContentMode.SELECTED_PLAYLISTS && state.catalog == null)
                        refreshAfterSave = true
                },
                onPlaylists = {
                    showPlaylists = true
                    if (state.catalog == null) model.refreshLibrary()
                },
                onDownload = ::download,
                onSchedule = {
                    scheduleReturnPage = SyncPage.BULK_DOWNLOADS
                    page = SyncPage.SCHEDULE
                },
            )

        SyncPage.ADVANCED ->
            SyncAdvancedPage(
                state,
                { page = null },
                ::chooseFolder,
                model::useManagedStorage,
                model::setLayout,
                ::openBulkDownloads,
                {
                    scheduleReturnPage = SyncPage.ADVANCED
                    page = SyncPage.SCHEDULE
                },
            )

        SyncPage.SCHEDULE ->
            SyncSchedulePage(
                state,
                { page = scheduleReturnPage },
                ::download,
                model::setNetwork,
                model::setInterval,
            )

        SyncPage.STATUS ->
            SyncStatusPage(
                state,
                { page = null },
                ::download,
                model::cancel,
                onOfflineLibrary = {
                    LibraryFilterStore.select(LibraryFilter.OFFLINE)
                    nav.navigate(Screen.AllSongs.createRoute())
                },
            )

        SyncPage.HELP -> SyncHelpPage { page = null }
        null -> Unit
    }
    if (showConnection)
        SyncConnectionPage(
            state,
            onDismiss = { if (!state.saving) showConnection = false },
            onSave = { url, username, password ->
                model.saveConnection(url, username, password) {
                    showConnection = false
                    refreshAfterSave = true
                }
            },
            onCheck = model::refreshLibrary,
        )
    if (showPlaylists)
        SyncPlaylistsPage(
            state,
            onDismiss = {
                if (!state.saving) {
                    showPlaylists = false
                    model.commitPlaylists()
                }
            },
            onChange = model::setPlaylists,
            onRefresh = model::refreshLibrary,
        )
    shownError?.let { SyncErrorPage(it) { shownError = null } }
}
