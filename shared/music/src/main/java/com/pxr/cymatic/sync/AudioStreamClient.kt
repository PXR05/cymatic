package com.pxr.cymatic.sync

import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class AudioStreamRequestException(
    val status: Int,
    val retryAfterMillis: Long,
    message: String,
) : IOException(message)

class AudioStreamClient(
    baseUrl: String,
    private val username: String,
    private val password: String,
) : RemoteLibraryClient {
    private val baseUrl = normalizeBaseUrl(baseUrl)
    private var sessionId: String? = null
    private val cancelled = AtomicBoolean(false)
    private val activeConnection = AtomicReference<HttpURLConnection?>(null)

    fun authenticate() = ensureAuthenticated()

    override fun cancel() {
        cancelled.set(true)
        activeConnection.getAndSet(null)?.disconnect()
    }

    override fun getTracks(): List<RemoteTrack> {
        ensureAuthenticated()
        val result = mutableListOf<RemoteTrack>()
        var page = 1
        var hasNext: Boolean
        do {
            checkNotCancelled()
            val body = requestJson("audio/?page=$page&limit=100&sortBy=uploadedAt&sortOrder=asc")
            val files = body.getJSONArray("files")
            for (index in 0 until files.length()) {
                val item = files.getJSONObject(index)
                val metadata = item.optJSONObject("metadata")
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
                        title =
                            metadata?.optString("title")?.takeIf(String::isNotBlank)
                                ?: item.getString("filename").substringBeforeLast('.'),
                        artist =
                            metadata?.optString("artist")?.takeIf(String::isNotBlank)
                                ?: "Unknown artist",
                        album =
                            metadata?.optString("album")?.takeIf(String::isNotBlank)
                                ?: "Unknown album",
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
            checkNotCancelled()
            val playlist = list.getJSONObject(index)
            val detail =
                requestJson("playlist/${encodePath(playlist.getString("id"))}")
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

    override fun openDownload(trackId: String): RemoteLibraryDownload {
        val connection = openTrack(trackId)
        return object : RemoteLibraryDownload {
            override val input get() = connection.inputStream
            override val contentLength get() = connection.getHeaderField("Content-Length")
            override val contentEncoding get() = connection.getHeaderField("Content-Encoding")
            override val contentType get() = connection.getHeaderField("Content-Type")
            override fun close() = release(connection)
        }
    }

    override fun openArtwork(trackId: String): RemoteLibraryDownload {
        val connection = authenticatedGet("audio/${encodePath(trackId)}/image", binary = true)
        return object : RemoteLibraryDownload {
            override val input get() = connection.inputStream
            override val contentLength get() = connection.getHeaderField("Content-Length")
            override val contentEncoding get() = connection.getHeaderField("Content-Encoding")
            override val contentType get() = connection.getHeaderField("Content-Type")
            override fun close() = release(connection)
        }
    }

    override fun openStream(trackId: String, position: Long, length: Long): RemoteLibraryDownload {
        val range = StreamRange.header(position, length)
        val connection =
            authenticatedGet("audio/${encodePath(trackId)}/stream", binary = true, range = range)
        try {
            val partial = connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            if (partial) StreamRange.validateContentRange(
                connection.getHeaderField("Content-Range"),
                position
            )
            else require(connection.responseCode == HttpURLConnection.HTTP_OK) { "The server did not return audio" }
            val input = connection.inputStream.buffered()
            if (!partial && position > 0) StreamRange.skipFully(input, position)
            val available = StreamRange.responseLength(
                connection.getHeaderFieldLong("Content-Length", -1L),
                connection.getHeaderField("Content-Range"), position, partial,
            )
            return object : RemoteLibraryDownload {
                override val responseHeaders get() = connection.headerFields.filterKeys { it != null }
                override val input = input
                override val contentLength = available.takeIf { it >= 0 }?.toString()
                override val contentEncoding get() = connection.getHeaderField("Content-Encoding")
                override val contentType get() = connection.getHeaderField("Content-Type")
                override fun close() {
                    try {
                        input.close()
                    } finally {
                        release(connection)
                    }
                }
            }
        } catch (error: Throwable) {
            release(connection)
            throw error
        }
    }

    private fun openTrack(trackId: String): HttpURLConnection {
        val connection = authenticatedGet("audio/${encodePath(trackId)}/stream", binary = true)
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            val status = connection.responseCode
            release(connection)
            throw AudioStreamRequestException(
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
        range: String? = null
    ): HttpURLConnection {
        repeat(2) { attempt ->
            ensureAuthenticated()
            checkNotCancelled()
            val connection =
                open(path).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $sessionId")
                    range?.let { setRequestProperty("Range", it) }
                    if (binary) {
                        setRequestProperty("Accept", "application/octet-stream")
                        setRequestProperty("Accept-Encoding", "identity")
                    }
                }
            activeConnection.set(connection)
            try {
                connection.connect()
                if (
                    connection.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED && attempt == 0
                ) {
                    sessionId = null
                    release(connection)
                    return@repeat
                }
                checkResponse(connection)
                return connection
            } catch (error: Throwable) {
                release(connection)
                throw error
            }
        }
        throw IOException("Could not renew the AudioStream session")
    }

    fun release(connection: HttpURLConnection) {
        activeConnection.compareAndSet(connection, null)
        try {
            connection.disconnect()
        } catch (error: Exception) {
            Log.w("LibrarySync", "Could not close the AudioStream connection", error)
        }
    }

    private fun ensureAuthenticated() {
        if (sessionId != null) return
        checkNotCancelled()
        val payload = JSONObject().put("username", username).put("password", password).toString()
        val connection =
            open("auth/login").apply {
                activeConnection.set(this)
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        val response =
            try {
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                checkResponse(connection)
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                release(connection)
            }
        sessionId = JSONObject(response).getString("sessionId")
    }

    private fun requestJson(path: String): JSONObject {
        val connection = authenticatedGet(path)
        val response =
            try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                release(connection)
            }
        return JSONObject(response)
    }

    private fun checkNotCancelled() {
        if (cancelled.get()) throw SyncCancelledException()
    }

    private fun open(path: String): HttpURLConnection =
        (URI(baseUrl).resolve(path).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Cymatic library sync")
        }

    private fun checkResponse(connection: HttpURLConnection) {
        val status = connection.responseCode
        if (status in 200..299) return
        val message = runCatching {
            connection.errorStream?.bufferedReader()?.use { it.readText() }
        }
            .getOrNull()
            .orEmpty()
        val retryAfter =
            connection
                .getHeaderField("Retry-After")
                ?.toLongOrNull()
                ?.coerceIn(0L, 30L)
                ?.times(1000L) ?: 0L
        throw AudioStreamRequestException(
            status,
            retryAfter,
            "AudioStream request failed ($status)${
                if (message.isBlank()) "" else ": ${
                    message.take(
                        500
                    )
                }"
            }",
        )
    }

    private fun encodePath(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
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
