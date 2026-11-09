package com.pxr.cymatic.sync

import java.io.EOFException
import java.io.InputStream

internal object StreamRange {
    fun header(position: Long, length: Long): String {
        require(position >= 0 && (length == -1L || length > 0))
        return if (length == -1L) "bytes=$position-"
        else "bytes=$position-${Math.addExact(position, length - 1)}"
    }

    fun validateContentRange(value: String?, position: Long) {
        val start = value?.let {
            Regex("bytes (\\d+)-\\d+/(?:\\d+|\\*)").matchEntire(it)?.groupValues?.get(1)
                ?.toLongOrNull()
        }
        require(start == position) { "The server returned an unexpected audio range" }
    }

    fun responseLength(
        contentLength: Long,
        contentRange: String?,
        position: Long,
        partial: Boolean
    ): Long {
        if (contentLength >= 0) return if (partial) contentLength else (contentLength - position).coerceAtLeast(
            0L
        )
        if (!partial) return -1L
        val match = contentRange?.let { Regex("bytes (\\d+)-(\\d+)/(?:\\d+|\\*)").matchEntire(it) }
            ?: return -1L
        val start = match.groupValues[1].toLongOrNull() ?: return -1L
        val end = match.groupValues[2].toLongOrNull() ?: return -1L
        return if (start == position && end >= start && end - start < Long.MAX_VALUE) end - start + 1 else -1L
    }

    fun skipFully(input: InputStream, position: Long) {
        var remaining = position
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) remaining -= skipped
            else if (input.read() == -1) throw EOFException("Seek position is past the end of this track")
            else remaining--
        }
    }
}
