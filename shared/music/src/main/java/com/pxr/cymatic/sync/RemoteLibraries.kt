package com.pxr.cymatic.sync

import android.content.Context
import com.pxr.cymatic.data.store.RemoteSourceRegistry
import java.util.concurrent.ConcurrentHashMap

object RemoteLibraries {
    private val cachedClients = ConcurrentHashMap<String, RemoteLibraryClient>()

    fun cachedSource(sourceId: String): RemoteLibrarySource? =
        RemoteSourceRegistry.cached(sourceId)?.toSource()

    suspend fun source(sourceId: String, context: Context? = null): RemoteLibrarySource? =
        RemoteSourceRegistry.get(sourceId, context)?.toSource()

    fun invalidate(sourceId: String) {
        cachedClients.remove(sourceId)
    }

    fun client(
        context: Context,
        source: RemoteLibrarySource,
        purpose: ClientPurpose = ClientPurpose.CATALOG,
    ): RemoteLibraryClient {
        if (purpose == ClientPurpose.ARTWORK || purpose == ClientPurpose.STREAM) {
            return cachedClients.computeIfAbsent(source.id) {
                createClient(context.applicationContext, source)
            }
        }
        return createClient(context.applicationContext, source)
    }

    private fun createClient(context: Context, source: RemoteLibrarySource): RemoteLibraryClient {
        val label = source.displayName.ifBlank {
            RemoteProvider.from(source.provider)?.label ?: source.provider
        }
        return when (RemoteProvider.from(source.provider)) {
            RemoteProvider.AUDIOSTREAM -> {
                val password =
                    SyncCredentialStore.get(context, source.id)?.password
                        ?: throw IllegalStateException("Sign in to $label first")
                AudioStreamClient(source.address, source.account, password)
            }

            else -> error("This library provider is not supported yet")
        }
    }

    fun catalogRepository(context: Context) =
        LibraryCatalogRepository(
            object : RemoteCatalogCache {
                override fun read(source: RemoteLibrarySource) =
                    SyncCatalogStore.read(context, source)

                override fun write(source: RemoteLibrarySource, catalog: RemoteCatalog) =
                    SyncCatalogStore.write(context, source, catalog)
            }
        )
}
