package com.pxr.cymatic.data.library

import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.model.AudioMetadata
import java.util.Locale

const val UnknownArtist = "Unknown Artist"
const val UnknownAlbum = "Unknown Album"

private fun dedupeArtistNames(candidates: Sequence<String>): List<String> {
    val seen = HashSet<String>()
    val result = ArrayList<String>()
    for (raw in candidates) {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) continue
        if (seen.add(cleaned.lowercase(Locale.ROOT))) {
            result.add(cleaned)
        }
    }
    return result
}

fun parseArtistNames(rawArtist: String?): List<String> {
    if (rawArtist.isNullOrBlank()) return emptyList()
    return dedupeArtistNames(
        sequence {
            var start = 0
            val len = rawArtist.length
            while (start < len) {
                var end = start
                while (end < len && rawArtist[end] != ';' && rawArtist[end] != '、') {
                    end++
                }
                yield(rawArtist.substring(start, end))
                start = end + 1
            }
        }
    )
}

fun artistNames(metadata: AudioMetadata): List<String> {
    val explicit = dedupeArtistNames(metadata.artists.asSequence())
    if (explicit.isNotEmpty()) return explicit
    return parseArtistNames(metadata.artist)
}

fun artistDisplayNames(audioFile: AudioFile): List<String> {
    return artistNames(audioFile.metadata).ifEmpty { listOf(UnknownArtist) }
}

fun artistDisplayName(audioFile: AudioFile): String {
    return artistDisplayNames(audioFile).first()
}

fun mainArtistDisplayName(rawArtist: String?): String {
    return parseArtistNames(rawArtist).firstOrNull() ?: UnknownArtist
}

fun artistDisplayText(metadata: AudioMetadata, fallback: String = UnknownArtist): String {
    return artistDisplayTextOrNull(metadata) ?: fallback
}

fun artistDisplayTextOrNull(metadata: AudioMetadata): String? {
    val names = artistNames(metadata)
    return if (names.isEmpty()) null else names.joinToString(", ")
}

fun albumDisplayName(audioFile: AudioFile): String {
    val album = audioFile.metadata.album?.trim()
    return if (album.isNullOrEmpty()) UnknownAlbum else album
}

fun filterByArtist(audioFiles: List<AudioFile>, artistName: String): List<AudioFile> {
    return audioFiles.filter { artistName in artistDisplayNames(it) }
}

fun filterByAlbum(audioFiles: List<AudioFile>, albumName: String): List<AudioFile> {
    return sortAlbumTracks(audioFiles.filter { albumDisplayName(it) == albumName })
}

fun filterByArtistAndAlbum(
    audioFiles: List<AudioFile>,
    artistName: String,
    albumName: String,
): List<AudioFile> {
    return sortAlbumTracks(
        audioFiles.filter {
            artistName in artistDisplayNames(it) && albumDisplayName(it) == albumName
        }
    )
}

private fun sortAlbumTracks(tracks: List<AudioFile>): List<AudioFile> {
    val multipleSources = tracks.map { it.sourceId }.distinct().size > 1
    val hasTrackOrder =
        tracks.any { it.metadata.trackNumber != null || it.metadata.discNumber != null }
    if (!multipleSources && !hasTrackOrder) {
        return tracks
    }
    return tracks.sortedWith(
        compareBy<AudioFile>(
            { if (multipleSources) it.sourceId.orEmpty() else "" },
            { it.metadata.discNumber ?: if (it.metadata.trackNumber != null) 1 else Int.MAX_VALUE },
            { it.metadata.trackNumber ?: Int.MAX_VALUE },
            { (it.metadata.title ?: "").lowercase() },
        )
    )
}
