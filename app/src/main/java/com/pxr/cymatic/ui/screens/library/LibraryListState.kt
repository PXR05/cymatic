package com.pxr.cymatic.ui.screens.library

data class LibraryListState<T>(
    val isLoading: Boolean = true,
    val items: List<T> = emptyList(),
    val errorMessage: String? = null,
)
