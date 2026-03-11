package com.owlsoda.pageportal.features.auth

import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationServiceConfiguration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OidcHelper {
    suspend fun fetchConfiguration(issuerUri: Uri): AuthorizationServiceConfiguration {
        return suspendCancellableCoroutine { continuation ->
            AuthorizationServiceConfiguration.fetchFromIssuer(issuerUri) { config, ex ->
                if (ex != null) {
                    continuation.resumeWithException(ex)
                } else if (config != null) {
                    continuation.resume(config)
                } else {
                    continuation.resumeWithException(Exception("Failed to fetch OIDC configuration"))
                }
            }
        }
    }
}
