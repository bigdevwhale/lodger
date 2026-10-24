package app.lodger.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import app.lodger.domain.Look
import app.lodger.domain.Photo
import java.io.File
import kotlin.math.roundToInt

/** Room photos: taken by the phone's own camera app, kept in the app's storage, read for places to put marks. */
object Photos {
    private val cache = LruCache<String, Bitmap>(12)

    /** A photo file, upright and no bigger than [max] on its long side. */
    fun load(file: File, max: Int): Bitmap? {
        if (!file.exists()) return null
        val key = "${file.absolutePath}/$max/${file.lastModified()}"
        cache.get(key)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= max) sample *= 2
        val raw = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val turn = runCatching {
            when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val upright = if (turn == 0f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(turn) }, true)
        val k = max.toFloat() / maxOf(upright.width, upright.height)
        val out = if (k < 1f) Bitmap.createScaledBitmap(upright, (upright.width * k).toInt(), (upright.height * k).toInt(), true) else upright
        cache.put(key, out)
        return out
    }

    /** Brightness on a small grid, 36 cells across, row by row. */
    fun lum(b: Bitmap): Triple<FloatArray, Int, Int> {
        val w = 36
        val h = (w * b.height / b.width.toFloat()).roundToInt().coerceIn(12, 96)
        val s = Bitmap.createScaledBitmap(b, w, h, true)
        val px = IntArray(w * h).also { s.getPixels(it, 0, w, 0, 0, w, h) }
        return Triple(FloatArray(px.size) { i -> val c = px[i]; (.299f * Color.red(c) + .587f * Color.green(c) + .114f * Color.blue(c)) / 255f }, w, h)
    }

    /** Reads a freshly taken photo; null if it can't be opened. */
    fun read(file: File, rel: String): Photo? {
        val b = load(file, 640) ?: return null
        val (l, w, h) = lum(b)
        return Photo(rel, Look.spots(l, w, h), System.currentTimeMillis())
    }

    fun copy(context: Context, uri: Uri, to: File): Boolean = runCatching {
        to.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { i -> to.outputStream().use { i.copyTo(it) } } != null
    }.getOrDefault(false)
}
