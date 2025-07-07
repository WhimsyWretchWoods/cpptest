package cpp.test

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ImageLoader.nativeInit()

        setContent {
            GalleryScreen()
        }
    }
}

@Composable
fun GalleryScreen() {
    PermissionHandler {
        if (it) {
            ImageGrid()
        } else {
            PermissionDeniedScreen()
        }
    }
}

@Composable
fun PermissionDeniedScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Permission denied. Can't show images.")
    }
}

@Composable
fun PermissionHandler(content: @Composable (Boolean) -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    val permissionLauncher = remember {
        androidx.activity.compose.rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            hasPermission = isGranted
        }
    }

    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
        }
    }

    content(hasPermission)
}

@Composable
fun ImageGrid() {
    val context = LocalContext.current
    var imageUris by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            loadImages(context)
        }
        result?.let {
            imageUris = it
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize()
    ) {
        items(imageUris) { uri ->
            ImageItem(uri)
        }
    }
}

@Composable
fun ImageItem(uri: String) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    DisposableEffect(uri) {
        // TODO: Add your image loading from URI here
        onDispose {
            bitmap?.recycle()
        }
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private suspend fun loadImages(context: Context): List<String>? = withContext(Dispatchers.IO) {
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val cursor = context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC"
    )

    return@withContext cursor?.use {
        val idIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val uris = mutableListOf<String>()
        while (it.moveToNext()) {
            val id = it.getLong(idIndex)
            val uri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                id
            ).toString()
            uris.add(uri)
        }
        uris
    }
}
