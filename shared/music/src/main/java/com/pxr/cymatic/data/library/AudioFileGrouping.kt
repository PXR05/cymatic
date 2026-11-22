package com.pxr.cymatic.data.library

import com.pxr.cymatic.data.model.AudioFile

const val UnknownArtist = "Unknown Artist"
const val UnknownAlbum = "Unknown Album"

fun artistDisplayName(audioFile: AudioFile): String {
    return mainArtistDisplayName(audioFile.metadata.artist)
}

fun mainArtistDisplayName(rawArtist: String?): String {
    if (rawArtist.isNullOrBlank()) return UnknownArtist
    var start = 0
    val len = rawArtist.length
    while (start < len) {
        var end = start
        while (end < len && rawArtist[end] != ',' && rawArtist[end] != '、') {
            end++
        }
        val segment = rawArtist.substring(start, end).trim()
        if (segment.isNotEmpty()) return segment
        start = end + 1
    }
    return UnknownArtist
}

fun albumDisplayName(audioFile: AudioFile): String {
    val album = audioFile.metadata.album?.trim()
    return if (album.isNullOrEmpty()) UnknownAlbum else album
}

fun filterByArtist(audioFiles: List<AudioFile>, artistName: String): List<AudioFile> {
    return audioFiles.filter { artistDisplayName(it) == artistName }
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
            artistDisplayName(it) == artistName && albumDisplayName(it) == albumName
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
