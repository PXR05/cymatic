package com.pxr.cymatic.setup

internal object MusicSetupTutorial {
    const val target = "Golden record"
    val folders = listOf("Albums", "Playlists", "Extras")
    private val items = listOf(
        listOf("First light", "After hours"),
        listOf("Morning mix", "Night drive"),
        listOf("Old demo", target),
    )

    fun items(folder: Int): List<String> = items[folder.coerceIn(folders.indices)]
}
