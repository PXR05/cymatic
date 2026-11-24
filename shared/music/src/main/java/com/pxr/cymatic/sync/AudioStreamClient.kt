package com.pxr.cymatic.sync

import com.pxr.cymatic.sync.http.HttpTransport
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale

class AudioStreamClient(
    baseUrl: String,
    private val username: String,
    private val password: String,
) : RemoteLibraryClient {
    private val baseUrl = normalizeBaseUrl(baseUrl)
    private var sessionId: String? = null
    private val transport = HttpTransport(logTag = "LibrarySync", providerName = "AudioStream")

    fun authenticate() = ensureAuthenticated()

    override fun cancel() = transport.cancel()

    override fun getTracks(): List<RemoteTrack> {
        ensureAuthenticated()
        val result = mutableListOf<RemoteTrack>()
        var page = 1
        var hasNext: Boolean
        do {
            transport.checkNotCancelled()
            val body = requestJson("audio/?page=$page&limit=100&sortBy=uploadedAt&sortOrder=asc")
            val files = body.getJSONArray("files")
            for (index in 0 until files.length()) {
                val item = files.getJSONObject(index)
                val metadata = item.optJSONObject("metadata")
                val artists = parseArtists(metadata?.optJSONArray("artists"))
                val rawArtist =
                    (metadata?.opt("artist") as? String)?.trim()?.takeIf(String::isNotEmpty)
                val title =
                    (metadata?.opt("title") as? String)?.trim()?.takeIf(String::isNotEmpty)
                        ?: item.getString("filename").substringBeforeLast('.')
                val artist =
                    rawArtist
                        ?: artists.takeIf { it.isNotEmpty() }?.joinToString("; ")
                        ?: "Unknown artist"
                val album =
                    (metadata?.opt("album") as? String)?.trim()?.takeIf(String::isNotEmpty)
                        ?: "Unknown album"
                result +=
                    RemoteTrack(
                        id = item.getString("id"),
                        durationMs = metadata?.optDouble("duration")
                            ?.takeIf { it.isFinite() && it >= 0 }?.let { (it * 1000).toLong() },
                        bitRate = metadata?.optLong("bitrate", -1L)?.takeIf { it > 0L },
                        sampleRate = metadata?.optLong("sampleRate", -1L)?.takeIf { it > 0L },
                        filename = item.getString("filename"),
                        size = item.optLong("size", -1L),
                        updatedAt = item.optString("updatedAt"),
                        title = title,
                        artist = artist,
                        album = album,
                        artists = artists,
                    )
            }
            page++
            hasNext = body.optBoolean("hasNext", false)
        } while (hasNext)
        return result
    }

    override fun getPlaylists(): List<RemotePlaylist> {
        ensureAuthenticated()
        val list = requestJson("playlist/").getJSONArray("playlists")
        val result = mutableListOf<RemotePlaylist>()
        for (index in 0 until list.length()) {
            transport.checkNotCancelled()
            val playlist = list.getJSONObject(index)
            val detail =
                requestJson("playlist/${HttpTransport.encodeQuery(playlist.getString("id"))}")
                    .getJSONObject("playlist")
            val name = detail.getString("name")
            val items = detail.getJSONArray("items")
            val trackIds = mutableListOf<String>()
            for (itemIndex in 0 until items.length()) {
                val item = items.getJSONObject(itemIndex)
                trackIds += item.getJSONObject("audio").getString("id")
            }
            result += RemotePlaylist(detail.getString("id"), name, trackIds)
        }
        return result
    }

    override fun openDownload(trackId: String): RemoteLibraryDownload =
        transport.wrapDownload(openTrack(trackId))

    override fun openArtwork(trackId: String, artworkId: String?): RemoteLibraryDownload {
        val connection =
            authenticatedGet(
                "audio/${HttpTransport.encodeQuery(trackId)}/image",
                binary = true,
            )
        return transport.wrapDownload(connection)
    }

    override fun openStream(
        trackId: String,
        position: Long,
        length: Long,
        quality: StreamQuality,
    ): RemoteLibraryDownload {
        val range = StreamRange.header(position, length)
        val connection =
            authenticatedGet(
                "audio/${HttpTransport.encodeQuery(trackId)}/stream",
                binary = true,
                range = range,
            )
        return transport.wrapRangedStream(connection, position)
    }

    private fun openTrack(trackId: String): HttpURLConnection {
        val connection =
            authenticatedGet(
                "audio/${HttpTransport.encodeQuery(trackId)}/stream",
                binary = true,
            )
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            val status = connection.responseCode
            transport.release(connection)
            throw RemoteRequestException(
                status,
                0,
                "Expected a complete audio response, received HTTP $status",
            )
        }
        return connection
    }

    private fun authenticatedGet(
        path: String,
        binary: Boolean = false,
        range: String? = null,
    ): HttpURLConnection {
        repeat(2) { attempt ->
            ensureAuthenticated()
            transport.checkNotCancelled()
            val url = URI(baseUrl).resolve(path).toASCIIString()
            val connection =
                transport.open(url).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $sessionId")
                    range?.let { setRequestProperty("Range", it) }
                    if (binary) {
                        setRequestProperty("Accept", "application/octet-stream")
                        setRequestProperty("Accept-Encoding", "identity")
                    }
                }
            transport.track(connection)
            try {
                connection.connect()
                if (
                    connection.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED && attempt == 0
                ) {
                    sessionId = null
                    transport.release(connection)
                    return@repeat
                }
                transport.checkResponse(connection)
                return connection
            } catch (error: Throwable) {
                transport.release(connection)
                throw error
            }
        }
        throw IOException("Could not renew the AudioStream session")
    }

    private fun ensureAuthenticated() {
        if (sessionId != null) return
        transport.checkNotCancelled()
        val payload = JSONObject().put("username", username).put("password", password).toString()
        val url = URI(baseUrl).resolve("auth/login").toASCIIString()
        val connection =
            transport.open(url).apply {
                transport.track(this)
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        val response =
            try {
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                transport.checkResponse(connection)
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                transport.release(connection)
            }
        sessionId = JSONObject(response).getString("sessionId")
    }

    private fun requestJson(path: String): JSONObject {
        val connection = authenticatedGet(path)
        val response =
            try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                transport.release(connection)
            }
        return JSONObject(response)
    }

    companion object {
        internal fun parseArtists(array: JSONArray?): List<String> {
            if (array == null) return emptyList()
            val seen = HashSet<String>()
            val result = ArrayList<String>(array.length())
            for (index in 0 until array.length()) {
                val cleaned = (array.opt(index) as? String)?.trim().orEmpty()
                if (cleaned.isEmpty()) continue
                if (seen.add(cleaned.lowercase(Locale.ROOT))) {
                    result.add(cleaned)
                }
            }
            return result
        }

        fun normalizeBaseUrl(value: String): String {
            val uri = URI(value.trim())
            require(uri.scheme == "https" || uri.scheme == "http") {
                "Sync URL must use HTTP or HTTPS"
            }
            require(!uri.host.isNullOrBlank()) { "Sync URL must include a host" }
            return value.trim().trimEnd('/') + "/"
        }
    }
}
