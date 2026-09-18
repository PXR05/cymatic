package com.pxr.cymatic.ui.screens.settings

/** Fail closed: never offer another product's APK or an unclassified legacy asset. */
internal fun isProductApk(name: String, assetPrefix: String): Boolean =
    assetPrefix.isNotBlank() && name.startsWith(assetPrefix) && name.endsWith(".apk")
