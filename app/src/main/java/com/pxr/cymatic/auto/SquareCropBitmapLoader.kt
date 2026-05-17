package com.pxr.cymatic.auto

import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.BitmapLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

@UnstableApi
class SquareCropBitmapLoader(private val delegate: DataSourceBitmapLoader) : BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean =
        delegate.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        Futures.transform(
            delegate.decodeBitmap(data),
            ::centerCrop,
            MoreExecutors.directExecutor()
        )

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        Futures.transform(
            delegate.loadBitmap(uri),
            ::centerCrop,
            MoreExecutors.directExecutor()
        )

    private fun centerCrop(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w == h) return src
        val side = minOf(w, h)
        val x = (w - side) / 2
        val y = (h - side) / 2
        val cropped = Bitmap.createBitmap(src, x, y, side, side)
        if (cropped !== src) src.recycle()
        return cropped
    }
}
