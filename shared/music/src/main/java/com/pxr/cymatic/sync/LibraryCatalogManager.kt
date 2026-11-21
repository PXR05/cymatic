package com.pxr.cymatic.sync

import android.content.Context
import com.pxr.cymatic.data.store.RemoteSourceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

sealed interface CatalogProgress {
    data object Idle : CatalogProgress
    data class Refreshing(
        val sourceName: String? = null,
        val index: Int = 0,
        val total: Int = 0,
    ) : CatalogProgress

    data class Complete(
        val refreshed: List<RemoteLibrarySource>,
        val failures: Map<String, String> = emptyMap(),
    ) : CatalogProgress

    data class Failed(val message: String) : CatalogProgress
    data object Cancelled : CatalogProgress
}

object LibraryCatalogManager {
    private val running = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val client = AtomicReference<RemoteLibraryClient?>(null)
    private val currentSourceId = AtomicReference<String?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableProgress = MutableStateFlow<CatalogProgress>(CatalogProgress.Idle)
    val progress = mutableProgress.asStateFlow()

    val isRunning: Boolean
        get() = running.get()

    val activeSourceId: String?
        get() = currentSourceId.get()

    fun refresh(context: Context, sourceId: String? = null): Boolean {
        if (!running.compareAndSet(false, true)) return false
        cancelled.set(false)
        mutableProgress.value = CatalogProgress.Refreshing()
        val application = context.applicationContext
        scope.launch {
            try {
                val targets =
                    if (sourceId != null) {
                        listOfNotNull(RemoteSourceRegistry.get(sourceId, application))
                    } else {
                        RemoteSourceRegistry.all(application)
                            .filter { it.enabled && SyncCredentialStore.has(application, it.id) }
                    }
                if (targets.isEmpty()) {
                    mutableProgress.value = CatalogProgress.Failed("No connected remote libraries")
                    return@launch
                }
                val refreshed = mutableListOf<RemoteLibrarySource>()
                val failures = linkedMapOf<String, String>()
                targets.forEachIndexed { index, config ->
                    if (cancelled.get()) throw SyncCancelledException()
                    val source = config.toSource()
                    currentSourceId.set(source.id)
                    mutableProgress.value =
                        CatalogProgress.Refreshing(
                            sourceName = config.displayName,
                            index = index + 1,
                            total = targets.size,
                        )
                    try {
                        val connection =
                            RemoteLibraries.client(application, source, ClientPurpose.CATALOG)
                        client.set(connection)
                        val catalog =
                            RemoteLibraries.catalogRepository(application)
                                .refresh(source, connection) {
                                    if (cancelled.get()) throw SyncCancelledException()
                                }
                        RemoteLibraryRepository.publish(application, source, catalog)
                        RemoteSourceRegistry.update(source.id) { it.copy(lastError = null) }
                        refreshed += source
                    } catch (error: SyncCancelledException) {
                        throw error
                    } catch (error: Exception) {
                        if (cancelled.get()) throw SyncCancelledException()
                        val errorCode = classifyError(error)
                        val message = connectionError(error, RemoteProvider.from(source.provider))
                        failures[source.id] = message
                        RemoteSourceRegistry.setResult(
                            source.id,
                            System.currentTimeMillis(),
                            "Refresh failed: $message",
                            lastError = errorCode,
                        )
                    } finally {
                        client.set(null)
                    }
                }
                mutableProgress.value =
                    if (refreshed.isEmpty()) {
                        CatalogProgress.Failed(
                            failures.values.firstOrNull() ?: "Could not refresh library"
                        )
                    } else {
                        CatalogProgress.Complete(refreshed, failures)
                    }
            } catch (_: SyncCancelledException) {
                mutableProgress.value = CatalogProgress.Cancelled
            } catch (error: Exception) {
                mutableProgress.value =
                    if (cancelled.get()) CatalogProgress.Cancelled
                    else CatalogProgress.Failed(error.message ?: "Could not refresh library")
            } finally {
                client.set(null)
                currentSourceId.set(null)
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

    internal fun classifyError(error: Throwable): String =
        when (error) {
            is RemoteRequestException ->
                if (error.status == 401 || error.status == 403) "auth" else "unreachable"

            is IllegalStateException -> "auth"
            else -> "unreachable"
        }
}
