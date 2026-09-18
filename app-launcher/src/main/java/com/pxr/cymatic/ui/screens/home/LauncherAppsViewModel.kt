package com.pxr.cymatic.ui.screens.home

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.launcher.LauncherAppsLoader
import com.pxr.cymatic.data.launcher.PinnedItem
import com.pxr.cymatic.data.store.LauncherStore
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
        val key: String

        data class App(val app: LauncherAppsLoader.LauncherApp) : PinnedGridEntry {
            override val key: String get() = "app:${app.packageName}"
        }

        data class Folder(
            val id: String,
            val name: String,
            val apps: List<LauncherAppsLoader.LauncherApp>
        ) : PinnedGridEntry {
            override val key: String get() = "folder:$id"
        }
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

    val homeApps: StateFlow<HomeAppsState> =
        combine(_allApps, _mostUsed, LauncherStore.pinnedLayoutFlow, LauncherStore.hasStoredPinnedLayoutFlow) { apps, mostUsed, customPins, hasStored ->
            val byPkg = apps.associateBy { it.packageName }
            if (!hasStored || customPins.isEmpty()) {
                val ranked = mostUsed.mapNotNull { byPkg[it] }
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
            } else {
                val entries = customPins.mapNotNull { item ->
                    when (item) {
                        is PinnedItem.App -> byPkg[item.packageName]?.let { PinnedGridEntry.App(it) }
                        is PinnedItem.Folder -> {
                            val folderApps = item.packages.mapNotNull { byPkg[it] }
                            if (folderApps.isNotEmpty()) {
                                PinnedGridEntry.Folder(
                                    id = item.id,
                                    name = item.name,
                                    apps = folderApps
                                )
                            } else null
                        }
                    }
                }
                HomeAppsState(
                    entries = entries,
                    isUsingDefaultPins = false
                )
            }
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

    private suspend fun getCurrentLayout(): MutableList<PinnedItem> {
        val stored = LauncherStore.getPinnedLayout()
        if (stored.isNotEmpty()) return stored.toMutableList()
        return homeApps.value.entries.mapNotNull { entry ->
            when (entry) {
                is PinnedGridEntry.App -> PinnedItem.App(entry.app.packageName)
                is PinnedGridEntry.Folder -> PinnedItem.Folder(entry.name, entry.apps.map { it.packageName }, entry.id)
            }
        }.toMutableList()
    }

    fun reorderPinned(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val list = getCurrentLayout()
            if (fromIndex in list.indices && toIndex in list.indices && fromIndex != toIndex) {
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
                LauncherStore.setPinnedLayout(list)
            }
        }
    }

    fun mergeIntoFolder(sourceIndex: Int, targetIndex: Int, defaultFolderName: String = "Folder") {
        viewModelScope.launch {
            val list = getCurrentLayout()
            if (sourceIndex !in list.indices || targetIndex !in list.indices || sourceIndex == targetIndex) return@launch
            val sourceItem = list[sourceIndex]
            val targetItem = list[targetIndex]

            val sourcePackages = when (sourceItem) {
                is PinnedItem.App -> listOf(sourceItem.packageName)
                is PinnedItem.Folder -> sourceItem.packages
            }

            val newFolder = when (targetItem) {
                is PinnedItem.App -> {
                    val combined = (listOf(targetItem.packageName) + sourcePackages).distinct()
                    PinnedItem.Folder(name = defaultFolderName, packages = combined)
                }
                is PinnedItem.Folder -> {
                    val combined = (targetItem.packages + sourcePackages).distinct()
                    targetItem.copy(packages = combined)
                }
            }

            list[targetIndex] = newFolder
            list.removeAt(sourceIndex)
            LauncherStore.setPinnedLayout(list)
        }
    }

    fun addAppToFolder(folderId: String, packageName: String) {
        viewModelScope.launch {
            val list = getCurrentLayout()
            val folderIndex = list.indexOfFirst { it is PinnedItem.Folder && it.id == folderId }
            if (folderIndex >= 0) {
                val folder = list[folderIndex] as PinnedItem.Folder
                if (packageName !in folder.packages) {
                    list[folderIndex] = folder.copy(packages = folder.packages + packageName)
                    LauncherStore.setPinnedLayout(list)
                }
            }
        }
    }

    fun reorderInFolder(folderId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val list = getCurrentLayout()
            val folderIndex = list.indexOfFirst { it is PinnedItem.Folder && it.id == folderId }
            if (folderIndex >= 0) {
                val folder = list[folderIndex] as PinnedItem.Folder
                val packages = folder.packages.toMutableList()
                if (fromIndex in packages.indices && toIndex in packages.indices && fromIndex != toIndex) {
                    val pkg = packages.removeAt(fromIndex)
                    packages.add(toIndex, pkg)
                    list[folderIndex] = folder.copy(packages = packages)
                    LauncherStore.setPinnedLayout(list)
                }
            }
        }
    }

    fun removeAppFromFolder(folderId: String, packageName: String) {
        viewModelScope.launch {
            val list = getCurrentLayout()
            val folderIndex = list.indexOfFirst { it is PinnedItem.Folder && it.id == folderId }
            if (folderIndex >= 0) {
                val folder = list[folderIndex] as PinnedItem.Folder
                val remaining = folder.packages.filter { it != packageName }
                if (remaining.isEmpty()) {
                    list.removeAt(folderIndex)
                } else if (remaining.size == 1) {
                    list[folderIndex] = PinnedItem.App(remaining.first())
                } else {
                    list[folderIndex] = folder.copy(packages = remaining)
                }
                LauncherStore.setPinnedLayout(list)
            }
        }
    }

    fun renameFolder(folderId: String, newName: String) {
        viewModelScope.launch {
            val list = getCurrentLayout()
            val folderIndex = list.indexOfFirst { it is PinnedItem.Folder && it.id == folderId }
            if (folderIndex >= 0) {
                val folder = list[folderIndex] as PinnedItem.Folder
                list[folderIndex] = folder.copy(name = newName.ifBlank { "Folder" })
                LauncherStore.setPinnedLayout(list)
            }
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            val list = getCurrentLayout()
            list.removeAll { it is PinnedItem.Folder && it.id == folderId }
            LauncherStore.setPinnedLayout(list)
        }
    }

    fun unpinItem(index: Int) {
        viewModelScope.launch {
            val list = getCurrentLayout()
            if (index in list.indices) {
                list.removeAt(index)
                LauncherStore.setPinnedLayout(list)
            }
        }
    }

    fun unpinApp(packageName: String) {
        viewModelScope.launch {
            val list = getCurrentLayout()
            list.removeAll { it is PinnedItem.App && it.packageName == packageName }
            LauncherStore.setPinnedLayout(list)
        }
    }

    fun pinApp(packageName: String, context: Context? = null) {
        viewModelScope.launch {
            val list = getCurrentLayout()
            if (list.none { it is PinnedItem.App && it.packageName == packageName }) {
                if (list.size >= MAX_PIN_COUNT) {
                    context?.let { ctx ->
                        withContext(Dispatchers.Main) {
                            Toast.makeText(ctx, "Maximum $MAX_PIN_COUNT pinned items reached", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@launch
                }
                list.add(PinnedItem.App(packageName))
                LauncherStore.setPinnedLayout(list)
            }
        }
    }

    fun resetToMostUsed() {
        LauncherStore.clearPinnedLayout()
    }

    fun isPinned(packageName: String): Boolean {
        return homeApps.value.entries.any { entry ->
            when (entry) {
                is PinnedGridEntry.App -> entry.app.packageName == packageName
                is PinnedGridEntry.Folder -> entry.apps.any { it.packageName == packageName }
            }
        }
    }

    companion object {
        const val DEFAULT_PIN_COUNT = 8
        const val MAX_PIN_COUNT = 8
    }
}
