package com.pxr.cymatic.sync

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class AudioStreamClient(
    baseUrl: String,
    private val username: String,
    private val password: String,
) {
    private val baseUrl = normalizeBaseUrl(baseUrl)
    private var sessionId: String? = null
    private val cancelled = AtomicBoolean(false)
    private val activeConnection = AtomicReference<HttpURLConnection?>(null)

    fun cancel() {
        cancelled.set(true)
        activeConnection.getAndSet(null)?.disconnect()
    }

    fun getTracks(): List<RemoteTrack> {
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
                result += RemoteTrack(
                    id = item.getString("id"),
                    filename = item.getString("filename"),
                    size = item.optLong("size", -1L),
                    updatedAt = item.optString("updatedAt"),
                    title = metadata?.optString("title")?.takeIf(String::isNotBlank)
                        ?: item.getString("filename").substringBeforeLast('.'),
                    artist = metadata?.optString("artist")?.takeIf(String::isNotBlank) ?: "Unknown artist",
                    album = metadata?.optString("album")?.takeIf(String::isNotBlank) ?: "Unknown album",
                )
            }
            page++
            hasNext = body.optBoolean("hasNext", false)
        } while (hasNext)
        return result
    }

    fun getPlaylists(): List<RemotePlaylist> {
        ensureAuthenticated()
        val list = requestJson("playlist/").getJSONArray("playlists")
        val result = mutableListOf<RemotePlaylist>()
        for (index in 0 until list.length()) {
            checkNotCancelled()
            val playlist = list.getJSONObject(index)
            val detail = requestJson("playlist/${encodePath(playlist.getString("id"))}")
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

    fun openTrack(trackId: String): HttpURLConnection {
        ensureAuthenticated()
        checkNotCancelled()
        val connection = open("audio/${encodePath(trackId)}/stream").apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $sessionId")
        }
        activeConnection.set(connection)
        return try {
            connection.connect()
            checkResponse(connection)
            connection
        } catch (error: Throwable) {
            release(connection)
            throw error
        }
    }

    fun release(connection: HttpURLConnection) {
        activeConnection.compareAndSet(connection, null)
        connection.disconnect()
    }

    private fun ensureAuthenticated() {
        if (sessionId != null) return
        checkNotCancelled()
        val payload = JSONObject().put("username", username).put("password", password).toString()
        val connection = open("auth/login").apply {
            activeConnection.set(this)
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        }
        val response = try {
            checkResponse(connection)
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            release(connection)
        }
        sessionId = JSONObject(response).getString("sessionId")
    }

    private fun requestJson(path: String): JSONObject {
        checkNotCancelled()
        val connection = open(path).apply {
            activeConnection.set(this)
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $sessionId")
        }
        val response = try {
            checkResponse(connection)
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
        }.getOrNull().orEmpty()
        connection.disconnect()
        throw IOException("AudioStream request failed ($status)${if (message.isBlank()) "" else ": $message"}")
    }

    private fun encodePath(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        fun normalizeBaseUrl(value: String): String {
            val uri = URI(value.trim())
            require(uri.scheme == "https" || uri.scheme == "http") { "Sync URL must use HTTP or HTTPS" }
            require(!uri.host.isNullOrBlank()) { "Sync URL must include a host" }
            return value.trim().trimEnd('/') + "/"
        }
    }
}
