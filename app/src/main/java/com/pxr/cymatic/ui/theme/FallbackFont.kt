package com.pxr.cymatic.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

class FallbackFont(
    val primaryResId: Int,
    val fallbackResId: Int,
    override val weight: FontWeight = FontWeight.Normal,
    override val style: FontStyle = FontStyle.Normal
) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Blocking,
    typefaceLoader = FallbackTypefaceLoader,
    variationSettings = FontVariation.Settings()
)

private object FallbackTypefaceLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface? {
        if (font !is FallbackFont) return null
        return try {
            val pixelFont = Font.Builder(context.resources, font.primaryResId).build()
            val pixelFamily = FontFamily.Builder(pixelFont).build()

            val cjkFont = Font.Builder(context.resources, font.fallbackResId).build()
            val cjkFamily = FontFamily.Builder(cjkFont).build()

            Typeface.CustomFallbackBuilder(pixelFamily)
                .addCustomFallback(cjkFamily)
                .build()
        } catch (e: Exception) {
            try {
                context.resources.getFont(font.primaryResId)
            } catch (ex: Exception) {
                Typeface.DEFAULT
            }
        }
    }

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface? {
        return loadBlocking(context, font)
    }
}
