package cpp.test

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.graphics.Bitmap.Config
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ImageLoader.nativeInit()

        setContent {
            PermissionRequester(Manifest.permission.READ_MEDIA_IMAGES) { isGranted ->
                if (isGranted) {
                    ImageGrid()
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Permission denied. Can't show images, obviously.")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ImageDecodingDispatcher.close() // Shut down the custom dispatcher
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
    ) { isGranted: Boolean ->
        permissionGranted = isGranted
        Log.d("PermissionRequester", "Permission '$permission' granted: $isGranted")
    }

    LaunchedEffect(Unit) {
        permissionGranted = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            Log.d("PermissionRequester", "Requesting permission: $permission")
            requestPermissionLauncher.launch(permission)
        }
    }

    content(permissionGranted)
}

@Composable
fun ImageGrid() {
    val context = LocalContext.current
    var images by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            cursor?.use {
                val idIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val uris = mutableListOf<String>()
                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    uris.add(uri.toString())
                }
                images = uris
            } ?: run {
                Log.e("ImageGrid", "Failed to query MediaStore, cursor was null.")
            }
        }
    }

    LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxSize()) {
        items(images) { uri ->
            LoadImage(uri)
        }
    }
}

// Custom dispatcher for image decoding to limit concurrency
private val ImageDecodingDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()

@Composable
fun LoadImage(uri: String) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val cachedBitmap = remember(uri) { BitmapCache.get(uri) }
    if (cachedBitmap != null) {
        bitmap = cachedBitmap
        Log.d("LoadImage", "Cache hit for URI: $uri")
    }

    DisposableEffect(uri) {
        var currentBitmap: android.graphics.Bitmap? = null

        if (cachedBitmap == null) {
            coroutineScope.launch(ImageDecodingDispatcher) { // Use custom dispatcher
                val parsedUri = Uri.parse(uri)
                var stream: java.io.InputStream? = null
                try {
                    stream = context.contentResolver.openInputStream(parsedUri)
                    val bytes = stream?.readBytes()

                    if (bytes != null && bytes.isNotEmpty()) {
                        val sampleSize = 2

                        val buffer = ImageLoader.nativeDecodeImageFromBytes(bytes, sampleSize)
                        if (buffer != null && buffer.size > 8) {
                            val width = java.nio.ByteBuffer.wrap(buffer, 0, 4)
                                .order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                            val height = java.nio.ByteBuffer.wrap(buffer, 4, 4)
                                .order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                            val pixels = buffer.copyOfRange(8, buffer.size)

                            if (width > 0 && height > 0 && pixels.size >= width * height * 4) {
                                val newBitmap = android.graphics.Bitmap.createBitmap(
                                    width,
                                    height,
                                    Config.ARGB_8888
                                ).apply {
                                    copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(pixels))
                                }
                                BitmapCache.put(uri, newBitmap) // Add to cache
                                currentBitmap = newBitmap // Track for potential immediate use
                                withContext(Dispatchers.Main) {
                                    bitmap = newBitmap
                                }
                            } else {
                                Log.e("LoadImage", "Invalid image data from native decoder for URI: $uri")
                            }
                        } else {
                            Log.e("LoadImage", "Native decode returned null or too small buffer for URI: $uri")
                        }
                    } else {
                        Log.e("LoadImage", "Failed to read bytes or bytes were empty for URI: $uri")
                    }
                } catch (e: Exception) {
                    Log.e("LoadImage", "Error loading image for URI: $uri", e)
                } finally {
                    stream?.close()
                }
            }
        }

        onDispose {
            // No explicit recycle here. Bitmaps are managed by BitmapCache.
            // The cache's LRU policy will handle eviction and recycling when needed.
            // If you explicitly recycle here, you'll break cache hits.
            currentBitmap = null // Clear reference to avoid accidental use
        }
    }

    bitmap?.let {
        Image(bitmap = it.asImageBitmap(), contentDescription = null)
    }
}
