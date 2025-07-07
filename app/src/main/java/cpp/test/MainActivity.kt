package cpp.test

import android.net.Uri
import android.os.Bundle
import android.graphics.Bitmap.Config
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import cpp.test.ui.theme.CpptestTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
// Removed android.util.Log import

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ImageLoader.nativeInit()
        setContent {
            CpptestTheme {
                val images = remember { mutableStateOf<List<Uri>>(emptyList()) }
                val context = LocalContext.current

                LaunchedEffect(Unit) {
                    val uriList = getGalleryImageUris(context)
                    images.value = uriList
                }

                GalleryGrid(imageUris = images.value)
            }
        }
    }
}

@Composable
fun GalleryGrid(imageUris: List<Uri>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize()
    ) {
        items(imageUris) { uri ->
            LoadImage(uri = uri.toString())
        }
    }
}

@Composable
fun LoadImage(uri: String) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(uri) {
        var currentBitmap: android.graphics.Bitmap? = null

        coroutineScope.launch(Dispatchers.IO) {
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
                            currentBitmap?.recycle()
                            currentBitmap = newBitmap
                            bitmap = newBitmap
                        }
                    }
                }
            } catch (e: Exception) {
                // No log here, because you're too good for debugging.
            } finally {
                stream?.close()
            }
        }

        onDispose {
            currentBitmap?.recycle()
            currentBitmap = null
        }
    }

    bitmap?.let {
        Image(bitmap = it.asImageBitmap(), contentDescription = null)
    }
}
