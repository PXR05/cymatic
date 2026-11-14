package com.pxr.cymatic.ui.screens.settings

internal const val PLAYER_APK_PREFIX = "cymatic-player-"

internal fun isPlayerApk(name: String): Boolean =
    name.startsWith(PLAYER_APK_PREFIX) && name.endsWith(".apk")
