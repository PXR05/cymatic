package com.pxr.cymatic.sync

import java.io.IOException

internal class IncompleteSyncDownloadException(expected: Long, received: Long) :
    IOException("Expected $expected bytes from the audio response, received $received")

internal object SyncDownloadValidation {
    fun expectedSize(
        contentLength: String?,
        contentEncoding: String?,
        contentType: String? = null,
    ): Long {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (
            type.startsWith("text/") ||
                type.startsWith("image/") ||
                type.contains("json") ||
                type.contains("xml")
        ) {
            throw IOException("The server returned $type instead of an audio file")
        }
        if (
            !contentEncoding.isNullOrBlank() &&
                !contentEncoding.equals("identity", ignoreCase = true)
        ) {
            throw IOException("The server returned encoded audio: $contentEncoding")
        }
        return contentLength?.toLongOrNull()?.takeIf { it >= 0L } ?: -1L
    }

    fun validate(received: Long, expected: Long) {
        if (received <= 0L || expected >= 0L && received != expected) {
            throw IncompleteSyncDownloadException(expected.coerceAtLeast(1L), received)
        }
    }
}
