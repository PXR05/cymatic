package com.pxr.cymatic.sync

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

enum class RemoteProvider(
    val value: String,
    val label: String,
    val supportsTranscoding: Boolean,
) {
    AUDIOSTREAM("audiostream", "AudioStream", false),
    SUBSONIC("subsonic", "Subsonic", true),
    JELLYFIN("jellyfin", "Jellyfin", true);

    companion object {
        fun from(value: String): RemoteProvider? =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

data class RemoteLibrarySource(
    val id: String,
    val provider: String,
    val address: String,
    val account: String,
    val displayName: String = "",
    val extras: Map<String, String> = emptyMap(),
) {
    fun itemKey(remoteId: String) = RemoteLibraryItemKey(id, remoteId)

    companion object {
        private val random = SecureRandom()

        fun legacyId(provider: String, address: String, account: String): String {
            val uri = URI(address.trim()).normalize()
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            val port =
                if ((scheme == "https" && uri.port == 443) ||
                    (scheme == "http" && uri.port == 80)
                ) -1 else uri.port
            val endpoint =
                URI(
                    scheme,
                    null,
                    uri.host?.lowercase(Locale.ROOT),
                    port,
                    uri.path.orEmpty().trimEnd('/'),
                    null,
                    null,
                )
                    .toASCIIString()
            return MessageDigest.getInstance("SHA-256")
                .digest("$provider\n$endpoint\n${account.trim()}".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        fun newId(): String {
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

data class RemoteSourceConfig(
    val id: String,
    val provider: String,
    val displayName: String,
    val address: String,
    val account: String,
    val enabled: Boolean = true,
    val folderName: String? = null,
    val contentMode: String = SyncContentMode.NONE.value,
    val contentConfigured: Boolean = false,
    val selectedPlaylists: Set<String> = emptySet(),
    val transcodeOnMetered: Boolean = false,
    val transcodeBitrateKbps: Int = 192,
    val lastSyncTime: Long = 0L,
    val lastSyncResult: String = "Not synced yet",
    val lastError: String? = null,
    val extras: Map<String, String> = emptyMap(),
    val createdAt: Long = 0L,
) {
    fun toSource(): RemoteLibrarySource =
        RemoteLibrarySource(
            id = id,
            provider = provider,
            address = address,
            account = account,
            displayName = displayName,
            extras = extras,
        )
}

data class RemoteCredentials(
    val password: String? = null,
    val apiKey: String? = null,
    val accessToken: String? = null,
    val userId: String? = null,
) {
    val canAuthenticate: Boolean
        get() = !apiKey.isNullOrEmpty() || !password.isNullOrEmpty() || !accessToken.isNullOrEmpty()
}

enum class ClientPurpose {
    CATALOG,
    DOWNLOAD,
    STREAM,
    ARTWORK,
}

sealed interface StreamQuality {
    data object Original : StreamQuality
    data class Mp3(val kbps: Int) : StreamQuality
}

class RemoteRequestException(
    val status: Int,
    val retryAfterMillis: Long,
    message: String,
) : IOException(message)

data class RemoteLibraryItemKey(val sourceId: String, val remoteId: String)

interface RemoteLibraryClient {
    fun getTracks(): List<RemoteTrack>
    fun getPlaylists(): List<RemotePlaylist>
    fun openDownload(trackId: String): RemoteLibraryDownload
    fun openArtwork(trackId: String, artworkId: String? = null): RemoteLibraryDownload =
        throw UnsupportedOperationException("Artwork is not supported by this source")

    fun openStream(
        trackId: String,
        position: Long,
        length: Long,
        quality: StreamQuality = StreamQuality.Original,
    ): RemoteLibraryDownload =
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
