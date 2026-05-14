package com.pxr.cymatic.ui.components.primitives

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private val PixelateShader = RuntimeShader(
"""
uniform shader composable;
uniform float pixelSize;

half4 main(float2 fragCoord) {
    float2 coord = floor(fragCoord / pixelSize) * pixelSize;
    return composable.eval(coord);
}
"""
)


@Composable
fun PixelatedIcon(
    modifier: Modifier = Modifier,
    imageVector: ImageVector,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.secondary,
    pixelSize : Float = 8f
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.graphicsLayer {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PixelateShader.setFloatUniform("pixelSize", pixelSize)
                renderEffect = RenderEffect.createRuntimeShaderEffect(
                    PixelateShader, "composable"
                ).asComposeRenderEffect()
            }
        }
    )
}