package com.pxr.cymatic.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PixelIcon(
    @DrawableRes resId: Int,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

fun pixelIcon(
    @DrawableRes resId: Int,
    size: Dp = 16.dp,
): @Composable () -> Unit = {
    PixelIcon(resId = resId, size = size)
}
