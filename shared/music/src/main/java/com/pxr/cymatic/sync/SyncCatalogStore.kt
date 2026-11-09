package com.pxr.cymatic.sync

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SyncCatalogStore {
    private const val FILE_NAME = "audiostream-catalog.json"
    private val sources = mutableMapOf<String, RemoteLibrarySource>()

    @Synchronized
    internal fun needsMetadataRefresh(context: Context, source: RemoteLibrarySource): Boolean = runCatching {
        val root = JSONObject(file(context, source.id).openRead().bufferedReader().use { it.readText() })
        root.optInt("metadataVersion", 1) < 2
    }.getOrDefault(false)

    @Synchronized
    fun source(context: Context, sourceId: String): RemoteLibrarySource? = sources[sourceId] ?: runCatching {
        val root = JSONObject(file(context, sourceId).openRead().bufferedReader().use { it.readText() })
        RemoteLibrarySource(root.getString("provider"), root.getString("address"), root.getString("account"))
            .takeIf { it.id == sourceId }
    }.getOrNull()?.also { sources[sourceId] = it }

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

    fun read(context: Context, source: RemoteLibrarySource): RemoteCatalog? = read(context, source.id)

    @Synchronized
    internal fun read(context: Context, sourceId: String): RemoteCatalog? = runCatching {
        val root = JSONObject(file(context, sourceId).openRead().bufferedReader().use { it.readText() })
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
                        trackNumber = item.optInt("trackNumber").takeIf { item.has("trackNumber") },
                        durationMs = item.optLong("durationMs").takeIf { item.has("durationMs") },
                        bitRate = item.optLong("bitRate", -1L).takeIf { it > 0L },
                        sampleRate = item.optLong("sampleRate", -1L).takeIf { it > 0L },
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
        RemoteCatalog(tracks, playlists, root.optLong("refreshedAt"))
    }.getOrNull()

    @Synchronized
    fun write(context: Context, source: RemoteLibrarySource, catalog: RemoteCatalog, metadataVersion: Int = 2) {
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
                    .apply { track.trackNumber?.let { put("trackNumber", it) } }
                    .apply { track.durationMs?.let { put("durationMs", it) } }
                    .apply { track.bitRate?.let { put("bitRate", it) } }
                    .apply { track.sampleRate?.let { put("sampleRate", it) } }
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
        val value = JSONObject()
                .put("metadataVersion", metadataVersion)
                .put("provider", source.provider)
                .put("address", source.address)
                .put("account", source.account)
                .put("sourceId", source.id)
                .put("refreshedAt", catalog.refreshedAt)
                .put("tracks", tracks)
                .put("playlists", playlists)
                .toString()
        val target = file(context, source.id)
        val output = target.startWrite()
        try {
            output.write(value.toByteArray(Charsets.UTF_8))
            target.finishWrite(output)
            sources[source.id] = source
        } catch (error: Throwable) {
            target.failWrite(output)
            throw error
        }
    }
}
