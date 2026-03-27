package com.owlsoda.pageportal.core.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides encrypted storage for authentication tokens using EncryptedSharedPreferences.
 * Tokens are encrypted at rest using AES-256-GCM with a key protected by Android Keystore.
 *
 * The Room database stores a placeholder ("ENCRYPTED") for the authToken field,
 * while the actual token is stored here keyed by the server ID.
 */
@Singleton
class SecureTokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SecureTokenStore"
        private const val PREFS_FILENAME = "secure_tokens"
        private const val KEY_PREFIX = "server_token_"
        
        /**
         * Sentinel value stored in Room's authToken field to indicate the real token
         * is in the encrypted store. Allows backward compatibility detection.
         */
        const val ENCRYPTED_PLACEHOLDER = "ENCRYPTED"
    }

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILENAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback: if crypto initialization fails (rare, e.g. wiped Keystore),
            // use regular SharedPreferences rather than completely breaking auth.
            Log.e(TAG, "Failed to create EncryptedSharedPreferences, falling back to regular prefs", e)
            context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Store a token for the given server ID.
     */
    fun storeToken(serverId: Long, token: String) {
        prefs.edit().putString("$KEY_PREFIX$serverId", token).apply()
    }

    /**
     * Retrieve the token for the given server ID.
     * Returns null if no token is stored.
     */
    fun getToken(serverId: Long): String? {
        return prefs.getString("$KEY_PREFIX$serverId", null)
    }

    /**
     * Remove the token for the given server ID.
     */
    fun removeToken(serverId: Long) {
        prefs.edit().remove("$KEY_PREFIX$serverId").apply()
    }

    /**
     * Check if a token exists for the given server ID.
     */
    fun hasToken(serverId: Long): Boolean {
        return prefs.contains("$KEY_PREFIX$serverId")
    }
}
