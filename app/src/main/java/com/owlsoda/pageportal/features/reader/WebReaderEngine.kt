package com.owlsoda.pageportal.features.reader

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A "Headless" version of the Web Reader that runs in the background.
 * It provides a bridge between native app controls and the Storyteller web app.
 */
@Composable
fun WebReaderEngine(
    url: String,
    authToken: String?,
    isPlaying: Boolean,
    onPlaybackStatusChanged: (Boolean) -> Unit,
    onProgressUpdate: (current: Double, total: Double) -> Unit,
    onHighlightUpdate: (String) -> Unit,
    onLog: (String) -> Unit = {}
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Sync playback state to WebView
    LaunchedEffect(isPlaying) {
        val action = if (isPlaying) "play" else "pause"
        webViewRef?.evaluateJavascript(
            "if (window.PagePortalBridge) window.PagePortalBridge.$action();", 
            null
        )
    }

    AndroidView(
        modifier = Modifier.size(1.dp), // Minimal size to ensure it keeps running
        factory = { ctx ->
            WebView(ctx).apply {
                // Background settings
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                
                // Auth Injection
                authToken?.let { token ->
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    val uri = android.net.Uri.parse(url)
                    val domain = uri.host ?: ""
                    cookieManager.setCookie(url, "token=$token; Path=/; Domain=$domain")
                    cookieManager.setCookie(url, "access_token=$token; Path=/")
                    cookieManager.flush()
                }

                // JS Bridge for Web -> Native
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onTimeUpdate(current: Double, total: Double) {
                        onProgressUpdate(current, total)
                    }

                    @android.webkit.JavascriptInterface
                    fun onPlaybackState(playing: Boolean) {
                        onPlaybackStatusChanged(playing)
                    }

                    @android.webkit.JavascriptInterface
                    fun onHighlight(smilId: String) {
                        onHighlightUpdate(smilId)
                    }

                    @android.webkit.JavascriptInterface
                    fun log(msg: String) {
                        onLog(msg)
                    }
                }, "WebEngine")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        // Inject the Storyteller Control Bridge
                        injectStorytellerBridge(view, authToken)
                    }
                }

                loadUrl(url)
                webViewRef = this
            }
        },
        update = { webViewRef = it }
    )
}

/**
 * Inject the JS bridge that finds the Storyteller audio player or state
 * and wires it to our native "WebEngine" interface.
 */
private fun injectStorytellerBridge(view: WebView?, token: String?) {
    val script = """
        (function() {
            console.log('PagePortal Ghost Engine: Booting...');
            
            // 1. Force Auth Persistence
            if ('$token') {
                localStorage.setItem('token', '$token');
                localStorage.setItem('access_token', '$token');
            }

            // 2. Define the Bridge API for Native App -> WebView
            window.PagePortalBridge = {
                play: () -> {
                    const btn = document.querySelector('button[aria-label="Play"], button.play-button');
                    if (btn) btn.click();
                    else document.querySelector('audio')?.play();
                },
                pause: () -> {
                    const btn = document.querySelector('button[aria-label="Pause"], button.pause-button');
                    if (btn) btn.click();
                    else document.querySelector('audio')?.pause();
                },
                seek: (seconds) => {
                    const audio = document.querySelector('audio');
                    if (audio) audio.currentTime = seconds;
                }
            };

            // 3. Monitor WebView -> Native App
            let lastUpdate = 0;
            setInterval(() => {
                const audio = document.querySelector('audio');
                if (audio) {
                    const now = Date.now();
                    if (now - lastUpdate > 500) {
                        WebEngine.onTimeUpdate(audio.currentTime, audio.duration);
                        WebEngine.onPlaybackState(!audio.paused);
                        lastUpdate = now;
                    }
                }
                
                // Sync highlights if present
                const activeHighlight = document.querySelector('.smil-highlight, .current-sentence');
                if (activeHighlight && activeHighlight.id) {
                    WebEngine.onHighlight(activeHighlight.id);
                }
            }, 500);
            
            console.log('PagePortal Ghost Engine: Wired.');
        })();
    """.trimIndent()
    
    view?.evaluateJavascript(script, null)
}
