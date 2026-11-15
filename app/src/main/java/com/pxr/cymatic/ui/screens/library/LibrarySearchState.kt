package com.pxr.cymatic.ui.screens.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class LibrarySearchQuery(val query: String = "", val active: Boolean = false) {
    fun <T> filter(items: List<T>, matches: (T, String) -> Boolean): List<T> =
        if (active && query.isNotEmpty()) items.filter { matches(it, query) } else items
}

internal class LibrarySearchState {
    var value by mutableStateOf(LibrarySearchQuery())
        private set

    private val mutableChanges = MutableStateFlow(value)
    val changes = mutableChanges.asStateFlow()

    fun setQuery(query: String) = update(value.copy(query = query))

    fun setActive(active: Boolean) = update(value.copy(active = active))

    private fun update(query: LibrarySearchQuery) {
        value = query
        mutableChanges.value = query
    }
}
