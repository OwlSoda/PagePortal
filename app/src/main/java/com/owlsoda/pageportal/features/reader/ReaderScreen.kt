package com.owlsoda.pageportal.features.reader

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.owlsoda.pageportal.R
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
    
    // Selection State
    var selectedText by remember { mutableStateOf<String?>(null) }
    var selectedRange by remember { mutableStateOf<String?>(null) }
    var showSelectionMenu by remember { mutableStateOf(false) }
    
    // Search State
    var showSearch by remember { mutableStateOf(false) }

    // WebView reference to inject JS
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // JS Interface
    val jsInterface = remember {
        object {
            @android.webkit.JavascriptInterface
            fun onSelection(text: String, range: String) {
                selectedText = text
                selectedRange = range
                showSelectionMenu = true
            }
        }
    }
    
    // Effect to update styles when settings change
    LaunchedEffect(uiState.theme, uiState.fontSize, uiState.fontFamily, uiState.lineHeight, uiState.margin, webViewRef) {
        webViewRef?.let { wv ->
            injectStyles(wv, uiState)
            // Re-inject highlights if needed
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

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically(),
                exit = slideOutVertically()
            ) {
                TopAppBar(
                    title = { Text(uiState.book?.title ?: "Reader") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(android.graphics.Color.parseColor(uiState.theme.backgroundColor)).copy(alpha = 0.9f)
                    )
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                 visible = showControls,
                 enter = slideInVertically { it },
                 exit = slideOutVertically { it }
            ) {
                val totalChapters = uiState.book?.chapters?.size ?: 0
                BottomAppBar(
                    containerColor = Color(android.graphics.Color.parseColor(uiState.theme.backgroundColor)).copy(alpha = 0.9f)
                ) {
                    IconButton(onClick = { viewModel.previousChapter() }) {
                        Text("<", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Chapter ${uiState.currentChapterIndex + 1}/$totalChapters")
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { viewModel.nextChapter() }) {
                        Text(">", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(android.graphics.Color.parseColor(uiState.theme.backgroundColor)))
        ) {
             if (uiState.isLoading) {
                 CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
             } else if (uiState.error != null) {
                 Text(
                     text = uiState.error!!,
                     color = MaterialTheme.colorScheme.error,
                     modifier = Modifier.align(Alignment.Center)
                 )
             } else {
                 AndroidView(
                     modifier = Modifier.fillMaxSize(),
                     factory = { ctx ->
                         WebView(ctx).apply {
                             settings.javaScriptEnabled = true
                             settings.domStorageEnabled = true
                             settings.allowFileAccess = false
                             addJavascriptInterface(jsInterface, "Android")
                             
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
                                     injectSelectionScript(view)
                                     // TODO: Restore highlights
                                 }
                             }
                             
                             setOnScrollChangeListener { v, _, scrollY, _, _ ->
                                 val wv = v as WebView
                                 val contentHeight = wv.contentHeight * wv.scale
                                 val viewHeight = wv.height
                                 val progress = if (contentHeight > viewHeight) {
                                     scrollY.toFloat() / (contentHeight - viewHeight)
                                 } else {
                                     1f
                                 }
                                 viewModel.onProgressChanged(uiState.currentChapterIndex, progress)
                             }
                             
                             setOnTouchListener { v, event ->
                                 if (event.action == android.view.MotionEvent.ACTION_UP) {
                                     val width = v.width
                                     val x = event.x
                                     when {
                                        x < width * 0.3 -> viewModel.previousChapter()
                                        x > width * 0.7 -> viewModel.nextChapter()
                                        else -> showControls = !showControls
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
                 
                 // Selection Menu
                 if (showSelectionMenu && selectedText != null) {
                     AlertDialog(
                         onDismissRequest = { showSelectionMenu = false },
                         title = { Text("Selected Text") },
                         text = { Text(selectedText!!) },
                         confirmButton = {
                             TextButton(onClick = {
                                 viewModel.addHighlight(
                                     uiState.currentChapterIndex,
                                     selectedRange ?: "",
                                     selectedText!!
                                 )
                                 showSelectionMenu = false
                                 webViewRef?.evaluateJavascript("highlightSelection('${selectedRange}', 'yellow');", null)
                             }) {
                                 Text("Highlight")
                             }
                         },
                         dismissButton = {
                             TextButton(onClick = { showSelectionMenu = false }) {
                                 Text("Cancel")
                             }
                         }
                     )
                 }
                 
                 // Search Dialog
                 if (showSearch) {
                     SearchDialog(
                         onDismiss = { showSearch = false; viewModel.clearSearch() },
                         onSearch = { query -> viewModel.searchBook(query) },
                         results = uiState.searchResults,
                         isSearching = uiState.isSearching,
                         onResultClick = { result ->
                             // Navigate to chapter
                             // TODO: Scroll to text logic
                             showSearch = false
                         }
                     )
                 }
                 
                 // Settings Sheet
                 if (showSettings) {
                     ModalBottomSheet(
                         onDismissRequest = { showSettings = false },
                         containerColor = MaterialTheme.colorScheme.surface
                     ) {
                          ReaderSettingsSheet(
                              fontSize = uiState.fontSize,
                              currentTheme = uiState.theme,
                              fontFamily = uiState.fontFamily,
                              lineHeight = uiState.lineHeight,
                              margin = uiState.margin,
                              onFontSizeChanged = viewModel::setFontSize,
                              onThemeChanged = viewModel::setTheme,
                              onFontFamilyChanged = viewModel::setFontFamily,
                              onLineHeightChanged = viewModel::setLineHeight,
                              onMarginChanged = viewModel::setMargin
                          )
                      }
                  }
             }
        }
    }
}

private fun injectStyles(webView: WebView, state: ReaderUiState) {
    val marginVal = if (state.margin == 0) "10px" else "${state.margin}rem"
    val js = """
        document.body.style.fontSize = '${state.fontSize}%';
        document.body.style.backgroundColor = '${state.theme.backgroundColor}';
        document.body.style.color = '${state.theme.textColor}';
        document.body.style.lineHeight = '${state.lineHeight}';
        document.body.style.margin = '0 $marginVal';
        document.body.style.maxWidth = '100%';
        document.body.style.padding = '0 10px';
        document.body.style.fontFamily = '${state.fontFamily}, serif';
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}

private fun injectSelectionScript(webView: WebView) {
    val js = """
        document.addEventListener('mouseup', function() {
            var selection = window.getSelection();
            if (selection.toString().length > 0) {
                 var range = selection.getRangeAt(0);
                 // Simple serialization: we just use the text content plus a dummy range ID for now.
                 // In a real app we'd map this to a DOM path.
                 Android.onSelection(selection.toString(), "range_placeholder");
            }
        });
        
        function highlightSelection(rangeId, color) {
             var selection = window.getSelection();
             if (selection.rangeCount > 0) {
                 var range = selection.getRangeAt(0);
                 var span = document.createElement('span');
                 span.style.backgroundColor = color;
                 try {
                     range.surroundContents(span);
                     selection.removeAllRanges();
                 } catch(e) {
                     console.log(e);
                 }
             }
        }
    """
    webView.evaluateJavascript(js, null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchDialog(
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    results: List<SearchResult>,
    isSearching: Boolean,
    onResultClick: (SearchResult) -> Unit
) {
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search in Book") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { 
                        query = it
                        if (it.length >= 3) onSearch(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search...") },
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, "Clear")
                            }
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(results) { result ->
                            ListItem(
                                headlineContent = { Text("Chapter ${result.chapterIndex + 1}") },
                                supportingContent = { 
                                    Text(
                                        result.previewText, 
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    ) 
                                },
                                modifier = Modifier.clickable { onResultClick(result) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ReaderSettingsSheet(
    fontSize: Int,
    currentTheme: ReaderTheme,
    fontFamily: String,
    lineHeight: Float,
    margin: Int,
    onFontSizeChanged: (Int) -> Unit,
    onThemeChanged: (ReaderTheme) -> Unit,
    onFontFamilyChanged: (String) -> Unit,
    onLineHeightChanged: (Float) -> Unit,
    onMarginChanged: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        Text("Appearance", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        
        // Theme
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderTheme.entries.forEach { theme ->
                FilterChip(
                    selected = currentTheme == theme,
                    onClick = { onThemeChanged(theme) },
                    label = { Text(theme.name) }
                )
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        // Font Family
        Text("Font: $fontFamily", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Serif", "Sans-Serif", "Monospace", "Cursive").forEach { font ->
                FilterChip(
                    selected = fontFamily.equals(font, ignoreCase = true),
                    onClick = { onFontFamilyChanged(font) },
                    label = { Text(font) }
                )
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Font Size
        Text("Size: $fontSize%", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = fontSize.toFloat(),
            onValueChange = { onFontSizeChanged(it.toInt()) },
            valueRange = 50f..200f
        )
        
        // Line Height
        Text("Line Height: ${String.format("%.1f", lineHeight)}", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = lineHeight,
            onValueChange = { onLineHeightChanged(it) },
            valueRange = 1.0f..2.5f
        )
        
        // Margin
        Text("Margin: $margin", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = margin.toFloat(),
            onValueChange = { onMarginChanged(it.toInt()) },
            valueRange = 0f..10f,
            steps = 9
        )
        
        Spacer(modifier = Modifier.height(32.dp)) // Padding for bottom nav bar
    }
}
