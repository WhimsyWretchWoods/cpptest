package cpp.test

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ImageLoader.nativeInit()
        BitmapCache.preWarmCache()

        setContent {
            PermissionRequester(Manifest.permission.READ_MEDIA_IMAGES) { isGranted ->
                if (isGranted) {
                    ImageGrid()
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Permission denied. Can't show images.")
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRequester(
    permission: String,
    content: @Composable (Boolean) -> Unit
) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(false) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> permissionGranted = isGranted }

    LaunchedEffect(Unit) {
        permissionGranted = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            requestPermissionLauncher.launch(permission)
        }
    }

    content(permissionGranted)
}

@Composable
fun ImageGrid() {
    val context = LocalContext.current
    var images by remember { mutableStateOf<List<ImageItem>>(emptyList()) }
    val columnCount = 4
    val thumbnailSize = (context.resources.displayMetrics.widthPixels / columnCount)

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
            )
            
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 100" // Limit to 100 images for performance
            )
            
            cursor?.use {
                val idIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val widthIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                
                val items = mutableListOf<ImageItem>()
                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val width = it.getInt(widthIndex)
                    val height = it.getInt(heightIndex)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()
                    items.add(ImageItem(uri, width, height))
                }
                images = items
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        modifier = Modifier.fillMaxSize()
    ) {
        items(images) { item ->
            LoadImage(
                uri = item.uri,
                originalWidth = item.width,
                originalHeight = item.height,
                targetSize = thumbnailSize
            )
        }
    }
}

data class ImageItem(val uri: String, val width: Int, val height: Int)

@Composable
fun LoadImage(uri: String, originalWidth: Int, originalHeight: Int, targetSize: Int) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Calculate sample size for efficient loading
    val sampleSize = calculateSampleSize(originalWidth, originalHeight, targetSize)

    // Check cache first
    val cacheKey = "$uri-$targetSize"
    val cachedBitmap = remember(cacheKey) { BitmapCache.get(cacheKey) }
    
    DisposableEffect(uri) {
        if (cachedBitmap != null) {
            bitmap = cachedBitmap
            isLoading = false
        } else {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val parsedUri = Uri.parse(uri)
                    context.contentResolver.openInputStream(parsedUri)?.use { stream ->
                        // First try to load with BitmapFactory for smaller images
                        if (originalWidth * originalHeight < 4_000_000) { // 4MP threshold
                            val options = BitmapFactory.Options().apply {
                                inSampleSize = sampleSize
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            }
                            val decodedBitmap = BitmapFactory.decodeStream(stream, null, options)
                            decodedBitmap?.let {
                                BitmapCache.put(cacheKey, it)
                                withContext(Dispatchers.Main) {
                                    bitmap = it
                                    isLoading = false
                                }
                            } ?: run {
                                // Fallback to native decoder if BitmapFactory fails
                                loadWithNativeDecoder(stream, sampleSize, targetSize, cacheKey)?.let {
                                    withContext(Dispatchers.Main) {
                                        bitmap = it
                                        isLoading = false
                                    }
                                }
                            }
                        } else {
                            // For larger images, use native decoder directly
                            loadWithNativeDecoder(stream, sampleSize, targetSize, cacheKey)?.let {
                                withContext(Dispatchers.Main) {
                                    bitmap = it
                                    isLoading = false
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LoadImage", "Error loading image: $uri", e)
                    withContext(Dispatchers.Main) {
                        isLoading = false
                    }
                }
            }
        }

        onDispose { /* Cleanup handled by cache */ }
    }

    Box(modifier = Modifier.size(targetSize.dp)) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private suspend fun loadWithNativeDecoder(
    stream: java.io.InputStream,
    sampleSize: Int,
    targetSize: Int,
    cacheKey: String
): Bitmap? {
    return try {
        val bytes = stream.readBytes()
        val buffer = ImageLoader.nativeDecodeImageFromBytes(bytes, sampleSize, targetSize, targetSize)
        if (buffer != null && buffer.size > 8) {
            val width = java.nio.ByteBuffer.wrap(buffer, 0, 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            val height = java.nio.ByteBuffer.wrap(buffer, 4, 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            val pixels = buffer.copyOfRange(8, buffer.size)

            if (width > 0 && height > 0 && pixels.size >= width * height * 4) {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                    copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(pixels))
                    BitmapCache.put(cacheKey, this)
                }
            } else null
        } else null
    } catch (e: Exception) {
        Log.e("LoadImage", "Native decode error", e)
        null
    }
}

private fun calculateSampleSize(originalWidth: Int, originalHeight: Int, targetSize: Int): Int {
    var sampleSize = 1
    if (originalHeight > targetSize || originalWidth > targetSize) {
        val halfHeight = originalHeight / 2
        val halfWidth = originalWidth / 2
        while (halfHeight / sampleSize >= targetSize && halfWidth / sampleSize >= targetSize) {
            sampleSize *= 2
        }
    }
    return sampleSize
}
