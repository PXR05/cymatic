package com.pxr.cymatic.sync.subsonic

import android.util.Log
import com.pxr.cymatic.sync.RemoteLibraryClient
import com.pxr.cymatic.sync.RemoteLibraryDownload
import com.pxr.cymatic.sync.RemotePlaylist
import com.pxr.cymatic.sync.RemoteRequestException
import com.pxr.cymatic.sync.RemoteTrack
import com.pxr.cymatic.sync.StreamQuality
import com.pxr.cymatic.sync.StreamRange
import com.pxr.cymatic.sync.http.HttpTransport
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.util.Locale

internal data class SubsonicVerification(
    val openSubsonic: Boolean,
    val extensions: List<String>,
    val serverType: String?,
    val serverVersion: String?,
    val resolvedUsername: String?,
    val apiKeyRejected: Boolean,
)

class SubsonicClient(
    baseUrl: String,
    private val username: String,
    private val password: String? = null,
    private val apiKey: String? = null,
    supportsApiKey: Boolean = true,
    apiKeyRejected: Boolean = false,
    private val onApiKeyRejected: (() -> Unit)? = null,
    private val saltProvider: () -> String = SubsonicAuth::generateSalt,
) : RemoteLibraryClient {
    private val baseUrl = SubsonicMapping.normalizeBaseUrl(baseUrl)
    private val transport = HttpTransport(logTag = "LibrarySync", providerName = "Subsonic")

    @Volatile
    private var useApiKey: Boolean =
        !apiKey.isNullOrEmpty() && supportsApiKey && !apiKeyRejected

    override fun cancel() = transport.cancel()

    internal fun verify(): SubsonicVerification {
        val ping = requestJson("ping")
        val openSubsonic = ping.optBoolean("openSubsonic", false)
        val serverType = ping.optString("type").takeIf(String::isNotBlank)
        val serverVersion = ping.optString("serverVersion").takeIf(String::isNotBlank)

        val extensions =
            runCatching {
                val extRoot = requestJson("getOpenSubsonicExtensions")
                val arr = extRoot.optJSONArray("openSubsonicExtensions")
                buildList {
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            arr.optJSONObject(i)?.optString("name")?.takeIf(String::isNotBlank)
                                ?.let(::add)
                        }
                    }
                }
            }.getOrDefault(emptyList())

        val isOpen = openSubsonic || extensions.isNotEmpty()
        val resolvedUsername =
            if (username.isBlank() && !apiKey.isNullOrEmpty() && isOpen) {
                runCatching {
                    requestJson("tokenInfo")
                        .optJSONObject("tokenInfo")
                        ?.optString("username")
                        ?.takeIf(String::isNotBlank)
                }.getOrNull()
            } else {
                username.takeIf(String::isNotBlank)
            }

        return SubsonicVerification(
            openSubsonic = isOpen,
            extensions = extensions,
            serverType = serverType,
            serverVersion = serverVersion,
            resolvedUsername = resolvedUsername,
            apiKeyRejected = !useApiKey && !apiKey.isNullOrEmpty(),
        )
    }

    override fun getTracks(): List<RemoteTrack> {
        val seen = linkedMapOf<String, RemoteTrack>()
        var offset = 0
        val pageSize = 500
        var firstPageEmpty = false

        while (true) {
            transport.checkNotCancelled()
            val body =
                requestJson(
                    "search3",
                    listOf(
                        "query" to "",
                        "artistCount" to "0",
                        "albumCount" to "0",
                        "songCount" to pageSize.toString(),
                        "songOffset" to offset.toString(),
                    ),
                )
            val songs = body.optJSONObject("searchResult3")?.optJSONArray("song")
            val count = songs?.length() ?: 0
            if (offset == 0 && count == 0) {
                firstPageEmpty = true
                break
            }
            if (songs != null) {
                for (i in 0 until count) {
                    val track = SubsonicMapping.parseTrack(songs.getJSONObject(i)) ?: continue
                    seen.putIfAbsent(track.id, track)
                }
            }
            if (count < pageSize) break
            offset += count
        }

        if (firstPageEmpty) {
            walkAlbumsForTracks(seen)
        }
        return seen.values.toList()
    }

    private fun walkAlbumsForTracks(seen: MutableMap<String, RemoteTrack>) {
        var offset = 0
        val pageSize = 500
        while (true) {
            transport.checkNotCancelled()
            val body =
                requestJson(
                    "getAlbumList2",
                    listOf(
                        "type" to "alphabeticalByName",
                        "size" to pageSize.toString(),
                        "offset" to offset.toString(),
                    ),
                )
            val albums = body.optJSONObject("albumList2")?.optJSONArray("album")
            val count = albums?.length() ?: 0
            if (count == 0) break
            for (i in 0 until count) {
                transport.checkNotCancelled()
                val albumId = albums!!.getJSONObject(i).optString("id")
                if (albumId.isBlank()) continue
                val detail = requestJson("getAlbum", listOf("id" to albumId))
                val songs = detail.optJSONObject("album")?.optJSONArray("song") ?: continue
                for (j in 0 until songs.length()) {
                    val track = SubsonicMapping.parseTrack(songs.getJSONObject(j)) ?: continue
                    seen.putIfAbsent(track.id, track)
                }
            }
            if (count < pageSize) break
            offset += count
        }
    }

    override fun getPlaylists(): List<RemotePlaylist> {
        transport.checkNotCancelled()
        val list = requestJson("getPlaylists").optJSONObject("playlists")?.optJSONArray("playlist")
            ?: return emptyList()
        val result = mutableListOf<RemotePlaylist>()
        for (i in 0 until list.length()) {
            transport.checkNotCancelled()
            val item = list.getJSONObject(i)
            val id = item.optString("id").takeIf(String::isNotBlank) ?: continue
            val detail = requestJson("getPlaylist", listOf("id" to id)).optJSONObject("playlist")
                ?: continue
            val name = detail.optString("name").ifBlank { item.optString("name") }
            val entries = detail.optJSONArray("entry")
            val trackIds = buildList {
                if (entries != null) {
                    for (j in 0 until entries.length()) {
                        entries.getJSONObject(j).optString("id").takeIf(String::isNotBlank)
                            ?.let(::add)
                    }
                }
            }
            result += RemotePlaylist(id = id, name = name, trackIds = trackIds)
        }
        return result
    }

    override fun openDownload(trackId: String): RemoteLibraryDownload {
        val connection =
            try {
                openBinary("download", listOf("id" to trackId))
            } catch (error: RemoteRequestException) {
                if (error.status == 403) {
                    openBinary("stream", listOf("id" to trackId, "format" to "raw"))
                } else {
                    throw error
                }
            }
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            val status = connection.responseCode
            transport.release(connection)
            throw RemoteRequestException(
                status,
                0,
                "Expected a complete audio response, received HTTP $status",
            )
        }
        return transport.wrapDownload(connection)
    }

    override fun openStream(
        trackId: String,
        position: Long,
        length: Long,
        quality: StreamQuality,
    ): RemoteLibraryDownload {
        val range = StreamRange.header(position, length)
        val params =
            when (quality) {
                StreamQuality.Original ->
                    listOf("id" to trackId, "format" to "raw")

                is StreamQuality.Mp3 ->
                    listOf(
                        "id" to trackId,
                        "format" to "mp3",
                        "maxBitRate" to quality.kbps.toString(),
                        "estimateContentLength" to "true",
                    )
            }
        val connection = openBinary("stream", params, range = range)
        return transport.wrapRangedStream(connection, position)
    }

    override fun openArtwork(trackId: String, artworkId: String?): RemoteLibraryDownload {
        val id = artworkId?.takeIf(String::isNotBlank) ?: trackId
        val connection = openBinary("getCoverArt", listOf("id" to id, "size" to "1024"))
        return transport.wrapDownload(connection)
    }

    private fun currentAuthMode(): SubsonicAuthMode {
        if (useApiKey && !apiKey.isNullOrEmpty()) {
            return SubsonicAuthMode.ApiKey(apiKey)
        }
        val pass = password?.takeIf(String::isNotEmpty)
            ?: throw IllegalStateException("Sign in to Subsonic first")
        return SubsonicAuthMode.SaltedToken(username, pass)
    }

    private fun buildEndpointUrl(method: String, extraParams: List<Pair<String, String>>): String {
        val params = buildList {
            addAll(SubsonicAuth.queryParams(currentAuthMode(), saltProvider))
            add("v" to API_VERSION)
            add("c" to CLIENT_NAME)
            add("f" to "json")
            addAll(extraParams)
        }
        return "${baseUrl}rest/$method?${SubsonicAuth.buildQuery(params)}"
    }

    private fun requestJson(
        method: String,
        extraParams: List<Pair<String, String>> = emptyList(),
    ): JSONObject {
        repeat(2) { attempt ->
            transport.checkNotCancelled()
            val url = buildEndpointUrl(method, extraParams)
            val connection = transport.open(url).apply { requestMethod = "GET" }
            transport.track(connection)
            val raw =
                try {
                    connection.connect()
                    transport.checkResponse(connection)
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    transport.release(connection)
                }
            val envelope = parseEnvelope(raw)
            val errorCode = envelopeErrorCode(envelope)
            if (errorCode != null) {
                if (attempt == 0 && tryFallbackFromApiKey(errorCode)) {
                    return@repeat
                }
                throw toRequestException(envelope, errorCode)
            }
            return envelope
        }
        throw IOException("Subsonic request failed for $method")
    }

    private fun openBinary(
        method: String,
        extraParams: List<Pair<String, String>>,
        range: String? = null,
    ): HttpURLConnection {
        repeat(2) { attempt ->
            transport.checkNotCancelled()
            val url = buildEndpointUrl(method, extraParams)
            val connection =
                transport.open(url).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/octet-stream, */*")
                    setRequestProperty("Accept-Encoding", "identity")
                    range?.let { setRequestProperty("Range", it) }
                }
            transport.track(connection)
            try {
                connection.connect()
                transport.checkResponse(connection)
                val contentType =
                    connection.getHeaderField("Content-Type").orEmpty().lowercase(Locale.ROOT)
                if (
                    contentType.startsWith("application/json") ||
                    contentType.startsWith("text/xml") ||
                    contentType.startsWith("application/xml")
                ) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    transport.release(connection)
                    val (code, message) = parseBinaryError(body, contentType)
                    if (attempt == 0 && code != null && tryFallbackFromApiKey(code)) {
                        return@repeat
                    }
                    val status = code?.let(SubsonicMapping::mapErrorStatus) ?: 500
                    throw RemoteRequestException(
                        status,
                        0L,
                        "Subsonic $method failed ($status): $message",
                    )
                }
                return connection
            } catch (error: Throwable) {
                transport.release(connection)
                throw error
            }
        }
        throw IOException("Subsonic binary request failed for $method")
    }

    private fun tryFallbackFromApiKey(errorCode: Int): Boolean {
        if (!useApiKey) return false
        if (errorCode != 42 && errorCode != 44) return false
        if (password.isNullOrEmpty() || username.isBlank()) return false
        Log.w("LibrarySync", "Subsonic API key rejected ($errorCode); falling back to token auth")
        useApiKey = false
        onApiKeyRejected?.invoke()
        return true
    }

    private fun parseEnvelope(raw: String): JSONObject {
        val root = JSONObject(raw)
        if (!root.has("subsonic-response")) {
            throw JSONException("Missing subsonic-response envelope")
        }
        return root.getJSONObject("subsonic-response")
    }

    private fun envelopeErrorCode(envelope: JSONObject): Int? {
        if (envelope.optString("status").equals("ok", ignoreCase = true)) return null
        return envelope.optJSONObject("error")?.optInt("code", 0) ?: 0
    }

    private fun toRequestException(envelope: JSONObject, code: Int): RemoteRequestException {
        val message =
            envelope.optJSONObject("error")?.optString("message").orEmpty()
                .ifBlank { "Subsonic error $code" }
        val status = SubsonicMapping.mapErrorStatus(code)
        return RemoteRequestException(status, 0L, "Subsonic request failed ($status): $message")
    }

    private fun parseBinaryError(body: String, contentType: String): Pair<Int?, String> {
        if (contentType.startsWith("application/json")) {
            val env = runCatching { parseEnvelope(body) }.getOrNull()
            if (env != null) {
                val err = env.optJSONObject("error")
                val code = err?.optInt("code", 0)
                val msg = err?.optString("message").orEmpty().ifBlank { "Server returned an error" }
                return code to msg
            }
        }
        val codeMatch = Regex("""code="(\d+)"""").find(body)?.groupValues?.get(1)?.toIntOrNull()
        val msgMatch = Regex("""message="([^"]+)"""").find(body)?.groupValues?.get(1)
        return codeMatch to (msgMatch ?: "Server returned an error")
    }

    companion object {
        private const val API_VERSION = "1.16.1"
        private const val CLIENT_NAME = "Cymatic"
    }
}
