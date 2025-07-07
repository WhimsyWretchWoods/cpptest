package cpp.test

import android.graphics.Bitmap
import android.util.LruCache
import kotlin.math.max

object BitmapCache : LruCache<String, Bitmap>(calculateCacheSize()) {
    private fun calculateCacheSize(): Int {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        // Use 1/4 of available memory instead of 1/8 for better caching
        return max(4 * 1024, maxMemory / 4) // Minimum 4MB cache
    }

    override fun sizeOf(key: String, bitmap: Bitmap): Int {
        // Use allocationByteCount if available for more accurate size
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            bitmap.allocationByteCount / 1024
        } else {
            bitmap.byteCount / 1024
        }
    }

    // Pre-warm the cache with common sizes if known
    fun preWarmCache() {
        // Optional: Can add common placeholder bitmaps here
    }
}
