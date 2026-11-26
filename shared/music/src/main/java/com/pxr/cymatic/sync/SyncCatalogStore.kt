package com.pxr.cymatic.sync

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SyncCatalogStore {
    private const val FILE_NAME = "audiostream-catalog.json"
    internal const val CURRENT_METADATA_VERSION = 4
    private val catalogs = mutableMapOf<String, RemoteCatalog>()
    private val trackIndexes = mutableMapOf<String, Map<String, RemoteTrack>>()

    @Synchronized
    fun cached(source: RemoteLibrarySource): RemoteCatalog? = catalogs[source.id]

    @Synchronized
    fun cached(sourceId: String): RemoteCatalog? = catalogs[sourceId]

    @Synchronized
    internal fun needsMetadataRefresh(context: Context, source: RemoteLibrarySource): Boolean =
        runCatching {
            val root = JSONObject(
                file(context, source.id).openRead().bufferedReader().use { it.readText() }
            )
            needsMetadataRefresh(root, source.provider)
        }.getOrDefault(false)

    internal fun needsMetadataRefresh(root: JSONObject, provider: String): Boolean {
        val requiredVersion =
            if (provider.equals(RemoteProvider.AUDIOSTREAM.value, ignoreCase = true)) {
                CURRENT_METADATA_VERSION
            } else {
                2
            }
        return root.optInt("metadataVersion", 1) < requiredVersion
    }

    fun source(context: Context, sourceId: String): RemoteLibrarySource? =
        RemoteLibraries.cachedSource(sourceId)
            ?: runBlocking { RemoteLibraries.source(sourceId, context) }

    fun track(context: Context, sourceId: String, remoteId: String): RemoteTrack? {
        synchronized(this) { trackIndexes[sourceId] }?.let { return it[remoteId] }
        val catalog = read(context, sourceId) ?: return null
        val index = catalog.tracks.associateBy { it.id }
        synchronized(this) { trackIndexes[sourceId] = index }
        return index[remoteId]
    }

    private fun file(context: Context, sourceId: String): AtomicFile {
        require(sourceId.matches(Regex("[a-f0-9]{64}")))
        val directory = File(context.filesDir, "library-catalogs")
        check(directory.isDirectory || directory.mkdirs()) { "Could not create library catalog folder" }
        return AtomicFile(File(directory, "$sourceId.json"))
    }

    @Synchronized
    fun migrateLegacy(context: Context, source: RemoteLibrarySource) {
        val legacy = File(context.filesDir, FILE_NAME)
        if (!legacy.isFile) return
        val target = file(context, source.id)
        if (!target.baseFile.exists()) {
            val output = target.startWrite()
            try {
                legacy.inputStream().use { it.copyTo(output) }
                target.finishWrite(output)
            } catch (error: Throwable) {
                target.failWrite(output)
                throw error
            }
        }
        check(legacy.delete()) { "Could not migrate the library catalog" }
    }

    fun read(context: Context, source: RemoteLibrarySource): RemoteCatalog? =
        read(context, source.id)

    @Synchronized
    fun read(context: Context, sourceId: String): RemoteCatalog? =
        catalogs[sourceId]
            ?: runCatching {
                val root =
                    JSONObject(
                        file(context, sourceId).openRead().bufferedReader().use { it.readText() }
                    )
                decodeCatalog(root)
            }
                .getOrNull()
                ?.also {
                    catalogs[sourceId] = it
                    trackIndexes[sourceId] = it.tracks.associateBy { track -> track.id }
                }

    internal fun decodeCatalog(root: JSONObject): RemoteCatalog {
        val tracksJson = root.getJSONArray("tracks")
        val tracks = buildList {
            for (index in 0 until tracksJson.length()) {
                val item = tracksJson.getJSONObject(index)
                add(
                    RemoteTrack(
                        id = item.getString("id"),
                        filename = item.getString("filename"),
                        size = item.optLong("size", -1L),
                        updatedAt = item.optString("updatedAt"),
                        title = item.optString("title"),
                        artist = item.optString("artist"),
                        album = item.optString("album"),
                        artists = AudioStreamClient.parseArtists(item.optJSONArray("artists")),
                        trackNumber =
                            item.optInt("trackNumber").takeIf { item.has("trackNumber") },
                        durationMs =
                            item.optLong("durationMs").takeIf { item.has("durationMs") },
                        bitRate = item.optLong("bitRate", -1L).takeIf { it > 0L },
                        sampleRate = item.optLong("sampleRate", -1L).takeIf { it > 0L },
                        artworkId =
                            item.optString("artworkId")
                                .takeIf { item.has("artworkId") && it.isNotBlank() },
                        albumArtist =
                            item.optString("albumArtist")
                                .takeIf { item.has("albumArtist") && it.isNotBlank() },
                        discNumber =
                            item.optInt("discNumber").takeIf { item.has("discNumber") },
                        bitDepth =
                            item.optInt("bitDepth").takeIf { item.has("bitDepth") },
                        channels =
                            item.optInt("channels").takeIf { item.has("channels") },
                        year =
                            item.optInt("year").takeIf { item.has("year") },
                    )
                )
            }
        }
        val playlistsJson = root.getJSONArray("playlists")
        val playlists = buildList {
            for (index in 0 until playlistsJson.length()) {
                val item = playlistsJson.getJSONObject(index)
                val ids = item.getJSONArray("trackIds")
                add(
                    RemotePlaylist(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        trackIds = List(ids.length()) { ids.getString(it) },
                    )
                )
            }
        }
        return RemoteCatalog(tracks, playlists, root.optLong("refreshedAt"))
    }

    internal fun encodeCatalog(
        source: RemoteLibrarySource,
        catalog: RemoteCatalog,
        metadataVersion: Int = CURRENT_METADATA_VERSION,
    ): JSONObject {
        val tracks = JSONArray()
        catalog.tracks.forEach { track ->
            tracks.put(
                JSONObject()
                    .put("id", track.id)
                    .put("filename", track.filename)
                    .put("size", track.size)
                    .put("updatedAt", track.updatedAt)
                    .put("title", track.title)
                    .put("artist", track.artist)
                    .put("album", track.album)
                    .apply {
                        if (track.artists.isNotEmpty()) put(
                            "artists",
                            JSONArray(track.artists)
                        )
                    }
                    .apply { track.trackNumber?.let { put("trackNumber", it) } }
                    .apply { track.durationMs?.let { put("durationMs", it) } }
                    .apply { track.bitRate?.let { put("bitRate", it) } }
                    .apply { track.sampleRate?.let { put("sampleRate", it) } }
                    .apply { track.artworkId?.let { put("artworkId", it) } }
                    .apply { track.albumArtist?.let { put("albumArtist", it) } }
                    .apply { track.discNumber?.let { put("discNumber", it) } }
                    .apply { track.bitDepth?.let { put("bitDepth", it) } }
                    .apply { track.channels?.let { put("channels", it) } }
                    .apply { track.year?.let { put("year", it) } }
            )
        }
        val playlists = JSONArray()
        catalog.playlists.forEach { playlist ->
            playlists.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("trackIds", JSONArray(playlist.trackIds))
            )
        }
        return JSONObject()
            .put("metadataVersion", metadataVersion)
            .put("provider", source.provider)
            .put("address", source.address)
            .put("account", source.account)
            .put("sourceId", source.id)
            .put("refreshedAt", catalog.refreshedAt)
            .put("tracks", tracks)
            .put("playlists", playlists)
    }

    @Synchronized
    fun write(
        context: Context,
        source: RemoteLibrarySource,
        catalog: RemoteCatalog,
        metadataVersion: Int = CURRENT_METADATA_VERSION,
    ) {
        val value = encodeCatalog(source, catalog, metadataVersion).toString()
        val target = file(context, source.id)
        val output = target.startWrite()
        try {
            output.write(value.toByteArray(Charsets.UTF_8))
            target.finishWrite(output)
            catalogs[source.id] = catalog
            trackIndexes[source.id] = catalog.tracks.associateBy { it.id }
        } catch (error: Throwable) {
            target.failWrite(output)
            throw error
        }
    }

    @Synchronized
    fun delete(context: Context, sourceId: String) {
        runCatching { file(context, sourceId).delete() }
        catalogs.remove(sourceId)
        trackIndexes.remove(sourceId)
    }
}
