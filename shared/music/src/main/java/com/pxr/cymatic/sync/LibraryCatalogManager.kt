package com.pxr.cymatic.sync

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CatalogProgress {
    data object Idle : CatalogProgress
    data object Refreshing : CatalogProgress
    data class Complete(val source: RemoteLibrarySource, val catalog: RemoteCatalog) : CatalogProgress
    data class Failed(val message: String) : CatalogProgress
    data object Cancelled : CatalogProgress
}
object LibraryCatalogManager {
    private val running = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val client = AtomicReference<RemoteLibraryClient?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableProgress = MutableStateFlow<CatalogProgress>(CatalogProgress.Idle)
    val progress = mutableProgress.asStateFlow()

    fun refresh(context: Context): Boolean {
        if (!running.compareAndSet(false, true)) return false
        cancelled.set(false)
        mutableProgress.value = CatalogProgress.Refreshing
        val application = context.applicationContext
        scope.launch {
            try {
                val source = RemoteLibraries.currentSource()
                SyncCredentialStore.migrateLegacy(application, source.id)
                SyncCatalogStore.migrateLegacy(application, source)
                val connection = RemoteLibraries.client(application, source)
                client.set(connection)
                val catalog = RemoteLibraries.catalogRepository(application).refresh(source, connection) {
                    if (cancelled.get()) throw SyncCancelledException()
                }
                mutableProgress.value = CatalogProgress.Complete(source, catalog)
            } catch (error: Exception) {
                mutableProgress.value = if (cancelled.get()) CatalogProgress.Cancelled
                    else CatalogProgress.Failed(error.message ?: "Could not refresh library")
            } finally {
                client.set(null)
                running.set(false)
            }
        }
        return true
    }

    fun cancel() {
        if (!running.get()) return
        cancelled.set(true)
        client.get()?.cancel()
    }

    fun clearStatus() {
        if (!running.get()) mutableProgress.value = CatalogProgress.Idle
    }
}
