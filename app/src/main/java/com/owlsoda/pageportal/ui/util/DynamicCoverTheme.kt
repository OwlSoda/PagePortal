package com.owlsoda.pageportal.ui.util

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts a harmonized [ColorScheme] from a cover image URL.
 *
 * Uses Android's Palette API to derive dominant, vibrant, and muted colors
 * from the book's cover art. The resulting scheme smoothly cross-fades in
 * when the palette finishes loading, creating the Apple Books / Spotify
 * effect where the entire detail page adapts to the artwork.
 * 
 * Returns null while loading, so callers can fall back to the default theme.
 */
data class CoverColors(
    val dominant: Color,
    val vibrant: Color,
    val muted: Color,
    val darkVibrant: Color,
    val darkMuted: Color,
    val lightVibrant: Color,
    val onDominant: Color,
    val onVibrant: Color
)

/**
 * Derives a cover-aware [ColorScheme] from a cover image URL.
 * 
 * Call this inside a composable to get dynamically-derived colors
 * that smoothly animate when the palette loads.
 */
@Composable
fun rememberCoverColors(
    coverUrl: String?,
    fallbackPrimary: Color = MaterialTheme.colorScheme.primary,
    fallbackSurface: Color = MaterialTheme.colorScheme.surface
): CoverColors? {
    val context = LocalContext.current
    var coverColors by remember(coverUrl) { mutableStateOf<CoverColors?>(null) }
    
    LaunchedEffect(coverUrl) {
        if (coverUrl == null) {
            coverColors = null
            return@LaunchedEffect
        }
        
        coverColors = withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .allowHardware(false) // Palette needs software bitmaps
                    .size(200) // Small size is faster; colors are scale-independent
                    .build()
                    
                val result = loader.execute(request)
                val bitmap = if (result is SuccessResult) {
                    (result.drawable as? BitmapDrawable)?.bitmap
                } else null
                
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    
                    val vibrant = palette.vibrantSwatch
                    val darkVibrant = palette.darkVibrantSwatch
                    val muted = palette.mutedSwatch
                    val darkMuted = palette.darkMutedSwatch
                    val dominant = palette.dominantSwatch
                    val lightVibrant = palette.lightVibrantSwatch
                    
                    CoverColors(
                        dominant = Color(dominant?.rgb ?: muted?.rgb ?: fallbackSurface.hashCode()),
                        vibrant = Color(vibrant?.rgb ?: dominant?.rgb ?: fallbackPrimary.hashCode()),
                        muted = Color(muted?.rgb ?: dominant?.rgb ?: fallbackSurface.hashCode()),
                        darkVibrant = Color(darkVibrant?.rgb ?: dominant?.rgb ?: fallbackSurface.hashCode()),
                        darkMuted = Color(darkMuted?.rgb ?: muted?.rgb ?: fallbackSurface.hashCode()),
                        lightVibrant = Color(lightVibrant?.rgb ?: vibrant?.rgb ?: fallbackPrimary.hashCode()),
                        onDominant = Color(dominant?.bodyTextColor ?: 0xFFFFFFFF.toInt()),
                        onVibrant = Color(vibrant?.bodyTextColor ?: 0xFFFFFFFF.toInt())
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    return coverColors
}

/**
 * Creates an animated color that smoothly transitions when the cover colors change.
 * Use this to make UI elements gracefully adapt to new cover art.
 */
@Composable
fun animatedCoverColor(
    targetColor: Color?,
    fallback: Color,
    durationMillis: Int = 600
): Color {
    val target = targetColor ?: fallback
    val animatedColor by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis),
        label = "coverColor"
    )
    return animatedColor
}
