package com.pxr.cymatic.ui.screens.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseAssetsTest {
    @Test
    fun selectsOnlyTheRequestedProductFromCombinedRelease() {
        for (product in ReleaseProduct.entries) {
            val prefix = product.assetPrefix
            assertTrue(isProductApk("${prefix}v0.2.0.apk", prefix))
            val other = ReleaseProduct.entries.first { it != product }
            assertFalse(isProductApk("${other.assetPrefix}v0.2.0.apk", prefix))
            assertFalse(isProductApk("cymatic-release-v0.1.9.2.apk", prefix))
            assertFalse(isProductApk("${prefix}v0.2.0.apk.sha256", prefix))
        }
    }

    @Test
    fun missingProductConfigurationCannotSelectAnApk() {
        assertFalse(isProductApk("cymatic-player-v0.2.0.apk", ""))
    }
}
