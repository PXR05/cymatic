package com.pxr.cymatic.ui.screens.settings

enum class ReleaseProduct(val assetPrefix: String) {
    PLAYER("cymatic-player-"),
    LAUNCHER("cymatic-launcher-"),
}

internal fun isProductApk(name: String, assetPrefix: String): Boolean =
    assetPrefix.isNotBlank() && name.startsWith(assetPrefix) && name.endsWith(".apk")
