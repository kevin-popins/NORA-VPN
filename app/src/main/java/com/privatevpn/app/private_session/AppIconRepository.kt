package com.privatevpn.app.private_session

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class AppIconRepository(
    private val appContext: Context
) {
    private val iconCache = object : LruCache<String, ByteArray>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    suspend fun loadIcons(apps: List<InstalledAppInfo>): Map<String, ByteArray> = withContext(Dispatchers.IO) {
        if (apps.isEmpty()) return@withContext emptyMap()

        val packageManager = appContext.packageManager
        val result = HashMap<String, ByteArray>(apps.size)

        apps.forEach { app ->
            val packageName = app.packageName
            val cached = iconCache.get(packageName)
            if (cached != null) {
                result[packageName] = cached
                return@forEach
            }

            val encoded = runCatching {
                val drawable = packageManager.getApplicationIcon(packageName)
                drawable.toPngBytes(56)
            }.getOrNull()

            if (encoded != null) {
                iconCache.put(packageName, encoded)
                result[packageName] = encoded
            }
        }

        result
    }

    private fun Drawable.toPngBytes(targetSizePx: Int): ByteArray {
        val bitmap = when (this) {
            is BitmapDrawable -> bitmap
            else -> {
                val safeWidth = if (intrinsicWidth > 0) intrinsicWidth else targetSizePx
                val safeHeight = if (intrinsicHeight > 0) intrinsicHeight else targetSizePx
                Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888).also { canvasBitmap ->
                    val canvas = Canvas(canvasBitmap)
                    setBounds(0, 0, canvas.width, canvas.height)
                    draw(canvas)
                }
            }
        }

        val scaled = if (bitmap.width != targetSizePx || bitmap.height != targetSizePx) {
            Bitmap.createScaledBitmap(bitmap, targetSizePx, targetSizePx, true)
        } else {
            bitmap
        }

        return ByteArrayOutputStream().use { stream ->
            scaled.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    }
}
