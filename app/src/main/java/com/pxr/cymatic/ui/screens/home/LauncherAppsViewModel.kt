package com.pxr.cymatic.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.launcher.LauncherAppsLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherAppsViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface PinnedGridEntry {
        data class App(val app: LauncherAppsLoader.LauncherApp) : PinnedGridEntry
        data class Folder(val name: String, val apps: List<LauncherAppsLoader.LauncherApp>) : PinnedGridEntry
    }

    data class HomeAppsState(
        val entries: List<PinnedGridEntry> = emptyList(),
        val isUsingDefaultPins: Boolean = true
    )

    private val _allApps = MutableStateFlow<List<LauncherAppsLoader.LauncherApp>>(emptyList())
    val allApps: StateFlow<List<LauncherAppsLoader.LauncherApp>> = _allApps

    private val _mostUsed = MutableStateFlow<List<String>>(emptyList())

    val mostUsedApps: StateFlow<List<LauncherAppsLoader.LauncherApp>> =
        combine(_allApps, _mostUsed) { apps, mostUsed ->
            val byPkg = apps.associateBy { it.packageName }
            val ranked = mostUsed.mapNotNull { byPkg[it] }
            (ranked + apps.filter { app -> ranked.none { it.packageName == app.packageName } }).take(8)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // Pinned apps are now strictly most-used and not user-modifiable.
    val homeApps: StateFlow<HomeAppsState> =
        combine(_allApps, _mostUsed) { apps, mostUsed ->
            val appsByPackage = apps.associateBy { it.packageName }
            val ranked = mostUsed.mapNotNull { appsByPackage[it] }
            val defaults = buildList {
                addAll(ranked)
                apps.forEach { app ->
                    if (none { it.packageName == app.packageName }) {
                        add(app)
                    }
                }
            }.take(DEFAULT_PIN_COUNT)
            HomeAppsState(
                entries = defaults.map { PinnedGridEntry.App(it) },
                isUsingDefaultPins = true
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeAppsState()
        )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val apps = LauncherAppsLoader.loadLaunchableApps(getApplication())
                val mostUsed =
                    LauncherAppsLoader.loadMostUsedPackageNames(getApplication(), DEFAULT_PIN_COUNT)
                apps to mostUsed
            }
            _allApps.value = result.first
            _mostUsed.value = result.second
        }
    }

    // Legacy pin/folder APIs are now no-ops – pinned apps are auto-managed via most-used.
    fun pin(packageName: String) {}
    fun unpin(packageName: String) {}
    fun reorder(fromIndex: Int, toIndex: Int) {}
    fun moveItem(fromIndex: Int, toIndex: Int) {}
    fun createFolder(draggedPackageName: String, targetPackageName: String) {}
    fun addToFolder(folderName: String, packageName: String) {}
    fun removeFromFolder(folderName: String, packageName: String) {}
    fun unpinFolder(folderName: String) {}
    fun renameFolder(oldName: String, newName: String) {}
    fun setLayout(entries: List<PinnedGridEntry>) {}
    fun commitLayout(transform: (List<com.pxr.cymatic.data.launcher.PinnedItem>) -> List<com.pxr.cymatic.data.launcher.PinnedItem>) {}

    companion object {
        const val DEFAULT_PIN_COUNT = 8
        const val MAX_PIN_COUNT = 8
    }
}
