package com.pxr.cymatic.data.library

import com.pxr.cymatic.data.model.AudioFile

const val UnknownArtist = "Unknown Artist"
const val UnknownAlbum = "Unknown Album"

fun artistDisplayName(audioFile: AudioFile): String {
    return mainArtistDisplayName(audioFile.metadata.artist)
}

fun mainArtistDisplayName(rawArtist: String?): String {
    val artistList =
        rawArtist
            ?.trim()
            ?.split(Regex("[、,]"))
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    return artistList.firstOrNull() ?: UnknownArtist
}

fun albumDisplayName(audioFile: AudioFile): String {
    val album = audioFile.metadata.album?.trim()
    return if (album.isNullOrEmpty()) UnknownAlbum else album
}

fun filterByArtist(audioFiles: List<AudioFile>, artistName: String): List<AudioFile> {
    return audioFiles.filter { artistDisplayName(it) == artistName }
}

fun filterByAlbum(audioFiles: List<AudioFile>, albumName: String): List<AudioFile> {
    return audioFiles.filter { albumDisplayName(it) == albumName }
}

fun filterByArtistAndAlbum(
    audioFiles: List<AudioFile>,
    artistName: String,
    albumName: String,
): List<AudioFile> {
    return audioFiles.filter {
        artistDisplayName(it) == artistName && albumDisplayName(it) == albumName
    }
}
