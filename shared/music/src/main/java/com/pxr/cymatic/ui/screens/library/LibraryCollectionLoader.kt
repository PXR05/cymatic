package com.pxr.cymatic.ui.screens.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class LibraryCollectionLoader<T>(
    private val scope: CoroutineScope,
    cachedItems: List<T>?,
    changes: Flow<Unit>,
    private val failureMessage: String,
    private val loadItems: suspend () -> List<T>,
) {
    private val mutableItems = MutableStateFlow(cachedItems.orEmpty())
    private val mutableLoading = MutableStateFlow(cachedItems == null)
    private val mutableError = MutableStateFlow<String?>(null)
    private var loadingJob: Job? = null

    val items = mutableItems.asStateFlow()
    val isLoading = mutableLoading.asStateFlow()
    val errorMessage = mutableError.asStateFlow()

    init {
        scope.launch { changes.collect { refresh() } }
    }

    fun refresh() {
        loadingJob?.cancel()
        loadingJob = scope.launch {
            mutableLoading.value = true
            mutableError.value = null
            try {
                mutableItems.value = withContext(Dispatchers.IO) { loadItems() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableError.value = e.message ?: failureMessage
            } finally {
                if (currentCoroutineContext().isActive) mutableLoading.value = false
            }
        }
    }
}
