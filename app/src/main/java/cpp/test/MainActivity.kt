package cpp.test

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import android.graphics.Bitmap.Config // Import for Bitmap.Config

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ImageLoader.nativeInit()

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
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
    ) { isGranted: Boolean ->
        permissionGranted = isGranted
    }

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
            }
        }
    }

    LazyVerticalGrid(columns = GridCells.Fixed(4)) {
        items(images) { uri ->
            LoadImage(uri)
        }
    }
}

@Composable
fun LoadImage(uri: String) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            val parsedUri = Uri.parse(uri)
            var stream: java.io.InputStream? = null
            try {
                stream = context.contentResolver.openInputStream(parsedUri)
                val bytes = stream?.readBytes()

                if (bytes != null && bytes.isNotEmpty()) {
                    val buffer = ImageLoader.nativeDecodeImageFromBytes(bytes)
                    if (buffer != null && buffer.size > 8) {
                        val width = java.nio.ByteBuffer.wrap(buffer, 0, 4)
                            .order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                        val height = java.nio.ByteBuffer.wrap(buffer, 4, 4)
                            .order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                        val pixels = buffer.copyOfRange(8, buffer.size)

                        if (width > 0 && height > 0 && pixels.size >= width * height * 4) { // Use >= for safety
                            bitmap = android.graphics.Bitmap.createBitmap(
                                width,
                                height,
                                Config.ARGB_8888 // Reverted to ARGB_8888 as it's standard
                            ).apply {
                                copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(pixels))
                            }
                        } else {
                            android.util.Log.e("LoadImage", "Invalid image data from native decoder for URI: $uri")
                        }
                    } else {
                        android.util.Log.e("LoadImage", "Native decode returned null or too small buffer for URI: $uri")
                    }
                } else {
                    android.util.Log.e("LoadImage", "Failed to read bytes or bytes were empty for URI: $uri")
                }
            } catch (e: Exception) {
                android.util.Log.e("LoadImage", "Error loading image for URI: $uri", e)
            } finally {
                stream?.close()
            }
        }
    }

    bitmap?.let {
        Image(bitmap = it.asImageBitmap(), contentDescription = null)
    }
}
