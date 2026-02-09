package com.owlsoda.pageportal.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Window size class for responsive layouts
 * Based on Material Design 3 breakpoints
 */
enum class WindowSizeClass {
    COMPACT,   // < 600dp (phones portrait)
    MEDIUM,    // 600-840dp (phones landscape, small tablets)
    EXPANDED   // > 840dp (tablets, foldables)
}

/**
 * Remember the current window size class based on screen width
 */
@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp) {
        when {
            configuration.screenWidthDp < 600 -> WindowSizeClass.COMPACT
            configuration.screenWidthDp < 840 -> WindowSizeClass.MEDIUM
            else -> WindowSizeClass.EXPANDED
        }
    }
}
