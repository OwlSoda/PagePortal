package com.owlsoda.pageportal.features.auth

import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationServiceConfiguration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OidcHelper {
    suspend fun fetchConfiguration(issuerUri: Uri): AuthorizationServiceConfiguration {
        return suspendCancellableCoroutine { continuation ->
            try {
                AuthorizationServiceConfiguration.fetchFromIssuer(issuerUri) { config, ex ->
                    if (!continuation.isActive) return@fetchFromIssuer
                    if (ex != null) {
                        continuation.resumeWithException(
                            Exception("OIDC discovery error at $issuerUri: ${ex.errorDescription ?: ex.message}", ex)
                        )
                    } else if (config != null) {
                        continuation.resume(config)
                    } else {
                        continuation.resumeWithException(
                            Exception("OIDC discovery returned empty configuration for $issuerUri")
                        )
                    }
                }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        Exception("Failed to initiate OIDC discovery for $issuerUri: ${e.message}", e)
                    )
                }
            }
        }
    }
}
