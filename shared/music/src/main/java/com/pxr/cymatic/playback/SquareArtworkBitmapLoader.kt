package com.pxr.cymatic.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

@UnstableApi
class SquareArtworkBitmapLoader(context: Context) : BitmapLoader {
    @Suppress("DEPRECATION")
    private val delegate = DataSourceBitmapLoader(context.applicationContext, MAX_ARTWORK_SIZE)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        crop(delegate.decodeBitmap(data))

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        crop(delegate.loadBitmap(uri))

    override fun supportsMimeType(mimeType: String): Boolean =
        delegate.supportsMimeType(mimeType)

    private fun crop(source: ListenableFuture<Bitmap>): ListenableFuture<Bitmap> =
        Futures.transform(source, ::centerCropSquare, MoreExecutors.directExecutor())

    companion object {
        private const val MAX_ARTWORK_SIZE = 1024

        internal fun centerCropSquare(bitmap: Bitmap): Bitmap {
            if (bitmap.width == bitmap.height) return bitmap
            val size = minOf(bitmap.width, bitmap.height)
            val left = (bitmap.width - size) / 2
            val top = (bitmap.height - size) / 2
            return Bitmap.createBitmap(bitmap, left, top, size, size)
        }
    }
}
