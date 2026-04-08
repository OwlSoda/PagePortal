package com.owlsoda.pageportal.features.reader

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebReaderScreen(
    url: String,
    onBack: () -> Unit,
    viewModel: WebReaderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var webView: WebView? by remember { mutableStateOf(null) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(url) {
        viewModel.initialize(url)
    }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = isLoading || state.isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TopAppBar(
                    title = { Text("Web Reader (Beta)") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isLoading || state.isLoading) padding else PaddingValues(0.dp))
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (!state.isLoading) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            // Inject Cookies for Authentication
                            state.authToken?.let { token ->
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)
                                
                                val uri = android.net.Uri.parse(url)
                                val domain = uri.host ?: ""
                                
                                // Set cookie for exact URL and domain
                                // Remove 'Secure' and 'HttpOnly' for better compatibility with different server types
                                // as some Storyteller instances run on custom ports/protocols.
                                val cookieString = "access_token=$token; Path=/; Domain=$domain"
                                cookieManager.setCookie(url, cookieString)
                                cookieManager.setCookie(domain, cookieString)
                                
                                // Common Storyteller variants
                                cookieManager.setCookie(url, "token=$token; Path=/")
                                cookieManager.flush()
                            }

                            webViewClient = object : WebViewClient() {
                                private fun injectAuth(view: WebView?) {
                                    state.authToken?.let { token ->
                                        view?.evaluateJavascript(
                                            """
                                            (function() {
                                                const inject = () => {
                                                    console.log('PagePortal: Syncing auth session...');
                                                    localStorage.setItem('access_token', '$token');
                                                    localStorage.setItem('token', '$token');
                                                    localStorage.setItem('st_token', '$token');
                                                };
                                                inject();
                                                // Retry for 3 seconds to ensure persistence during SPA boot
                                                const interval = setInterval(inject, 500);
                                                setTimeout(() => clearInterval(interval), 3000);
                                            })();
                                            """.trimIndent(),
                                            null
                                        )
                                    }
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    // Inject as early as possible
                                    injectAuth(view)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    injectAuth(view)
                                    
                                    // Auto-Recovery: If we are on a login page but have a token, 
                                    // try to redirect back to the original URL
                                    url?.let { currentUrl ->
                                        if (currentUrl.contains("/login") || currentUrl.contains("/auth")) {
                                            state.authToken?.let {
                                                android.util.Log.d("WebReader", "Detected login redirect, attempting auto-bounce back to $currentUrl")
                                                view?.loadUrl(currentUrl) 
                                            }
                                        }
                                    }
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: android.webkit.SslErrorHandler?,
                                    error: android.net.http.SslError?
                                ) {
                                    android.util.Log.e("WebReader", "SSL Error: ${error?.primaryError}. URL: ${error?.url}")
                                    // In debug builds or for specific user certs, we might want to proceed
                                    // But for now, just log to diagnose.
                                    handler?.cancel() 
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                    error: android.webkit.WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        android.util.Log.e("WebReader", "Net Error: ${error?.errorCode} - ${error?.description}. URL: ${request.url}")
                                    }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?
                                ): Boolean {
                                    return false 
                                }
                            }
                            
                            // Enable Remote Debugging via chrome://inspect
                            WebView.setWebContentsDebuggingEnabled(true)
                            
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                    android.util.Log.d("WebReaderConsole", "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()}")
                                    return true
                                }
                            }

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                // Support for blobs and cross-origin resource access inside the reader
                                allowFileAccessFromFileURLs = true
                                allowUniversalAccessFromFileURLs = true
                                
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                mediaPlaybackRequiresUserGesture = false
                                cacheMode = WebSettings.LOAD_DEFAULT

                                // HARDENING: Modern User Agent to bypass SPA blockages
                                val version = android.os.Build.VERSION.RELEASE
                                userAgentString = "Mozilla/5.0 (Linux; Android $version; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36 PagePortal/0.1"
                                
                                // HARDENING: Support for larger SPAs
                                setSupportZoom(true)
                                displayZoomControls = false
                            }

                            // HARDENING: Layer type adjustment for potential white screen fixes
                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                            loadUrl(url)
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Enhanced Loading State
            if (isLoading || state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )
                        
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp * scale),
                            strokeWidth = 4.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Syncing with Server...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            // Diagnostics HUD (visible when loading or after long loading)
            var showDiagnostics by remember { mutableStateOf(false) }
            val alpha by animateFloatAsState(if (showDiagnostics || isLoading) 1f else 0f, label = "diagAlpha")
            
            if (alpha > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Button(
                        onClick = { 
                            webView?.clearCache(true)
                            webView?.reload() 
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f * alpha),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                        ),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("Hard Reload")
                    }
                }
            }

            // Top Bar / Status
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp)
            ) {
                // Exit Button (Always visible when not loading)
                if (!isLoading) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.3f), MaterialTheme.shapes.small)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Exit",
                            tint = Color.White
                        )
                    }
                }

                if (isLoading) {
                    Text(
                        "URL: ${webView?.url ?: "Initializing..."}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

