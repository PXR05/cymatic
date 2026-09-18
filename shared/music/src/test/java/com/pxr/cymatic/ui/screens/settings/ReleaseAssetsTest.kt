package com.pxr.cymatic.ui.screens.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseAssetsTest {
    @Test
    fun selectsOnlyTheRequestedProductFromCombinedRelease() {
        for (product in listOf("player", "launcher")) {
            val prefix = "cymatic-$product-"
            assertTrue(isProductApk("${prefix}v0.2.0.apk", prefix))
            val other = if (product == "player") "launcher" else "player"
            assertFalse(isProductApk("cymatic-$other-v0.2.0.apk", prefix))
            assertFalse(isProductApk("cymatic-release-v0.1.9.2.apk", prefix))
            assertFalse(isProductApk("${prefix}v0.2.0.apk.sha256", prefix))
        }
    }

    @Test
    fun missingProductConfigurationCannotSelectAnApk() {
        assertFalse(isProductApk("cymatic-player-v0.2.0.apk", ""))
    }
}
