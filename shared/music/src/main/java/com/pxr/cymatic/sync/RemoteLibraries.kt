package com.pxr.cymatic.sync

import android.content.Context
import com.pxr.cymatic.data.store.RemoteSourceRegistry
import com.pxr.cymatic.sync.subsonic.SubsonicClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object RemoteLibraries {
    private val cachedClients = ConcurrentHashMap<String, RemoteLibraryClient>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        if (
            purpose == ClientPurpose.ARTWORK ||
            purpose == ClientPurpose.STREAM ||
            purpose == ClientPurpose.RADIO
        ) {
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
        val creds = SyncCredentialStore.get(context, source.id)
        return when (RemoteProvider.from(source.provider)) {
            RemoteProvider.AUDIOSTREAM -> {
                val password =
                    creds?.password
                        ?: throw IllegalStateException("Sign in to $label first")
                AudioStreamClient(source.address, source.account, password)
            }

            RemoteProvider.SUBSONIC -> {
                if (creds == null || (creds.apiKey.isNullOrEmpty() && creds.password.isNullOrEmpty())) {
                    throw IllegalStateException("Sign in to $label first")
                }
                val extensions =
                    source.extras["extensions"].orEmpty()
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                val supportsApiKey =
                    source.extras["openSubsonic"] == "true" &&
                            (extensions.isEmpty() || "apiKeyAuthentication" in extensions)
                val apiKeyRejected = source.extras["apiKeyRejected"] == "true"
                SubsonicClient(
                    baseUrl = source.address,
                    username = source.account,
                    password = creds.password,
                    apiKey = creds.apiKey,
                    supportsApiKey = supportsApiKey,
                    apiKeyRejected = apiKeyRejected,
                    onApiKeyRejected = {
                        scope.launch {
                            RemoteSourceRegistry.update(source.id, invalidateClient = true) { cfg ->
                                cfg.copy(extras = cfg.extras + ("apiKeyRejected" to "true"))
                            }
                        }
                    },
                )
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
