package com.pxr.cymatic.ui.components.launcher

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun rememberWallpaperBitmap(): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun loadBitmap() {
        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val drawable = wallpaperManager.drawable ?: wallpaperManager.fastDrawable
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                bitmap = drawable.bitmap
            } else if (drawable != null) {
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap = bmp
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loadBitmap()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        loadBitmap()
    }

    return bitmap
}

@Composable
fun WallpaperBackdrop(
    isSongWallpaperActive: Boolean,
    currentArtworkUri: Uri?,
    nonHomeOffset: Float,
    wallpaperBlurRadius: Float,
    wallpaperDarkenOpacity: Float,
    wallpaperGradientEnabled: Boolean,
    wallpaperBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (isSongWallpaperActive && currentArtworkUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(currentArtworkUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.50f
                        scaleY = 1.50f
                    }
                    .blur(70.dp)
            )

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(currentArtworkUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.20f
                        scaleY = 1.20f
                        alpha = 0.55f
                    }
                    .blur(28.dp)
            )

            val heroAlpha = (1f - nonHomeOffset * 1.5f).coerceIn(0f, 1f)
            if (heroAlpha > 0.001f) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentArtworkUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.58f)
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                            alpha = heroAlpha
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.00f to Color.Black,
                                    0.35f to Color.Black,
                                    0.50f to Color.Black.copy(alpha = 0.95f),
                                    0.65f to Color.Black.copy(alpha = 0.78f),
                                    0.78f to Color.Black.copy(alpha = 0.50f),
                                    0.88f to Color.Black.copy(alpha = 0.22f),
                                    0.96f to Color.Black.copy(alpha = 0.06f),
                                    1.00f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                )
            }

            val homeTopAlpha = 0.05f
            val nonHomeTopAlpha = 0.68f
            val topAlpha = homeTopAlpha + (nonHomeTopAlpha - homeTopAlpha) * nonHomeOffset
            val homeBottomAlpha = 0.65f
            val nonHomeBottomAlpha = 0.74f
            val bottomAlpha = homeBottomAlpha + (nonHomeBottomAlpha - homeBottomAlpha) * nonHomeOffset

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.00f to Color.Black.copy(alpha = topAlpha),
                            0.40f to Color.Black.copy(alpha = topAlpha + (bottomAlpha - topAlpha) * 0.35f),
                            0.75f to Color.Black.copy(alpha = bottomAlpha * 0.90f),
                            1.00f to Color.Black.copy(alpha = bottomAlpha)
                        )
                    )
            )
        } else {
            if (wallpaperBlurRadius > 0.001f && wallpaperBitmap != null) {
                Image(
                    bitmap = wallpaperBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(wallpaperBlurRadius.dp)
                )
            }

            if (wallpaperDarkenOpacity > 0.001f) {
                val topAlpha = if (wallpaperGradientEnabled) {
                    wallpaperDarkenOpacity * nonHomeOffset
                } else {
                    wallpaperDarkenOpacity
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = topAlpha),
                                0.35f to Color.Black.copy(alpha = topAlpha),
                                1.0f to Color.Black.copy(alpha = wallpaperDarkenOpacity)
                            )
                        )
                )
            }
        }
    }
}
