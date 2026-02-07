package com.owlsoda.pageportal.features.reader.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.IOException

@Composable
fun PdfViewer(
    file: File,
    modifier: Modifier = Modifier,
    onTap: () -> Unit
) {
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    val listState = rememberLazyListState()
    
    // Initialize Renderer
    DisposableEffect(file) {
        val fileDescriptor = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: IOException) {
            error = "Failed to open PDF file"
            null
        }

        if (fileDescriptor != null) {
            try {
                val pdfRenderer = PdfRenderer(fileDescriptor)
                renderer = pdfRenderer
                pageCount = pdfRenderer.pageCount
            } catch (e: Exception) {
                error = "Failed to initialize PDF Renderer"
            }
        }

        onDispose {
            renderer?.close()
            fileDescriptor?.close()
        }
    }

    if (error != null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = error!!, color = MaterialTheme.colorScheme.error)
        }
        return
    }

    if (renderer == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Zoom State
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black) // Dark background for contrast
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { 
                        // Reset zoom or zoom in
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2f
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    if (scale == 1f) {
                        offsetX = 0f
                        offsetY = 0f
                    } else {
                        val maxOffsetX = (size.width * (scale - 1)) / 2
                        val maxOffsetY = (size.height * (scale - 1)) / 2
                        offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                    }
                }
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pageCount) { index ->
                PdfPage(
                    renderer = renderer!!,
                    index = index
                )
            }
        }
    }
}

@Composable
fun PdfPage(
    renderer: PdfRenderer,
    index: Int
) {
    // Render bitmap for this page
    // Using a key to force re-render if renderer changes, though renderer is stable here
    var bitmap by remember(renderer, index) { mutableStateOf<Bitmap?>(null) }
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val density = LocalDensity.current
    
    // We want the bitmap width to match screen width (at least) for high quality
    val reqWidthPx = with(density) { screenWidth.toPx().toInt() }

    LaunchedEffect(renderer, index, reqWidthPx) {
        // Run rendering on IO thread to avoid blocking main
        // However, PdfRenderer is not thread-safe! It must be used synchronized or on one thread.
        // For simplicity in MVP, we might run on Main or use a mutex. 
        // Docs say: "usage of this class is not thread safe"
        
        // Actually rendering a bitmap takes time (100ms+), so strictly main thread might jank scrolling.
        // But since we can only use the renderer from one thread, we should probably stick to Main 
        // or use a dedicated single-thread dispatcher. 
        // Let's try Main first with LaunchedEffect (Dispatchers.Main by default).
        
        try {
            val page = renderer.openPage(index)
            // Calculate height to maintain aspect ratio
            val aspectRatio = page.width.toFloat() / page.height.toFloat()
            val reqHeightPx = (reqWidthPx / aspectRatio).toInt()
            
            val bmp = Bitmap.createBitmap(reqWidthPx, reqHeightPx, Bitmap.Config.ARGB_8888)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap = bmp
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Page ${index + 1}",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(
                    bitmap!!.width.toFloat() / bitmap!!.height.toFloat()
                )
                .background(Color.White),
            contentScale = ContentScale.FillWidth
        )
    } else {
        // Placeholder with correct aspect ratio if known? 
        // Or just a loader with fixed height
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp) // Approximate page height
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
