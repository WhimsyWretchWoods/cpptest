package cpp.test

import android.Manifest
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ImageLoader.nativeInit()

        // permission
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            1
        )

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ImageGrid()
                }
            }
        }
    }
}

@Composable
fun ImageGrid() {
    val context = LocalContext.current
    var images by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(Unit) {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )
        cursor?.use {
            val pathIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val paths = mutableListOf<String>()
            while (it.moveToNext()) {
                val path = it.getString(pathIndex)
                paths.add(path)
            }
            images = paths
        }
    }

    LazyVerticalGrid(columns = GridCells.Fixed(4)) {
        items(images) { path ->
            LoadImage(path)
        }
    }
}

@Composable
fun LoadImage(path: String) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            val buffer = ImageLoader.nativeDecodeImage(path)
            if (buffer != null && buffer.size > 8) {
                val width = java.nio.ByteBuffer.wrap(buffer, 0, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                val height = java.nio.ByteBuffer.wrap(buffer, 4, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                val pixels = buffer.copyOfRange(8, buffer.size)
                bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                bitmap?.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(pixels))
            }
        }
    }

    bitmap?.let {
        Image(bitmap = it.asImageBitmap(), contentDescription = null)
    }
}
