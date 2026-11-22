package com.pxr.cymatic.sync.subsonic

import com.pxr.cymatic.sync.AudioStreamClient
import com.pxr.cymatic.sync.RemoteTrack
import org.json.JSONObject

internal object SubsonicMapping {
    fun normalizeBaseUrl(raw: String): String {
        val normalized = AudioStreamClient.normalizeBaseUrl(raw)
        val withoutTrailing = normalized.trimEnd('/')
        val stripped =
            if (withoutTrailing.endsWith("/rest", ignoreCase = true)) {
                withoutTrailing.dropLast(5)
            } else {
                withoutTrailing
            }
        return stripped.trimEnd('/') + "/"
    }

    fun mapErrorStatus(code: Int): Int =
        when (code) {
            40, 41, 44 -> 401
            50 -> 403
            70 -> 404
            0, 10, 20, 30, 42 -> 400
            else -> 500
        }

    fun parseTrack(item: JSONObject): RemoteTrack? {
        if (item.optBoolean("isVideo", false)) return null
        val type = item.optString("type").takeIf { item.has("type") && it.isNotBlank() }
        if (type != null && !type.equals("music", ignoreCase = true)) return null

        val id = item.optString("id").takeIf(String::isNotBlank) ?: return null
        val title = item.optString("title").ifBlank { "Unknown title" }
        val suffix = item.optString("suffix").ifBlank { "mp3" }
        val rawPath = item.optString("path").takeIf(String::isNotBlank)
        val filename =
            rawPath
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.takeIf(String::isNotBlank)
                ?: "$title.$suffix"
        val size = item.optLong("size", -1L)
        val created = item.optString("created")
        val updatedAt = "$created|$size|$suffix"
        val artist = item.optString("artist").ifBlank { "Unknown artist" }
        val album = item.optString("album").ifBlank { "Unknown album" }
        val albumArtist =
            item.optString("displayAlbumArtist").takeIf(String::isNotBlank)
                ?: item.optString("albumArtist").takeIf(String::isNotBlank)
        val trackNumber = item.optInt("track", -1).takeIf { item.has("track") && it > 0 }
        val discNumber = item.optInt("discNumber", -1).takeIf { item.has("discNumber") && it > 0 }
        val year = item.optInt("year", -1).takeIf { item.has("year") && it > 0 }
        val durationMs =
            item.optLong("duration", -1L)
                .takeIf { item.has("duration") && it >= 0L }
                ?.times(1000L)
        // Subsonic reports bitRate in kbps; Cymatic stores bitRate in bps.
        val bitRate =
            item.optLong("bitRate", -1L)
                .takeIf { item.has("bitRate") && it > 0L }
                ?.times(1000L)
        val sampleRate =
            item.optLong("samplingRate", -1L).takeIf { item.has("samplingRate") && it > 0L }
        val bitDepth =
            item.optInt("bitDepth", -1).takeIf { item.has("bitDepth") && it > 0 }
        val channels =
            item.optInt("channelCount", -1).takeIf { item.has("channelCount") && it > 0 }
        val artworkId =
            item.optString("coverArt").takeIf { item.has("coverArt") && it.isNotBlank() }

        return RemoteTrack(
            id = id,
            filename = filename,
            size = size,
            updatedAt = updatedAt,
            title = title,
            artist = artist,
            album = album,
            trackNumber = trackNumber,
            durationMs = durationMs,
            bitRate = bitRate,
            sampleRate = sampleRate,
            artworkId = artworkId,
            albumArtist = albumArtist,
            discNumber = discNumber,
            bitDepth = bitDepth,
            channels = channels,
            year = year,
        )
    }
}
