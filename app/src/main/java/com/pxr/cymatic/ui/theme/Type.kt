package com.pxr.cymatic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.pxr.cymatic.R

val PixelFontFamily = FontFamily(
    FallbackFont(
        primaryResId = R.font.pixel,
        fallbackResId = R.font.pixel_cjk,
        weight = FontWeight.Normal,
        style = FontStyle.Normal
    )
)

private val defaultTypography = Typography()

val Typography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = PixelFontFamily),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = PixelFontFamily),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = PixelFontFamily),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = PixelFontFamily),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = PixelFontFamily),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = PixelFontFamily),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = PixelFontFamily),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = PixelFontFamily),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = PixelFontFamily),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = PixelFontFamily),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = PixelFontFamily),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = PixelFontFamily),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = PixelFontFamily),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = PixelFontFamily),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = PixelFontFamily)
)