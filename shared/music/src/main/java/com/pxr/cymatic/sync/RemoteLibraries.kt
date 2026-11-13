package com.pxr.cymatic.sync

import android.content.Context
import com.pxr.cymatic.data.store.LibrarySyncSettings

internal object RemoteLibraries {
    suspend fun currentSource() =
        RemoteLibrarySource(
            LibrarySyncSettings.getSyncAdapter(),
            LibrarySyncSettings.getSyncUrl(),
            LibrarySyncSettings.getSyncUsername(),
        )

    fun client(context: Context, source: RemoteLibrarySource): RemoteLibraryClient {
        SyncCredentialStore.migrateLegacy(context, source.id)
        return when (source.provider) {
            "audiostream" -> {
                val password =
                    SyncCredentialStore.getPassword(context, source.id)
                        ?: throw IllegalStateException("Enter your library password first")
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
