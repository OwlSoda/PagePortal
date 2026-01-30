package com.owlsoda.pageportal.network

import com.owlsoda.pageportal.core.database.dao.ServerDao
import com.owlsoda.pageportal.services.ServiceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor that automatically adds Authorization headers to requests
 * based on the target URL matching a configured server.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val serverDao: ServerDao
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url.toString()
        
        // Log request for debugging
        android.util.Log.d("AuthInterceptor", "Intercepting: $url")

        // Skip if already has Authorization header
        if (originalRequest.header("Authorization") != null) {
            android.util.Log.d("AuthInterceptor", "Skipping: Auth header already present")
            return chain.proceed(originalRequest)
        }

        // Find a matching server for this URL
        val requestHttpUrl = originalRequest.url
        val server = runBlocking {
            serverDao.getActiveServers().first().find { server ->
                val serverHttpUrl = try {
                    server.serverUrl.toHttpUrl()
                } catch (e: Exception) {
                    null
                } ?: return@find false
                
                val hostMatches = serverHttpUrl.host == requestHttpUrl.host
                val portMatches = serverHttpUrl.port == requestHttpUrl.port
                
                val serverPath = serverHttpUrl.encodedPath.trimEnd('/')
                val requestPath = requestHttpUrl.encodedPath.trimEnd('/')
                val pathMatches = requestPath.startsWith(serverPath)

                android.util.Log.d("AuthInterceptor", "Checking match: Request(${requestHttpUrl.host}:${requestHttpUrl.port}${requestHttpUrl.encodedPath}) vs Server(${serverHttpUrl.host}:${serverHttpUrl.port}${serverHttpUrl.encodedPath}) -> Host:$hostMatches, Port:$portMatches, Path:$pathMatches")

                hostMatches && portMatches && pathMatches
            }
        }

        if (server == null) {
            android.util.Log.d("AuthInterceptor", "No matching server found for: $url")
            return chain.proceed(originalRequest)
        }

        val token = server.authToken
        if (token == null) {
            android.util.Log.d("AuthInterceptor", "Server found but no token: ${server.serverUrl}")
            return chain.proceed(originalRequest)
        }

        android.util.Log.d("AuthInterceptor", "Injecting auth for ${server.serviceType} (${server.serverUrl})")

        val newRequest = originalRequest.newBuilder().apply {
            when (server.serviceType) {
                ServiceType.STORYTELLER.name,
                ServiceType.AUDIOBOOKSHELF.name -> {
                    addHeader("Authorization", "Bearer $token")
                }
                ServiceType.BOOKLORE.name -> {
                    // Booklore (OPDS) uses pre-formatted Basic auth or the token itself
                    addHeader("Authorization", token)
                }
            }
        }.build()

        return chain.proceed(newRequest)
    }
}
