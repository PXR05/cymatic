package com.pxr.cymatic.sync

import java.io.Closeable
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

data class RemoteLibrarySource(val provider: String, val address: String, val account: String) {
    val id: String by lazy {
        val uri = URI(address.trim()).normalize()
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val port = if ((scheme == "https" && uri.port == 443) ||
            (scheme == "http" && uri.port == 80)
        ) -1 else uri.port
        val endpoint = URI(
            scheme, null, uri.host?.lowercase(Locale.ROOT),
            port, uri.path.orEmpty().trimEnd('/'), null, null,
        ).toASCIIString()
        MessageDigest.getInstance("SHA-256")
            .digest("$provider\n$endpoint\n${account.trim()}".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun itemKey(remoteId: String) = RemoteLibraryItemKey(id, remoteId)
}

data class RemoteLibraryItemKey(val sourceId: String, val remoteId: String)

interface RemoteLibraryClient {
    fun getTracks(): List<RemoteTrack>
    fun getPlaylists(): List<RemotePlaylist>
    fun openDownload(trackId: String): RemoteLibraryDownload
    fun openArtwork(trackId: String): RemoteLibraryDownload =
        throw UnsupportedOperationException("Artwork is not supported by this source")

    fun openStream(trackId: String, position: Long, length: Long): RemoteLibraryDownload =
        throw UnsupportedOperationException("Streaming is not supported by this source")

    fun cancel()
}

interface RemoteLibraryDownload : Closeable {
    val responseHeaders: Map<String, List<String>> get() = emptyMap()
    val input: InputStream
    val contentLength: String?
    val contentEncoding: String?
    val contentType: String?
}

interface RemoteCatalogCache {
    fun read(source: RemoteLibrarySource): RemoteCatalog?
    fun write(source: RemoteLibrarySource, catalog: RemoteCatalog)
}

class LibraryCatalogRepository(
    private val cache: RemoteCatalogCache,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun refresh(
        source: RemoteLibrarySource,
        client: RemoteLibraryClient,
        checkCancelled: () -> Unit = {},
    ): RemoteCatalog {
        checkCancelled()
        val tracks = client.getTracks()
        checkCancelled()
        val playlists = catalogPlaylists(tracks, client.getPlaylists())
        checkCancelled()
        val catalog = RemoteCatalog(tracks, playlists, now())
        cache.write(source, catalog)
        return catalog
    }
}
