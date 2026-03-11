package com.owlsoda.pageportal.features.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Helper for PKCE (Proof Key for Code Exchange) generation.
 * Follows RFC 7636.
 */
object PkceHelper {
    /**
     * Generates a random high-entropy string as the code verifier.
     */
    fun generateVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Generates the code challenge derived from the verifier.
     */
    fun generateChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Generates a random state string for CSRF protection.
     */
    fun generateState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
