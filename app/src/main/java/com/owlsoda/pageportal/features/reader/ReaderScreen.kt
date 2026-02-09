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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.owlsoda.pageportal.R
import androidx.compose.foundation.verticalScroll
import com.owlsoda.pageportal.reader.epub.EpubBook
import com.owlsoda.pageportal.features.reader.pdf.PdfViewer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    isReadAloud: Boolean = false,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    
    // Load book on entry
    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId, context, isReadAloud)
    }

    // UI State for Controls
    var showControls by remember { mutableStateOf(false) } // Default false for immersion
    var showSettings by remember { mutableStateOf(false) }
    
    // Selection State
    var selectedText by remember { mutableStateOf<String?>(null) }
    var selectedRange by remember { mutableStateOf<String?>(null) }
    var showSelectionMenu by remember { mutableStateOf(false) }
    
    // Search State
    var showSearch by remember { mutableStateOf(false) }
    
    // Equalizer State  
    var showEqualizerSheet by remember { mutableStateOf(false) }
    
    // Bookmarks State
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkNote by remember { mutableStateOf("") }
    var showBookmarksSheet by remember { mutableStateOf(false) }

    // WebView reference
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
            @android.webkit.JavascriptInterface
            fun nextChapter() { viewModel.nextChapter() }
            @android.webkit.JavascriptInterface
            fun prevChapter() { viewModel.previousChapter() }
        }
    }
    
    // Effects
    // Style injection disabled - using raw EPUB HTML styling
    // LaunchedEffect(uiState.theme, uiState.fontSize, uiState.fontFamily, uiState.lineHeight, uiState.margin, webViewRef) {
    //     webViewRef?.let { injectStyles(it, uiState) }
    // }
    LaunchedEffect(uiState.book?.title, uiState.currentChapterIndex) {
        if (uiState.book != null && webViewRef != null) {
            val chapter = uiState.book!!.chapters.getOrNull(uiState.currentChapterIndex)
            if (chapter != null) {
                val rawHtml = viewModel.getChapterHtml(uiState.currentChapterIndex)
                android.util.Log.d("ReaderScreen", "Loading chapter ${uiState.currentChapterIndex}: HTML ${if (rawHtml != null) "${rawHtml.length} chars" else "NULL"}")
                
                if (rawHtml != null) {
                    // Inject CSS to fix layout issues while preserving EPUB styling
                    val cssOverride = """
                        <style>
                            html, body {
                                height: auto !important;
                                width: 100% !important;
                                margin: 0 !important;
                                padding: 16px !important;
                                overflow-x: hidden !important;
                                position: static !important;
                            }
                            * {
                                max-width: 100% !important;
                                position: static !important;
                            }
                            body {
                                background-color: ${uiState.theme.backgroundColor} !important;
                                color: ${uiState.theme.textColor} !important;
                            }
                            p, div, span, h1, h2, h3, h4, h5, h6 {
                                color: ${uiState.theme.textColor} !important;
                            }
                        </style>
                    """.trimIndent()
                    
                    // Inject CSS into HTML head
                    val htmlWithCss = if (rawHtml.contains("</head>", ignoreCase = true)) {
                        rawHtml.replace("</head>", "$cssOverride</head>", ignoreCase = true)
                    } else if (rawHtml.contains("<body", ignoreCase = true)) {
                        rawHtml.replace("<body", "<head>$cssOverride</head><body", ignoreCase = true)
                    } else {
                        "<html><head>$cssOverride</head><body>$rawHtml</body></html>"
                    }
                    
                    android.util.Log.d("ReaderScreen", "HTML with CSS: ${htmlWithCss.take(400)}")
                    val baseUrl = "http://localhost/${chapter.href.substringBeforeLast('/')}/"
                    webViewRef?.loadDataWithBaseURL(
                        baseUrl,
                        htmlWithCss,
                        "text/html",
                        "UTF-8",
                        null
                    )
                } else {
                    android.util.Log.e("ReaderScreen", "HTML is NULL for chapter ${uiState.currentChapterIndex}")
                }
            }
        }
    }
    LaunchedEffect(uiState.activeSmilHighlightId) {
        uiState.activeSmilHighlightId?.let { id ->
            webViewRef?.evaluateJavascript("applySmilHighlight('$id');", null)
        }
    }

    // Animation States
    val contentScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showControls) 0.85f else 1f,
        label = "contentScale"
    )
    val contentRadius by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (showControls) 16.dp else 0.dp,
        label = "contentRadius"
    )
    val chromeAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showControls) 1f else 0f,
        label = "chromeAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant) // "Stage" background
    ) {
        // --- 1. Book Content Layer ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding() // Keep away from notches even in immersive mode? Maybe.
                .padding(if (showControls) 16.dp else 0.dp) // Add padding when shrunk
                .background(Color(android.graphics.Color.parseColor(uiState.theme.backgroundColor)))
        ) {
            // Error display only
            if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
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
                                                else -> "application/octet-stream"
                                            }
                                            return WebResourceResponse(mimeType, "UTF-8", stream)
                                         }
                                     }
                                     return super.shouldInterceptRequest(view, request)
                                 }
                                 override fun onPageFinished(view: WebView?, url: String?) {
                                     injectStyles(view!!, uiState)
                                     injectSelectionScript(view)
                                 }
                             }
                             
                             setOnTouchListener { v, event ->
                                 if (event.action == android.view.MotionEvent.ACTION_UP) {
                                     val width = v.width
                                     val x = event.x
                                     
                                     val action = when {
                                        x < width * 0.3 -> uiState.gestureTapLeft
                                        x > width * 0.7 -> uiState.gestureTapRight
                                        else -> uiState.gestureTapCenter
                                     }
                                     
                                     when (action) {
                                         "PREV" -> {
                                            if (uiState.isVerticalScroll) viewModel.previousChapter() 
                                            else webViewRef?.evaluateJavascript( "if (window.scrollX > 0) { window.scrollBy(-window.innerWidth, 0); } else { Android.prevChapter(); }", null)
                                         }
                                         "NEXT" -> {
                                            if (uiState.isVerticalScroll) viewModel.nextChapter()
                                            else webViewRef?.evaluateJavascript( "if ((window.scrollX + window.innerWidth) < document.body.scrollWidth) { window.scrollBy(window.innerWidth, 0); } else { Android.nextChapter(); }", null)
                                         }
                                         "MENU" -> showControls = !showControls
                                         else -> {} // NONE
                                     }
                                 }
                                 false
                             }
                         }
                     },
                     update = { webViewRef = it }
                 )
            }
            
            // Interaction blocker DISABLED for debugging
            // if (showControls) {
            //     Box(
            //         modifier = Modifier
            //             .fillMaxSize()
            //             .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            //                 showControls = false
            //             }
            //     )
            // }
        }

        // --- 2. Chrome Layer (Controls) ---
        if (showControls) {
             // Top Bar
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
                 colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                 modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
             )
             
             // Bottom Control Strip
             Column(
                 modifier = Modifier
                     .align(Alignment.BottomCenter)
                     .fillMaxWidth()
                     .navigationBarsPadding()
                     .padding(bottom = 24.dp),
                 horizontalAlignment = Alignment.CenterHorizontally // Center existing items
             ) {
                  // Playback Controls (if available) - Centered and prominent
                  androidx.compose.animation.AnimatedVisibility(
                      visible = uiState.isReadAloudAvailable,
                      enter = androidx.compose.animation.slideInVertically(
                          initialOffsetY = { it },
                          animationSpec = androidx.compose.animation.core.spring(
                              dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                              stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                          )
                      ) + androidx.compose.animation.fadeIn(),
                      exit = androidx.compose.animation.slideOutVertically(
                          targetOffsetY = { it }
                      ) + androidx.compose.animation.fadeOut()
                  ) {
                      Card(
                          shape = RoundedCornerShape(50), // Pill shape
                          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                          modifier = Modifier.padding(bottom = 16.dp)
                      ) {
                          Row(
                              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                              verticalAlignment = Alignment.CenterVertically,
                              horizontalArrangement = Arrangement.spacedBy(12.dp)
                          ) {
                              // Rewind button
                              IconButton(onClick = { 
                                  view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                  viewModel.rewindAudio(10) 
                              }) {
                                  Icon(
                                      painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_media_rew),
                                      contentDescription = "Rewind 10s",
                                      modifier = Modifier.size(24.dp)
                                  )
                              }
                              
                              // Play/Pause button (larger, central)
                              IconButton(
                                  onClick = { 
                                      view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                                      viewModel.toggleAudioPlay() 
                                  },
                                  modifier = Modifier
                                      .size(56.dp)
                                      .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                              ) {
                                  Icon(
                                      if (uiState.isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                      "Toggle Audio",
                                      tint = MaterialTheme.colorScheme.onPrimary,
                                      modifier = Modifier.size(32.dp)
                                  )
                              }
                              
                              // Forward button
                              IconButton(onClick = { 
                                  view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                  viewModel.forwardAudio(30) 
                              }) {
                                  Icon(
                                      painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_media_ff),
                                      contentDescription = "Forward 30s",
                                      modifier = Modifier.size(24.dp)
                                  )
                              }
                              
                              // Bookmark Button
                              IconButton(onClick = { 
                                  view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                  showBookmarkDialog = true
                              }) {
                                  Icon(
                                      imageVector = Icons.Default.Bookmark,
                                      contentDescription = "Add Bookmark",
                                      modifier = Modifier.size(20.dp)
                                  )
                              }
                              
                              Spacer(Modifier.width(8.dp))

                              Text("${String.format("%.1f", uiState.playbackSpeed)}x", style = MaterialTheme.typography.labelLarge)
                              
                              // Sleep Timer Button
                              var showSleepMenu by remember { mutableStateOf(false) }
                             Box {
                                 IconButton(onClick = { showSleepMenu = true }) {
                                     // Use Timer icon (needs import or vector resource)
                                     // Using Settings placeholder if needed, but preferable to use Timer
                                     Icon(
                                         imageVector = Icons.Default.Timer ?: Icons.Default.Settings, 
                                         contentDescription = "Sleep Timer",
                                         tint = if (uiState.sleepTimerMinutes > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                     )
                                 }
                                 
                                 DropdownMenu(
                                     expanded = showSleepMenu,
                                     onDismissRequest = { showSleepMenu = false }
                                 ) {
                                     listOf(0, 15, 30, 45, 60).forEach { mins ->
                                         DropdownMenuItem(
                                             text = { Text(if (mins == 0) "Off" else "$mins min") },
                                             onClick = { 
                                                 viewModel.setSleepTimer(mins)
                                                 showSleepMenu = false
                                             },
                                             leadingIcon = if (uiState.sleepTimerMinutes == mins) {
                                                 { Icon(Icons.Default.Check, null) }
                                             } else null
                                         )
                                     }
                                  }
                              }
                              
                              // Equalizer Button
                              IconButton(onClick = { 
                                  view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                  showEqualizerSheet = true
                              }) {
                                  Icon(
                                      painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_manage),
                                      contentDescription = "Equalizer",
                                      modifier = Modifier.size(20.dp)
                                  )
                              }
                          }
                      }
                  }
                 
                 // Bottom Navigation (Chapters)
                 BottomAppBar(
                      containerColor = Color.Transparent,
                      contentPadding = PaddingValues(0.dp)
                 ) {
                     IconButton(onClick = { viewModel.previousChapter() }) {
                         Text("<", style = MaterialTheme.typography.titleLarge)
                     }
                     Spacer(Modifier.weight(1f))
                     Text("Chapter ${uiState.currentChapterIndex + 1}/${uiState.book?.chapters?.size ?: 0}")
                     Spacer(Modifier.weight(1f))
                     IconButton(onClick = { viewModel.nextChapter() }) {
                         Text(">", style = MaterialTheme.typography.titleLarge)
                     }
                 }
             }
        }
        
         // Dialogs and Sheets (Overlay)
         if (uiState.isLoading.not() && uiState.error == null) {
             // ... (Keep existing dialog logic: Selection, Search, SettingsSheet)
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
             
             if (showSearch) {
                 SearchDialog(
                     onDismiss = { showSearch = false; viewModel.clearSearch() },
                     onSearch = { query -> viewModel.searchBook(query) },
                     results = uiState.searchResults,
                     isSearching = uiState.isSearching,
                     onResultClick = { result ->
                         showSearch = false
                         // Todo: Navigation logic
                     }
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
                          fontFamily = uiState.fontFamily,
                          lineHeight = uiState.lineHeight,
                          margin = uiState.margin,
                          onFontSizeChanged = viewModel::setFontSize,
                          onThemeChanged = viewModel::setTheme,
                          onFontFamilyChanged = viewModel::setFontFamily,
                          onLineHeightChanged = viewModel::setLineHeight,
                          onMarginChanged = viewModel::setMargin,
                          textAlignment = uiState.textAlignment,
                          onTextAlignmentChanged = viewModel::setTextAlignment,
                          paragraphSpacing = uiState.paragraphSpacing,
                          onParagraphSpacingChanged = viewModel::setParagraphSpacing,
                          brightness = uiState.brightness,
                          onBrightnessChanged = viewModel::setBrightness,
                          isVerticalScroll = uiState.isVerticalScroll,
                          onScrollModeChanged = viewModel::setScrollMode
                      )
                  }
              }
              
              // Equalizer Bottom Sheet
              if (showEqualizerSheet) {
                  EqualizerBottomSheet(
                      currentPreset = uiState.currentEqualizerPreset,
                      onPresetSelected = { preset ->
                          viewModel.setEqualizerPreset(preset)
                      },
                      onDismiss = { showEqualizerSheet = false }
                  )
              }
              
              // Bookmark Dialog
              if (showBookmarkDialog) {
                  androidx.compose.material3.AlertDialog(
                      onDismissRequest = { showBookmarkDialog = false },
                      title = { Text("Add Bookmark") },
                      text = {
                          androidx.compose.material3.TextField(
                              value = bookmarkNote,
                              onValueChange = { bookmarkNote = it },
                              label = { Text("Note (optional)") },
                              singleLine = true
                          )
                      },
                      confirmButton = {
                          TextButton(onClick = {
                              viewModel.addBookmark(bookmarkNote.ifBlank { null })
                              bookmarkNote = ""
                              showBookmarkDialog = false
                          }) {
                              Text("Save")
                          }
                      },
                      dismissButton = {
                          TextButton(onClick = { showBookmarkDialog = false }) {
                              Text("Cancel")
                          }
                      }
                  )
              }
              
              // Bookmarks Bottom Sheet
              if (showBookmarksSheet && bookId.toLongOrNull() != null) {
                  val bookmarks by viewModel.getBookmarks(bookId.toLong()).collectAsState(initial = emptyList())
                  BookmarksBottomSheet(
                      bookmarks = bookmarks,
                      onBookmarkClick = { bookmark ->
                          viewModel.jumpToBookmark(bookmark)
                          showBookmarksSheet = false
                      },
                      onDeleteBookmark = { bookmark ->
                          viewModel.deleteBookmark(bookmark)
                      },
                      onDismiss = { showBookmarksSheet = false }
                  )
              }
         }
    }
}

private fun injectStyles(webView: WebView, state: ReaderUiState) {
    val marginVal = if (state.margin == 0) "10px" else "${state.margin}rem"
    
    val css = if (state.isVerticalScroll) {
        // Vertical Scroll Mode
        """
        document.body.style.height = 'auto';
        document.body.style.overflowY = 'scroll';
        document.body.style.overflowX = 'hidden';
        document.body.style.columnWidth = 'auto';
        document.body.style.columnGap = 'normal';
        document.documentElement.style.scrollBehavior = 'smooth';
        """
    } else {
        // Horizontal Pagination Mode (CSS Columns)
        """
        document.body.style.height = '100vh';
        document.body.style.overflowY = 'hidden';
        document.body.style.overflowX = 'hidden'; // Hide scrollbar, scroll via JS
        document.body.style.columnWidth = '100vw';
        document.body.style.columnGap = '100px'; // Gap between columns off-screen
        """
    }

    // Paragraph Spacing CSS
    val paragraphSpacingCss = if (state.paragraphSpacing != 1.0f) {
        // Default is usually 1em or so. We scale it.
        "p { margin-bottom: ${state.paragraphSpacing}em !important; }"
    } else ""
    
    // Brightness Filter
    val brightnessFilter = if (state.brightness >= 0) {
        "html { filter: brightness(${state.brightness}); background-color: ${state.theme.backgroundColor}; } body { background-color: transparent; }"
    } else {
        "html { filter: none; } body { background-color: ${state.theme.backgroundColor}; }"
    }

    val js = """
        $css
        var styleTag = document.getElementById('reader-style');
        if (!styleTag) {
            styleTag = document.createElement('style');
            styleTag.id = 'reader-style';
            document.head.appendChild(styleTag);
        }
        styleTag.innerHTML = `
            body {
                font-size: ${state.fontSize}%;
                color: ${state.theme.textColor};
                line-height: ${state.lineHeight};
                margin: 0 $marginVal;
                max-width: 100%;
                padding: 0 10px;
                font-family: '${state.fontFamily}', serif;
                text-align: ${state.textAlignment.lowercase()};
            }
            $paragraphSpacingCss
            $brightnessFilter
            .smil-active { 
                background: linear-gradient(135deg, #fff176 0%, #ffeb3b 100%) !important;
                color: black !important; 
                border-radius: 6px;
                padding: 2px 4px;
                box-shadow: 0 2px 8px rgba(255, 235, 59, 0.4);
                transition: all 0.3s cubic-bezier(0.4, 0.0, 0.2, 1);
                animation: smilPulse 2s ease-in-out infinite;
            }
            @keyframes smilPulse {
                0%, 100% { box-shadow: 0 2px 8px rgba(255, 235, 59, 0.4); }
                50% { box-shadow: 0 2px 12px rgba(255, 235, 59, 0.6); }
            }
        `;
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
        
        
        var currentActiveId = null;
        
        function applySmilHighlight(id) {
            if (currentActiveId) {
                 var oldEl = document.getElementById(currentActiveId);
                 if (oldEl) oldEl.classList.remove('smil-active');
            }
            
            var el = document.getElementById(id);
            if (el) {
                el.classList.add('smil-active');
                el.scrollIntoView({behavior: "smooth", block: "center"});
                currentActiveId = id;
            }
        }
        
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
    textAlignment: String,
    paragraphSpacing: Float,
    brightness: Float,
    onFontSizeChanged: (Int) -> Unit,
    onThemeChanged: (ReaderTheme) -> Unit,
    onFontFamilyChanged: (String) -> Unit,
    onLineHeightChanged: (Float) -> Unit,
    onMarginChanged: (Int) -> Unit,
    onTextAlignmentChanged: (String) -> Unit,
    onParagraphSpacingChanged: (Float) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    isVerticalScroll: Boolean,
    onScrollModeChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
    ) {
        Text("Appearance", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))
        
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
        
        // Brightness
        Text("Brightness", style = MaterialTheme.typography.titleMedium)
        val isAuto = brightness < 0
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (isAuto) "System" else "${(brightness * 100).toInt()}%")
            Spacer(Modifier.weight(1f))
            Switch(
                checked = isAuto,
                onCheckedChange = { 
                     if (it) onBrightnessChanged(-1.0f) 
                     else onBrightnessChanged(0.5f)
                }
            )
        }
        if (!isAuto) {
            Slider(
                value = brightness,
                onValueChange = { onBrightnessChanged(it) },
                valueRange = 0.0f..1.0f
            )
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Font Size
        Text("Font Size: $fontSize%", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onFontSizeChanged(fontSize - 10) },
                enabled = fontSize > 50
            ) {
                Icon(Icons.Default.Remove, "Decrease font size")
            }
            Slider(
                value = fontSize.toFloat(),
                onValueChange = { onFontSizeChanged(it.toInt()) },
                valueRange = 50f..200f,
                steps = 14,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { onFontSizeChanged(fontSize + 10) },
                enabled = fontSize < 200
            ) {
                Icon(Icons.Default.Add, "Increase font size")
            }
        }
        
        // Font Family
        Text("Font: $fontFamily", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Serif", "Sans-Serif", "Monospace").forEach { font ->
                FilterChip(
                    selected = fontFamily.equals(font, ignoreCase = true),
                    onClick = { onFontFamilyChanged(font) },
                    label = { Text(font) }
                )
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        // Formatting
        Text("Formatting", style = MaterialTheme.typography.titleMedium)
        
        Text("Alignment", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("LEFT", "JUSTIFY", "CENTER").forEach { align ->
                FilterChip(
                    selected = textAlignment == align,
                    onClick = { onTextAlignmentChanged(align) },
                    label = { Text(align.substring(0, 1) + align.substring(1).lowercase()) }
                )
            }
        }

        Text("Line Height: ${String.format("%.1f", lineHeight)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        Slider(
            value = lineHeight,
            onValueChange = { onLineHeightChanged(it) },
            valueRange = 1.0f..2.5f
        )
        
        Text("Paragraph Spacing: ${String.format("%.1f", paragraphSpacing)}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = paragraphSpacing,
            onValueChange = { onParagraphSpacingChanged(it) },
            valueRange = 0.0f..2.0f
        )
        
        Text("Margin: $margin", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = margin.toFloat(),
            onValueChange = { onMarginChanged(it.toInt()) },
            valueRange = 0f..10f,
            steps = 9
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Scroll Mode Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Scroll Mode", style = MaterialTheme.typography.titleMedium)
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isVerticalScroll) "Vertical" else "Pagination")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isVerticalScroll,
                    onCheckedChange = { onScrollModeChanged(it) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp)) // Padding for bottom nav bar
    }
}
