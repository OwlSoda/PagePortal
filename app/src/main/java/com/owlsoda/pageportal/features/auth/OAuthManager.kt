package com.owlsoda.pageportal.features.auth

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton to coordinate OAuth redirects between MainActivity and ViewModels.
 */
@Singleton
class OAuthManager @Inject constructor() {
    private val _redirectUri = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val redirectUri = _redirectUri.asSharedFlow()

    fun handleRedirect(uri: Uri) {
        _redirectUri.tryEmit(uri)
    }
}
