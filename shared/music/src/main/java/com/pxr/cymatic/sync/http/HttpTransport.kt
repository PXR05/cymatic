package com.pxr.cymatic.sync.http

import android.util.Log
import com.pxr.cymatic.sync.RemoteLibraryDownload
import com.pxr.cymatic.sync.RemoteRequestException
import com.pxr.cymatic.sync.StreamRange
import com.pxr.cymatic.sync.SyncCancelledException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class HttpTransport(
    private val logTag: String = "LibrarySync",
    private val providerName: String = "Server",
) {
    private val cancelled = AtomicBoolean(false)
    private val activeConnection = AtomicReference<HttpURLConnection?>(null)

    val isCancelled: Boolean
        get() = cancelled.get()

    fun cancel() {
        cancelled.set(true)
        activeConnection.getAndSet(null)?.disconnect()
    }

    fun checkNotCancelled() {
        if (cancelled.get()) throw SyncCancelledException()
    }

    fun open(url: String, headers: Map<String, String> = emptyMap()): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Cymatic library sync")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }

    fun track(connection: HttpURLConnection): HttpURLConnection {
        activeConnection.set(connection)
        return connection
    }

    fun release(connection: HttpURLConnection) {
        activeConnection.compareAndSet(connection, null)
        try {
            connection.disconnect()
        } catch (error: Exception) {
            Log.w(logTag, "Could not close the $providerName connection", error)
        }
    }

    fun checkResponse(connection: HttpURLConnection) {
        val status = connection.responseCode
        if (status in 200..299) return
        val message =
            runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }
                .getOrNull()
                .orEmpty()
        val retryAfter =
            connection
                .getHeaderField("Retry-After")
                ?.toLongOrNull()
                ?.coerceIn(0L, 30L)
                ?.times(1000L) ?: 0L
        throw RemoteRequestException(
            status,
            retryAfter,
            "$providerName request failed ($status)${
                if (message.isBlank()) "" else ": ${message.take(500)}"
            }",
        )
    }

    fun wrapDownload(connection: HttpURLConnection): RemoteLibraryDownload =
        object : RemoteLibraryDownload {
            override val responseHeaders get() = connection.headerFields.filterKeys { it != null }
            override val input get() = connection.inputStream
            override val contentLength get() = connection.getHeaderField("Content-Length")
            override val contentEncoding get() = connection.getHeaderField("Content-Encoding")
            override val contentType get() = connection.getHeaderField("Content-Type")
            override fun close() = release(connection)
        }

    fun wrapRangedStream(
        connection: HttpURLConnection,
        position: Long,
    ): RemoteLibraryDownload {
        try {
            val partial = connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            if (partial) {
                StreamRange.validateContentRange(
                    connection.getHeaderField("Content-Range"),
                    position,
                )
            } else {
                require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "The server did not return audio"
                }
            }
            val input = connection.inputStream.buffered()
            if (!partial && position > 0) StreamRange.skipFully(input, position)
            val available =
                StreamRange.responseLength(
                    connection.getHeaderFieldLong("Content-Length", -1L),
                    connection.getHeaderField("Content-Range"),
                    position,
                    partial,
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
            if (error is IOException) throw error
            throw IOException(error.message ?: "Could not open audio stream", error)
        }
    }

    companion object {
        fun encodeQuery(value: String): String =
            URLEncoder.encode(value, Charsets.UTF_8.name())
    }
}
