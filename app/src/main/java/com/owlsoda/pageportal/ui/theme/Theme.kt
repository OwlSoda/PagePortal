package com.owlsoda.pageportal.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PagePortalLightColorScheme = lightColorScheme(
    primary = PagePortalPurple,
    onPrimary = Color.White,
    primaryContainer = PagePortalPurple.copy(alpha = 0.1f),
    onPrimaryContainer = PagePortalPurple,
    secondary = PagePortalAccent,
    onSecondary = Color.White,
    background = PagePortalBackground,
    onBackground = PagePortalTextPrimary,
    surface = PagePortalSurface,
    onSurface = PagePortalTextPrimary,
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = PagePortalTextSecondary,
    error = PagePortalError,
    onError = Color.White
)

private val PagePortalDarkColorScheme = darkColorScheme(
    primary = PagePortalAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E1E1E),
    onPrimaryContainer = Color.White,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFB0B0B0)
)

@Composable
fun PagePortalTheme(
    mode: String = "SYSTEM",
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when (mode) {
        "LIGHT" -> PagePortalLightColorScheme
        "DARK" -> PagePortalDarkColorScheme
        "AMOLED" -> PagePortalDarkColorScheme.copy(background = Color.Black, surface = Color.Black)
        else -> if (darkTheme) PagePortalDarkColorScheme else PagePortalLightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            val isDark = mode == "DARK" || mode == "AMOLED" || (mode == "SYSTEM" && darkTheme)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
