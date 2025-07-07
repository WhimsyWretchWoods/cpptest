package cpp.test

import android.graphics.Bitmap
import android.util.LruCache

object BitmapCache : LruCache<String, Bitmap>(calculateCacheSize()) {

    private fun calculateCacheSize(): Int {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return maxMemory / 8
    }

    override fun sizeOf(key: String, bitmap: Bitmap): Int {
        return bitmap.byteCount / 1024
    }
}
