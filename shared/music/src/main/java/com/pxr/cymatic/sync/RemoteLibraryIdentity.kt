package com.pxr.cymatic.sync

import java.nio.ByteBuffer
import java.security.MessageDigest

fun RemoteLibraryItemKey.audioId(): Long {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("remote:$sourceId:$remoteId".toByteArray(Charsets.UTF_8))
    return -((ByteBuffer.wrap(digest).long and Long.MAX_VALUE).coerceAtLeast(1))
}

internal fun remoteMimeType(filename: String): String? = when (filename.substringAfterLast('.').lowercase()) {
    "mp3" -> "audio/mpeg"
    "m4a", "mp4" -> "audio/mp4"
    "flac" -> "audio/flac"
    "ogg", "opus" -> "audio/ogg"
    "dsf" -> "audio/dsd"
    "wav" -> "audio/wav"
    "aac" -> "audio/aac"
    else -> null
}
