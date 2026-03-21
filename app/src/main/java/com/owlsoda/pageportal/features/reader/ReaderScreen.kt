package com.owlsoda.pageportal.features.reader

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.automirrored.filled.List
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
import kotlin.math.roundToInt
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.owlsoda.pageportal.R
import androidx.compose.foundation.verticalScroll
import com.owlsoda.pageportal.reader.epub.EpubBook
import com.owlsoda.pageportal.features.reader.pdf.PdfViewer
import androidx.compose.ui.platform.LocalConfiguration
import android.webkit.WebSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    isReadAloud: Boolean = false,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSyncingMap by viewModel.isSyncing.collectAsState()
    val isSyncing = isSyncingMap[bookId.toLongOrNull() ?: 0L] == true
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
    
    // TOC State
    var showTocSheet by remember { mutableStateOf(false) }

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
    
    
    // Style injection re-enabled and triggered on configuration changes
    val configuration = LocalConfiguration.current
    LaunchedEffect(uiState.theme, uiState.fontSize, uiState.fontFamily, 
                   uiState.lineHeight, uiState.margin, uiState.isVerticalScroll,
                   uiState.textAlignment, uiState.paragraphSpacing, 
                   uiState.brightness, configuration.screenWidthDp, configuration.screenHeightDp, webViewRef) {
        webViewRef?.let { injectStyles(it, uiState) }
    }

    LaunchedEffect(uiState.book?.title, uiState.currentChapterIndex) {
        if (uiState.book != null && webViewRef != null) {
            val chapter = uiState.book!!.chapters.getOrNull(uiState.currentChapterIndex)
            if (chapter != null) {
                val rawHtml = viewModel.getChapterHtml(uiState.currentChapterIndex)
                android.util.Log.d("ReaderScreen", "Loading chapter ${uiState.currentChapterIndex}: HTML ${if (rawHtml != null) "${rawHtml.length} chars" else "NULL"}")
                
                if (rawHtml != null) {
                    // Inject CSS to fix layout issues while preserving EPUB styling
                    // CAUTION: Do NOT set height/overflow/position on html/body here as it conflicts with injectStyles
                    val cssOverride = """
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <style>
                            /* Base resets that don't interfere with column layout */
                            * { max-width: 100% !important; box-sizing: border-box !important; }
                            html, body {
                                margin: 0 !important;
                                padding: 0 !important;
                                background-color: ${uiState.theme.backgroundColor} !important;
                                color: ${uiState.theme.textColor} !important;
                            }
                            img, video, svg {
                                max-width: 100% !important;
                                height: auto !important;
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
            // Small delay to ensure page is loaded and JS functions are injected
            kotlinx.coroutines.delay(50)
            webViewRef?.evaluateJavascript("if (typeof applySmilHighlight === 'function') { applySmilHighlight('$id'); } else { console.warn('applySmilHighlight not yet available'); }", null)
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
            .background(Color(android.graphics.Color.parseColor(uiState.theme.backgroundColor))) // Match Stage to Theme
    ) {
        // --- 1. Book Content Layer ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .graphicsLayer {
                    scaleX = contentScale
                    scaleY = contentScale
                    clip = true
                    shape = RoundedCornerShape(contentRadius)
                }
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
                             settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                             settings.loadWithOverviewMode = false
                             settings.useWideViewPort = false
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
                            
                            // Gesture detection: swipe for page navigation, tap for menu
                            val gestureDetector = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
                                override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                                    val width = this@apply.width
                                    val x = e.x
                                    when {
                                        x < width * 0.3 -> {
                                            if (uiState.isVerticalScroll) {
                                                // In vertical scroll: left tap goes to previous chapter
                                                viewModel.previousChapter()
                                            } else {
                                                // In horizontal: left tap scrolls left or prev chapter
                                                webViewRef?.evaluateJavascript(
                                                    "if (window.scrollX > 0) { window.scrollBy(-window.innerWidth, 0); } else { Android.prevChapter(); }", null
                                                )
                                            }
                                        }
                                        x > width * 0.7 -> {
                                            if (uiState.isVerticalScroll) {
                                                viewModel.nextChapter()
                                            } else {
                                                webViewRef?.evaluateJavascript(
                                                    "if (Math.ceil(window.scrollX + window.innerWidth) < document.body.scrollWidth) { window.scrollBy(window.innerWidth, 0); } else { Android.nextChapter(); }", null
                                                )
                                            }
                                        }
                                        else -> showControls = !showControls // Center tap: toggle controls
                                    }
                                    return true
                                }
                                
                                override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                                    val diffX = (e2.x - (e1?.x ?: e2.x))
                                    val diffY = (e2.y - (e1?.y ?: e2.y))
                                    if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 100) {
                                        if (uiState.isVerticalScroll) {
                                            // In vertical scroll: swipe changes chapter directly
                                            if (diffX < 0) viewModel.nextChapter() else viewModel.previousChapter()
                                        } else {
                                        if (diffX < 0) {
                                            // Swipe left -> next page
                                            webViewRef?.evaluateJavascript(
                                                "if (Math.ceil(window.scrollX + window.innerWidth) < document.body.scrollWidth) { window.scrollBy(window.innerWidth, 0); } else { Android.nextChapter(); }", null
                                            )
                                        } else {
                                            // Swipe right -> previous page
                                            webViewRef?.evaluateJavascript(
                                                "if (window.scrollX > 0) { window.scrollBy(-window.innerWidth, 0); } else { Android.prevChapter(); }", null
                                            )
                                        }
                                        }
                                        return true
                                    }
                                    return false
                                }
                            })
                            setOnTouchListener { v, event ->
                                gestureDetector.onTouchEvent(event)
                                // In vertical scroll mode, let WebView handle all touch events natively
                                if (uiState.isVerticalScroll) false else v.performClick().let { false }
                            }
                        }
                    },
                    update = { webViewRef = it }
                )
            }
            
            // Interaction blocker DISABLED for debugging
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
                     if (isSyncing) {
                         CircularProgressIndicator(
                             modifier = Modifier.size(24.dp).padding(4.dp),
                             strokeWidth = 2.dp
                         )
                     } else {
                         Icon(
                             Icons.Filled.CloudDone,
                             "Synced",
                             tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                             modifier = Modifier.size(20.dp)
                         )
                     }
                     Spacer(Modifier.width(8.dp))
                     IconButton(onClick = { showTocSheet = true }) {
                         Icon(Icons.AutoMirrored.Filled.List, "Table of Contents")
                     }
                     IconButton(onClick = { showSearch = true }) {
                         Icon(Icons.Filled.Search, "Search")
                     }
                     IconButton(onClick = { showSettings = true }) {
                         Icon(Icons.Filled.Settings, "Settings")
                     }
                 },
                 colors = TopAppBarDefaults.topAppBarColors(
                     containerColor = Color(android.graphics.Color.parseColor(uiState.theme.backgroundColor)).copy(alpha = 0.9f)
                 ),
                 modifier = Modifier
                     .align(Alignment.TopCenter)
                     .statusBarsPadding()
                     .zIndex(10f)
             )
             
             // Bottom Control Strip
             Column(
                 modifier = Modifier
                     .align(Alignment.BottomCenter)
                     .fillMaxWidth()
                     .navigationBarsPadding()
                     .padding(bottom = 24.dp)
                     .zIndex(10f),
                 horizontalAlignment = Alignment.CenterHorizontally
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
                              TextButton(onClick = { 
                                  view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                  viewModel.rewindAudio(uiState.rewindSeconds) 
                              }) {
                                  Text("-${uiState.rewindSeconds}s", style = MaterialTheme.typography.labelMedium)
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
                                      if (uiState.isPlayingAudio) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                      "Toggle Audio",
                                      tint = MaterialTheme.colorScheme.onPrimary,
                                      modifier = Modifier.size(32.dp)
                                  )
                              }
                              
                              // Forward button
                              TextButton(onClick = { 
                                  view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                  viewModel.forwardAudio(uiState.forwardSeconds) 
                              }) {
                                  Text("+${uiState.forwardSeconds}s", style = MaterialTheme.typography.labelMedium)
                              }
                              
                              Spacer(Modifier.width(8.dp))

                              // Playback Speed Dropdown
                              var showSpeedMenu by remember { mutableStateOf(false) }
                              Box {
                                  TextButton(onClick = { showSpeedMenu = true }) {
                                      Text("${String.format("%.1f", uiState.playbackSpeed)}x", 
                                           style = MaterialTheme.typography.labelLarge)
                                  }
                                  DropdownMenu(
                                      expanded = showSpeedMenu,
                                      onDismissRequest = { showSpeedMenu = false }
                                  ) {
                                      listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f).forEach { speed ->
                                          DropdownMenuItem(
                                              text = { Text("${String.format("%.1f", speed)}x") },
                                              onClick = {
                                                  viewModel.setPlaybackSpeed(speed)
                                                  showSpeedMenu = false
                                              },
                                              leadingIcon = if (uiState.playbackSpeed == speed) {
                                                  { Icon(Icons.Filled.Check, null) }
                                              } else null
                                          )
                                      }
                                  }
                              }
                              
                              // Sleep Timer Button
                              var showSleepMenu by remember { mutableStateOf(false) }
                             Box {
                                 IconButton(onClick = { showSleepMenu = true }) {
                                     Icon(
                                         imageVector = Icons.Filled.Timer ?: Icons.Filled.Settings,
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
                                                 { Icon(Icons.Filled.Check, null) }
                                             } else null
                                         )
                                     }
                                  }
                              }
                              
                          }
                      }
                  }
                 
                 // Chapter indicator (no nav buttons - use swipe or TOC)
                 Box(
                     modifier = Modifier
                         .fillMaxWidth()
                         .padding(horizontal = 16.dp, vertical = 8.dp),
                     contentAlignment = Alignment.Center
                 ) {
                     Text(
                         text = uiState.book?.chapters?.getOrNull(uiState.currentChapterIndex)?.title 
                             ?: "Chapter ${uiState.currentChapterIndex + 1} of ${uiState.book?.chapters?.size ?: 0}",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                     )
                 }
             }
        }
        
         // Dialogs and Sheets (Overlay)
         if (uiState.isLoading.not() && uiState.error == null) {
             // Selection Dialog
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
                                  selectedText!!,
                                  color = uiState.smilHighlightColor
                              )
                              showSelectionMenu = false
                              webViewRef?.evaluateJavascript("highlightSelection('${selectedRange}', '${uiState.smilHighlightColor}');", null)
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
                          onScrollModeChanged = viewModel::setScrollMode,
                          rewindSeconds = uiState.rewindSeconds,
                          forwardSeconds = uiState.forwardSeconds,
                          onRewindSecondsChanged = viewModel::setRewindSeconds,
                          onForwardSecondsChanged = viewModel::setForwardSeconds,
                          smilHighlightColor = uiState.smilHighlightColor,
                          smilUnderlineColor = uiState.smilUnderlineColor,
                          onSmilHighlightColorChanged = viewModel::setSmilHighlightColor,
                          onSmilUnderlineColorChanged = viewModel::setSmilUnderlineColor
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
              
              // Table of Contents Bottom Sheet
              if (showTocSheet) {
                  ModalBottomSheet(
                      onDismissRequest = { showTocSheet = false },
                      containerColor = MaterialTheme.colorScheme.surface
                  ) {
                      Column(modifier = Modifier.padding(16.dp)) {
                          Text(
                              "Table of Contents",
                              style = MaterialTheme.typography.titleLarge,
                              modifier = Modifier.padding(bottom = 16.dp)
                          )
                          LazyColumn(
                              modifier = Modifier.heightIn(max = 400.dp)
                          ) {
                              val chapters = uiState.book?.chapters ?: emptyList()
                              items(chapters.size) { index ->
                                  val chapter = chapters[index]
                                  val isCurrentChapter = index == uiState.currentChapterIndex
                                  val hasAudio = uiState.book?.smilData?.get(chapter.id)?.parList?.isNotEmpty() == true
                                  ListItem(
                                      headlineContent = {
                                          Text(
                                              text = chapter.title ?: "Chapter ${index + 1}",
                                              style = if (isCurrentChapter) MaterialTheme.typography.bodyLarge.copy(
                                                  fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                              ) else MaterialTheme.typography.bodyLarge,
                                              color = if (isCurrentChapter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                          )
                                      },
                                      trailingContent = {
                                          if (hasAudio) {
                                              Text("🔊", style = MaterialTheme.typography.bodySmall)
                                          }
                                      },
                                      modifier = Modifier.clickable {
                                          viewModel.jumpToChapter(index)
                                          showTocSheet = false
                                      },
                                      colors = ListItemDefaults.colors(
                                          containerColor = if (isCurrentChapter) 
                                              MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                                          else Color.Transparent
                                      )
                                  )
                                  if (index < chapters.size - 1) HorizontalDivider()
                              }
                          }
                      }
                  }
              }
         }
    }
}

private fun injectStyles(webView: WebView, state: ReaderUiState) {
    val marginVal = if (state.margin == 0) "10px" else "${state.margin}rem"
    
    val css = if (state.isVerticalScroll) {
        // Vertical Scroll Mode
        """
        document.documentElement.style.height = 'auto';
        document.documentElement.style.width = '100vw';
        document.documentElement.style.overflowX = 'hidden';
        document.documentElement.style.overflowY = 'auto';
        
        document.body.style.height = 'auto';
        document.body.style.width = '100vw';
        document.body.style.overflowX = 'hidden';
        document.body.style.overflowY = 'visible';
        document.body.style.columnWidth = 'auto';
        document.body.style.columnGap = 'normal';
        document.body.style.display = 'block';
        """
    } else {
        // Horizontal Pagination Mode (Strict CSS Columns)
        """
        document.documentElement.style.height = '100vh';
        document.documentElement.style.width = '100vw';
        document.documentElement.style.overflow = 'hidden';
        
        document.body.style.height = '100vh';
        document.body.style.width = '100vw';
        document.body.style.margin = '0';
        document.body.style.padding = '0';
        document.body.style.overflow = 'hidden';
        document.body.style.display = 'block';
        document.body.style.columnWidth = '100vw';
        document.body.style.columnGap = '0px';
        document.body.style.columnFill = 'auto';
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
                font-size: ${state.fontSize}% !important;
                color: ${state.theme.textColor} !important;
                line-height: ${state.lineHeight} !important;
                margin: 0 !important;
                padding: 24px $marginVal !important;
                font-family: '${state.fontFamily}', serif !important;
                text-align: ${state.textAlignment.lowercase()} !important;
                box-sizing: border-box !important;
                -webkit-column-break-inside: avoid;
                page-break-inside: avoid;
                break-inside: avoid;
            }
            p, div, span, h1, h2, h3, h4, h5, h6, li, td, th, a, em, strong, b, i, blockquote {
                color: inherit !important;
                word-wrap: break-word !important;
                overflow-wrap: break-word !important;
            }
            $paragraphSpacingCss
            $brightnessFilter
            .smil-active { 
                background: ${state.smilHighlightColor} !important;
                color: #000000 !important; 
                text-decoration: underline 3px ${state.smilUnderlineColor} !important;
                text-underline-offset: 3px !important;
                border-radius: 4px;
                padding: 2px 4px;
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
        var smilDebugDone = false;
        
        function applySmilHighlight(id) {
            console.log('SMIL highlight: ' + id);
            
            // Remove previous highlight
            if (currentActiveId) {
                 var oldEl = document.getElementById(currentActiveId);
                 if (!oldEl) oldEl = document.querySelector('[data-smil-id="' + currentActiveId + '"]');
                 if (!oldEl) oldEl = document.querySelector('.smil-active');
                 if (oldEl) oldEl.classList.remove('smil-active');
            }
            
            // Strategy 1: direct getElementById
            var el = document.getElementById(id);
            
            // Strategy 2: data-id attribute
            if (!el) el = document.querySelector('[data-id="' + id + '"]');
            
            // Strategy 3: name attribute
            if (!el) el = document.querySelector('[name="' + id + '"]');
            
            // Strategy 4: epub:type or data-smil-id
            if (!el) el = document.querySelector('[epub\\:type="' + id + '"]');
            if (!el) el = document.querySelector('[data-smil-id="' + id + '"]');
            
            // Strategy 5: partial ID match (e.g. 'para1' matches 'word_para1_3')
            if (!el) {
                var allEls = document.querySelectorAll('[id*="' + id + '"]');
                if (allEls.length > 0) el = allEls[0];
            }
            
            // Strategy 6: CSS class with the id name
            if (!el) el = document.querySelector('.' + id);
            
            if (el) {
                el.classList.add('smil-active');
                el.scrollIntoView({behavior: 'smooth', block: 'center'});
                currentActiveId = id;
                console.log('SMIL highlighted: ' + id + ' tag=' + el.tagName);
            } else {
                // Debug: log available IDs on first miss
                if (!smilDebugDone) {
                    smilDebugDone = true;
                    var ids = [];
                    var allElements = document.querySelectorAll('[id]');
                    allElements.forEach(function(e) { ids.push(e.id); });
                    console.warn('SMIL: element "' + id + '" not found. Available IDs (' + ids.length + '): ' + ids.slice(0, 20).join(', '));
                    // Also log elements with data-id
                    var dataIds = [];
                    document.querySelectorAll('[data-id]').forEach(function(e) { dataIds.push(e.getAttribute('data-id')); });
                    if (dataIds.length > 0) console.warn('SMIL: data-id attrs: ' + dataIds.slice(0, 20).join(', '));
                } else {
                    console.warn('SMIL element not found: ' + id);
                }
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
                                Icon(Icons.Filled.Close, "Clear")
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

@OptIn(ExperimentalLayoutApi::class)
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
    onScrollModeChanged: (Boolean) -> Unit,
    rewindSeconds: Int,
    forwardSeconds: Int,
    onRewindSecondsChanged: (Int) -> Unit,
    onForwardSecondsChanged: (Int) -> Unit,
    smilHighlightColor: String,
    smilUnderlineColor: String,
    onSmilHighlightColorChanged: (String) -> Unit,
    onSmilUnderlineColorChanged: (String) -> Unit
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
                Icon(Icons.Filled.Remove, "Decrease font size")
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
                Icon(Icons.Filled.Add, "Increase font size")
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onLineHeightChanged(((lineHeight - 0.1f) * 10).roundToInt() / 10f) },
                enabled = lineHeight > 1.05f
            ) {
                Icon(Icons.Filled.Remove, "Decrease line height")
            }
            Slider(
                value = lineHeight,
                onValueChange = { onLineHeightChanged(((it) * 10).roundToInt() / 10f) },
                valueRange = 1.0f..2.5f,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { onLineHeightChanged(((lineHeight + 0.1f) * 10).roundToInt() / 10f) },
                enabled = lineHeight < 2.45f
            ) {
                Icon(Icons.Filled.Add, "Increase line height")
            }
        }
        
        Text("Paragraph Spacing: ${String.format("%.1f", paragraphSpacing)}", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onParagraphSpacingChanged(((paragraphSpacing - 0.1f) * 10).roundToInt() / 10f) },
                enabled = paragraphSpacing > 0.05f
            ) {
                Icon(Icons.Filled.Remove, "Decrease paragraph spacing")
            }
            Slider(
                value = paragraphSpacing,
                onValueChange = { onParagraphSpacingChanged(((it) * 10).roundToInt() / 10f) },
                valueRange = 0.0f..2.0f,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { onParagraphSpacingChanged(((paragraphSpacing + 0.1f) * 10).roundToInt() / 10f) },
                enabled = paragraphSpacing < 1.95f
            ) {
                Icon(Icons.Filled.Add, "Increase paragraph spacing")
            }
        }
        
        Text("Margin: $margin", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onMarginChanged(margin - 1) },
                enabled = margin > 0
            ) {
                Icon(Icons.Filled.Remove, "Decrease margin")
            }
            Slider(
                value = margin.toFloat(),
                onValueChange = { onMarginChanged(it.toInt()) },
                valueRange = 0f..10f,
                steps = 9,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { onMarginChanged(margin + 1) },
                enabled = margin < 10
            ) {
                Icon(Icons.Filled.Add, "Increase margin")
            }
        }
        
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
        
        Spacer(modifier = Modifier.height(32.dp)) 

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        // Audio Settings
        Text("Audio Settings", style = MaterialTheme.typography.titleMedium)
        
        Text("Rewind Duration: ${rewindSeconds}s", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 30).forEach { seconds ->
                FilterChip(
                    selected = rewindSeconds == seconds,
                    onClick = { onRewindSecondsChanged(seconds) },
                    label = { Text("${seconds}s") }
                )
            }
        }

        Text("Forward Duration: ${forwardSeconds}s", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 15, 30, 60).forEach { seconds ->
                FilterChip(
                    selected = forwardSeconds == seconds,
                    onClick = { onForwardSecondsChanged(seconds) },
                    label = { Text("${seconds}s") }
                )
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // ReadAloud Colors
        Text("ReadAloud Style", style = MaterialTheme.typography.titleMedium)
        
        val presetColors = listOf(
            "#FFF176", "#FFD54F", "#FFB74D", "#FF8A65", 
            "#AED581", "#81C784", "#4DB6AC", "#4FC3F7",
            "#BA68C8", "#F06292", "#E57373", "#FF6D00"
        )

        Text("Highlight Color", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presetColors.forEach { color ->
                val isSelected = smilHighlightColor.equals(color, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(android.graphics.Color.parseColor(color)))
                        .clickable { onSmilHighlightColorChanged(color) }
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Filled.Check,
                            null, 
                            modifier = Modifier.size(16.dp),
                            tint = if (color.lowercase() == "#fff176") Color.Black else Color.White
                        )
                    }
                }
            }
        }

        Text("Underline Color", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presetColors.forEach { color ->
                val isSelected = smilUnderlineColor.equals(color, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(android.graphics.Color.parseColor(color)))
                        .clickable { onSmilUnderlineColorChanged(color) }
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Filled.Check,
                            null, 
                            modifier = Modifier.size(16.dp),
                            tint = if (color.lowercase() == "#fff176") Color.Black else Color.White
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp)) // Padding for bottom nav bar
    }
}
