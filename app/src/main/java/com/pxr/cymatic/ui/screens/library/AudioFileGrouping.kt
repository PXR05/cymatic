package com.pxr.cymatic.ui.screens.library

import com.pxr.cymatic.data.model.AudioFile

internal const val UnknownArtist = "Unknown Artist"
internal const val UnknownAlbum = "Unknown Album"

internal fun artistDisplayName(audioFile: AudioFile): String {
    return mainArtistDisplayName(audioFile.metadata.artist)
}

internal fun mainArtistDisplayName(rawArtist: String?): String {
    val artistList = rawArtist
        ?.trim()
        ?.split(Regex("[、,]"))
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

    return artistList.firstOrNull() ?: UnknownArtist
}

internal fun albumDisplayName(audioFile: AudioFile): String {
    val album = audioFile.metadata.album?.trim()
    return if (album.isNullOrEmpty()) UnknownAlbum else album
}

internal fun filterByArtist(audioFiles: List<AudioFile>, artistName: String): List<AudioFile> {
    return audioFiles.filter { artistDisplayName(it) == artistName }
}

internal fun filterByAlbum(audioFiles: List<AudioFile>, albumName: String): List<AudioFile> {
    return audioFiles.filter { albumDisplayName(it) == albumName }
}


