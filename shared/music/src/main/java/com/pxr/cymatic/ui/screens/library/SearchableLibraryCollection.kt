package com.pxr.cymatic.ui.screens.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal class SearchableLibraryCollection<T>(
    scope: CoroutineScope,
    cachedItems: List<T>?,
    changes: Flow<Unit>,
    failureMessage: String,
    loadItems: suspend () -> List<T>,
    matches: (T, String) -> Boolean,
) {
    val search = LibrarySearchState()
    private val loader =
        LibraryCollectionLoader(scope, cachedItems, changes, failureMessage, loadItems)

    val state =
        combine(loader.items, loader.isLoading, loader.errorMessage, search.changes) {
                items,
                loading,
                error,
                query ->
                LibraryListState(
                    isLoading = loading && items.isEmpty(),
                    items = query.filter(items, matches),
                    errorMessage = error,
                )
            }
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(5000),
                LibraryListState(isLoading = cachedItems == null, items = cachedItems.orEmpty()),
            )

    fun refresh() = loader.refresh()
}
