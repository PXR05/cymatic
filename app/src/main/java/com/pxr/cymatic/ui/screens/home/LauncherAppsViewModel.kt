package com.pxr.cymatic.ui.screens.home

import android.app.Application
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

    private var currentDisplayItems: List<PinnedItem> = emptyList()

    val homeApps: StateFlow<HomeAppsState> =
        combine(_allApps, _mostUsed, LauncherStore.pinnedLayoutFlow) { apps, mostUsed, layout ->
            val appsByPackage = apps.associateBy { it.packageName }

            if (layout.isEmpty()) {
                val ranked = mostUsed.mapNotNull { appsByPackage[it] }
                val defaults = buildList {
                    addAll(ranked)
                    apps.forEach { app ->
                        if (none { it.packageName == app.packageName }) {
                            add(app)
                        }
                    }
                }.take(DEFAULT_PIN_COUNT)

                currentDisplayItems = defaults.map { PinnedItem.App(it.packageName) }
                HomeAppsState(
                    entries = defaults.map { PinnedGridEntry.App(it) },
                    isUsingDefaultPins = true
                )
            } else {
                val entries = layout.mapNotNull { item ->
                    when (item) {
                        is PinnedItem.App -> appsByPackage[item.packageName]?.let {
                            PinnedGridEntry.App(it)
                        }

                        is PinnedItem.Folder -> {
                            val resolved = item.packages.mapNotNull { appsByPackage[it] }
                            when {
                                resolved.isEmpty() -> null
                                resolved.size == 1 -> PinnedGridEntry.App(resolved.first())
                                else -> PinnedGridEntry.Folder(item.name, resolved)
                            }
                        }
                    }
                }
                currentDisplayItems = entries.map { it.toPinnedItem() }
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

    fun pin(packageName: String) {
        commitLayout { current ->
            when {
                current.any { it is PinnedItem.App && it.packageName == packageName } -> current
                current.size >= MAX_PIN_COUNT -> current
                else -> current + PinnedItem.App(packageName)
            }
        }
    }

    fun unpin(packageName: String) {
        commitLayout { current ->
            current.mapNotNull { item ->
                when (item) {
                    is PinnedItem.App -> if (item.packageName == packageName) null else item
                    is PinnedItem.Folder -> {
                        val remaining = item.packages - packageName
                        when {
                            remaining == item.packages -> item
                            remaining.size >= 2 -> item.copy(packages = remaining)
                            else -> remaining.firstOrNull()?.let(PinnedItem::App)
                        }
                    }
                }
            }
        }
    }

    fun reorder(fromIndex: Int, toIndex: Int) {
        commitLayout { current ->
            if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) {
                current
            } else {
                current.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
            }
        }
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        commitLayout { current ->
            if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) {
                current
            } else {
                current.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
            }
        }
    }

    fun createFolder(draggedPackageName: String, targetPackageName: String) {
        if (draggedPackageName == targetPackageName) return
        commitLayout { current ->
            val targetIndex = current.indexOfFirst { it is PinnedItem.App && it.packageName == targetPackageName }
            if (targetIndex < 0) return@commitLayout current
            val targetLabel = _allApps.value
                .firstOrNull { it.packageName == targetPackageName }
                ?.label ?: "Folder"
            current.toMutableList().apply {
                set(
                    targetIndex,
                    PinnedItem.Folder(
                        name = targetLabel,
                        packages = listOf(targetPackageName, draggedPackageName)
                    )
                )
                removeAll { it is PinnedItem.App && it.packageName == draggedPackageName }
            }
        }
    }

    fun addToFolder(folderName: String, packageName: String) {
        commitLayout { current ->
            val result = current.map { item ->
                if (item is PinnedItem.Folder && item.name == folderName && packageName !in item.packages) {
                    item.copy(packages = item.packages + packageName)
                } else {
                    item
                }
            }.toMutableList()

            result.removeAll { it is PinnedItem.App && it.packageName == packageName }
            result
        }
    }

    fun removeFromFolder(folderName: String, packageName: String) {
        commitLayout { current ->
            val result = current.map { item ->
                if (item is PinnedItem.Folder && item.name == folderName) {
                    val remaining = item.packages - packageName
                    when {
                        remaining.size >= 2 -> item.copy(packages = remaining)
                        else -> remaining.firstOrNull()?.let(PinnedItem::App)
                    }
                } else {
                    item
                }
            }.filterNotNull()

            if (result.any { it is PinnedItem.App && it.packageName == packageName }) {
                result
            } else {
                result + PinnedItem.App(packageName)
            }
        }
    }

    fun unpinFolder(folderName: String) {
        commitLayout { current ->
            current.filterNot { it is PinnedItem.Folder && it.name == folderName }
        }
    }

    fun renameFolder(oldName: String, newName: String) {
        if (oldName == newName || newName.isBlank()) return
        commitLayout { current ->
            current.map { item ->
                if (item is PinnedItem.Folder && item.name == oldName) {
                    item.copy(name = newName)
                } else {
                    item
                }
            }
        }
    }

    fun setLayout(entries: List<PinnedGridEntry>) {
        viewModelScope.launch {
            LauncherStore.setPinnedLayout(entries.map { it.toPinnedItem() })
        }
    }

    fun commitLayout(transform: (List<PinnedItem>) -> List<PinnedItem>) {
        viewModelScope.launch {
            val base = if (currentDisplayItems.isNotEmpty()) {
                currentDisplayItems
            } else {
                LauncherStore.getPinnedLayout()
            }
            LauncherStore.setPinnedLayout(transform(base).distinct())
        }
    }

    private fun PinnedGridEntry.toPinnedItem(): PinnedItem = when (this) {
        is PinnedGridEntry.App -> PinnedItem.App(app.packageName)
        is PinnedGridEntry.Folder -> PinnedItem.Folder(name, apps.map { it.packageName })
    }

    companion object {
        const val DEFAULT_PIN_COUNT = 8
        const val MAX_PIN_COUNT = 8
    }
}
