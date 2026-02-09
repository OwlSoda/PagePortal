package com.owlsoda.pageportal.features.reader

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.owlsoda.pageportal.reader.epub.EpubBook

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Load book on entry
    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId, context)
    }

    // UI State for Controls
    var showControls by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }

    // WebView reference to inject JS
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    // Effect to update styles when settings change
    LaunchedEffect(uiState.theme, uiState.fontSize, webViewRef) {
        webViewRef?.let { wv ->
            injectStyles(wv, uiState)
        }
    }
    
    // Effect to load content when chapter changes
    LaunchedEffect(uiState.book, uiState.currentChapterIndex, webViewRef) {
        if (uiState.book != null && webViewRef != null) {
            val chapter = uiState.book!!.chapters.getOrNull(uiState.currentChapterIndex)
            if (chapter != null) {
                // We use localhost to allow WebViewClient to intercept
                val url = "http://localhost/${uiState.book!!.basePath}${chapter.href}"
                webViewRef?.loadUrl(url)
            }
        }
    }

    // Effect to update highlight
    LaunchedEffect(uiState.highlightedElementId, webViewRef) {
        val id = uiState.highlightedElementId
        if (id != null && webViewRef != null) {
            val js = """
                // Remove existing highlights
                document.querySelectorAll('.highlight').forEach(el => el.classList.remove('highlight'));
                // Add highlight to new element
                var el = document.getElementById('$id');
                if (el) {
                    el.classList.add('highlight');
                    el.scrollIntoView({behavior: "smooth", block: "center"});
                }
            """.trimIndent()
            webViewRef?.evaluateJavascript(js, null)
        } else if (id == null && webViewRef != null) {
            // Clear highlight
             webViewRef?.evaluateJavascript("document.querySelectorAll('.highlight').forEach(el => el.classList.remove('highlight'));", null)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(android.graphics.Color.parseColor(uiState.theme.backgroundColor)))) {
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            // WebView Content
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 0.dp), // Handle system bars via margins in CSS if needed, or window insets
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false // We intercept requests manually
                        
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val url = request?.url?.toString() ?: return null
                                if (url.startsWith("http://localhost/")) {
                                    val stream = viewModel.getResource(url)
                                    if (stream != null) {
                                        val mimeType = when {
                                            url.endsWith(".css", true) -> "text/css"
                                            url.endsWith(".js", true) -> "text/javascript"
                                            url.endsWith(".jpg", true) || url.endsWith(".jpeg", true) -> "image/jpeg"
                                            url.endsWith(".png", true) -> "image/png"
                                            url.endsWith(".gif", true) -> "image/gif"
                                            url.endsWith(".svg", true) -> "image/svg+xml"
                                            url.endsWith(".xhtml", true) || url.endsWith(".html", true) -> "application/xhtml+xml"
                                            else -> "application/octet-stream"
                                        }
                                        return WebResourceResponse(mimeType, "UTF-8", stream)
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                            
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                injectStyles(view!!, uiState)
                            }
                        }
                        
                        // Tap listener for controls
                        setOnTouchListener { v, event ->
                            if (event.action == android.view.MotionEvent.ACTION_UP) {
                                // Simple logic: if tap in center 30% -> toggle controls
                                // If left 30% -> prev, If right 30% -> next
                                val width = v.width
                                val x = event.x
                                
                                when {
                                    x < width * 0.3 -> {
                                        viewModel.previousChapter()
                                    }
                                    x > width * 0.7 -> {
                                        viewModel.nextChapter()
                                    }
                                    else -> {
                                        showControls = !showControls
                                    }
                                }
                            }
                            false 
                        }
                    }
                },
                update = { webView ->
                    webViewRef = webView
                }
            )
            
            // Controls
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                ReaderTopBar(
                    title = uiState.book?.title ?: "",
                    onBack = onBack,
                    onSettings = { showSettings = true }
                )
            }
            
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ReaderBottomBar(
                    currentChapter = uiState.currentChapterIndex + 1,
                    totalChapters = uiState.book?.chapters?.size ?: 0,
                    isPlaying = uiState.isPlaying,
                    hasAudio = uiState.hasAudio,
                    onPrev = viewModel::previousChapter,
                    onNext = viewModel::nextChapter,
                    onPlayPause = viewModel::toggleAudio,
                    onToggleControls = { showControls = false } // Hide button
                )
            }
            
            if (showSettings) {
                ModalBottomSheet(
                    onDismissRequest = { showSettings = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    ReaderSettingsSheet(
                        fontSize = uiState.fontSize,
                        currentTheme = uiState.theme,
                        onFontSizeChanged = viewModel::setFontSize,
                        onThemeChanged = viewModel::setTheme
                    )
                }
            }
        }
    }
}

private fun injectStyles(webView: WebView, state: ReaderUiState) {
    val js = """
        document.body.style.fontSize = '${state.fontSize}%';
        document.body.style.backgroundColor = '${state.theme.backgroundColor}';
        document.body.style.color = '${state.theme.textColor}';
        document.body.style.lineHeight = '1.6';
        document.body.style.margin = '20px';

        // Add highlight style if not exists
        if (!document.getElementById('highlight-style')) {
            var style = document.createElement('style');
            style.id = 'highlight-style';
            style.innerHTML = '.highlight { background-color: yellow; color: black; }';
            document.head.appendChild(style);
        }
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
    TopAppBar(
        title = { 
            Text(
                text = title, 
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            ) 
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.FormatSize, contentDescription = "Settings")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    )
}

@Composable
fun ReaderBottomBar(
    currentChapter: Int,
    totalChapters: Int,
    isPlaying: Boolean,
    hasAudio: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onToggleControls: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onPrev, enabled = currentChapter > 1) {
                Text("Previous")
            }
            
            if (hasAudio) {
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
            } else {
                // Placeholder for alignment
                Spacer(modifier = Modifier.width(48.dp))
            }

            Text(
                text = "Chapter $currentChapter of $totalChapters",
                style = MaterialTheme.typography.bodyMedium
            )
            
            TextButton(onClick = onNext, enabled = currentChapter < totalChapters) {
                Text("Next")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    fontSize: Int,
    currentTheme: ReaderTheme,
    onFontSizeChanged: (Int) -> Unit,
    onThemeChanged: (ReaderTheme) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .navigationBarsPadding()
    ) {
        Text("Appearance", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))
        
        // Font Size
        Text("Font Size: $fontSize%", style = MaterialTheme.typography.bodyMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onFontSizeChanged(fontSize - 10) },
                enabled = fontSize > 50
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease font size"
                )
            }

            Slider(
                value = fontSize.toFloat(),
                onValueChange = { onFontSizeChanged(it.toInt()) },
                valueRange = 50f..200f,
                steps = 14,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Font size adjustment" }
            )

            IconButton(
                onClick = { onFontSizeChanged(fontSize + 10) },
                enabled = fontSize < 200
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase font size"
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Theme
        Text("Theme", style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ReaderTheme.values().forEach { theme ->
                FilterChip(
                    selected = currentTheme == theme,
                    onClick = { onThemeChanged(theme) },
                    label = { Text(theme.name.lowercase().capitalize()) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color(android.graphics.Color.parseColor(theme.backgroundColor)), RoundedCornerShape(4.dp))
                        )
                    }
                )
            }
        }
    }
}

private fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
