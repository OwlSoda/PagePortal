package com.owlsoda.pageportal.features.comic

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * A wrapper around Image that adds zoom and pan capabilities.
 * Supports pinch-to-zoom, double-tap to zoom, and panning.
 */
@Composable
fun ZoomableImage(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onTap: (Offset) -> Unit = {},
    maxScale: Float = 3f,
    minScale: Float = 1f
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (scale > 1f) {
                            // Reset to original
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            // Zoom in 2x at the tap point
                            scale = 2f
                            // Calculate offset to center the tap point
                            // This is a simplified centering; for perfect precision 
                            // we'd need to account for current viewport center
                            val center = Offset(size.width / 2f, size.height / 2f)
                            offset = (center - tapOffset) * 2f
                        }
                    },
                    onTap = { tapOffset ->
                        onTap(tapOffset)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(minScale, maxScale)
                    
                    // Allow panning only when zoomed in or actively zooming
                    if (scale > 1f) {
                        val newOffset = offset + pan
                        // Apply bounds logic here if needed, or allow free panning
                        // For now, allow free panning but maybe snap back in future
                        offset = newOffset
                    } else {
                        offset = Offset.Zero
                    }
                }
            }
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}
