package com.pxr.cymatic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF424242),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF5A5A5A),
    outline = Color(0xFF101010),
    outlineVariant = Color(0xFFE3E3E3),
    inverseSurface = Color(0xFF121212),
    inverseOnSurface = Color(0xFFEEEEEE)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFF878787),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFD1D1D1),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFD1D1D1),
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = Color(0xFF9E9E9E),
    outline = Color(0xFF5A5A5A),
    outlineVariant = Color(0xFF232323),
    inverseSurface = Color(0xFFEDEDED),
    inverseOnSurface = Color(0xFF111111)
)


@Composable
fun CymaticTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}