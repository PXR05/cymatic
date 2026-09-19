package com.pxr.cymatic.sync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SyncCatalogStore {
    private const val FILE_NAME = "audiostream-catalog.json"

    fun read(context: Context): RemoteCatalog? = runCatching {
        val root = JSONObject(File(context.filesDir, FILE_NAME).readText())
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

    fun write(context: Context, catalog: RemoteCatalog) {
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
        File(context.filesDir, FILE_NAME).writeText(
            JSONObject()
                .put("refreshedAt", catalog.refreshedAt)
                .put("tracks", tracks)
                .put("playlists", playlists)
                .toString()
        )
    }
}
