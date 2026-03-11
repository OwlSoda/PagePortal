package com.owlsoda.pageportal.services

import com.owlsoda.pageportal.core.database.dao.ServerDao
import com.owlsoda.pageportal.core.database.entity.ServerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages all active service connections and coordinates multi-server operations.
 */
@Singleton
class ServiceManager @Inject constructor(
    private val serverDao: ServerDao,
    internal val okHttpClient: OkHttpClient
) {
    private val services = ConcurrentHashMap<Long, BookService>()
    
    /**
     * Add a new server and authenticate.
     */
    suspend fun addServer(
        serviceType: ServiceType,
        serverUrl: String,
        username: String,
        password: String
    ): Result<ServerEntity> {
        return try {
            withContext(Dispatchers.IO) {
                val normalizedUrl = normalizeUrl(serverUrl)
                // Create service instance
                val service = createService(serviceType, normalizedUrl)
                
                // Authenticate
                val authResult = service.authenticate(normalizedUrl, username, password)
                
                if (!authResult.success) {
                    return@withContext Result.failure(Exception(authResult.errorMessage ?: "Authentication failed"))
                }
                
                // Check if server already exists
                val existing = serverDao.getServerByUrlAndType(normalizedUrl, serviceType.name)
                if (existing != null) {
                    // Update existing server
                    val updated = existing.copy(
                        authToken = authResult.token,
                        userId = authResult.userId,
                        lastSyncAt = System.currentTimeMillis()
                    )
                    serverDao.updateServer(updated)
                    services[updated.id] = service
                    return@withContext Result.success(updated)
                }
                
                // Create new server entry
                val server = ServerEntity(
                    serviceType = serviceType.name,
                    serverUrl = normalizedUrl,
                    username = username,
                    authToken = authResult.token,
                    userId = authResult.userId,
                    displayName = "${serviceType.name} - $username"
                )
                
                val serverId = serverDao.insertServer(server)
                val savedServer = server.copy(id = serverId)
                
                // Cache service
                services[serverId] = service
                
                Result.success(savedServer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Authentication failed: ${e.message ?: "Unknown error"}"))
        }
    }

    /**
     * Add a server using a pre-obtained token (OAuth/OIDC).
     */
    suspend fun addServerWithToken(
        serviceType: ServiceType,
        serverUrl: String,
        token: String,
        username: String,
        userId: String? = null
    ): Result<ServerEntity> {
        return try {
            withContext(Dispatchers.IO) {
                val normalizedUrl = normalizeUrl(serverUrl)
                // Check if server already exists
                val existing = serverDao.getServerByUrlAndType(normalizedUrl, serviceType.name)
                if (existing != null) {
                    val updated = existing.copy(
                        authToken = token,
                        userId = userId ?: existing.userId,
                        username = username,
                        lastSyncAt = System.currentTimeMillis()
                    )
                    serverDao.updateServer(updated)
                    // Invalidate service cache
                    services.remove(updated.id)
                    return@withContext Result.success(updated)
                }

                // Create new server entry
                val server = ServerEntity(
                    serviceType = serviceType.name,
                    serverUrl = normalizedUrl,
                    username = username,
                    authToken = token,
                    userId = userId,
                    displayName = "${serviceType.name} - $username"
                )

                val serverId = serverDao.insertServer(server)
                val savedServer = server.copy(id = serverId)

                Result.success(savedServer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Failed to add server: ${e.message}"))
        }
    }
    
    /**
     * Get or create a service for a server.
     */
    suspend fun getService(serverId: Long): BookService? {
        // Return cached service if available
        services[serverId]?.let { return it }
        
        // Load server and create service
        val server = serverDao.getServerById(serverId) ?: return null
        val service = createService(server.toServiceType(), server.serverUrl)
        
        // Configure service with auth token
        when (service) {
            is com.owlsoda.pageportal.services.audiobookshelf.AudiobookshelfService -> {
                server.authToken?.let { service.setAuthToken(it) }
            }
            is com.owlsoda.pageportal.services.booklore.BookloreService -> {
                server.authToken?.let { token ->
                    service.configure(server.serverUrl, token)
                }
            }
            is com.owlsoda.pageportal.services.storyteller.StorytellerService -> {
                server.authToken?.let { token ->
                    service.configure(server.serverUrl, token)
                }
            }
        }
        
        services[serverId] = service
        return service
    }

    suspend fun getServiceEntity(serverId: Long): ServerEntity? {
        return serverDao.getServerById(serverId)
    }
    
    /**
     * Get all books from all active servers.
     * Fetches all pages for each server.
     */
    suspend fun getAllBooks(): List<Pair<ServerEntity, List<ServiceBook>>> {
        val activeServers = serverDao.getActiveServers().first()
        return activeServers.mapNotNull { server ->
            val service = getService(server.id) ?: return@mapNotNull null
            try {
                val allServiceBooks = mutableListOf<ServiceBook>()
                var currentPage = 0
                val pageSize = 50
                
                while (true) {
                    val pageBooks = service.listBooks(page = currentPage, pageSize = pageSize)
                    if (pageBooks.isEmpty()) break
                    
                    allServiceBooks.addAll(pageBooks)
                    
                    // If we got fewer books than requested, it's likely the last page
                    if (pageBooks.size < pageSize) break
                    
                    currentPage++
                    
                    // Safety cap to prevent infinite loops (e.g. 10k books max per server for now)
                    if (currentPage > 200) break 
                }
                
                server to allServiceBooks
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Remove a server.
     */
    suspend fun removeServer(serverId: Long) {
        services.remove(serverId)
        serverDao.deleteServerById(serverId)
    }
    
    data class ServerStatus(
        val serverId: Long,
        val isHealthy: Boolean,
        val lastSyncTime: Long?,
        val errorMessage: String?
    )

    suspend fun checkServerHealth(serverId: Long): ServerStatus {
        return try {
            val service = getService(serverId)
            if (service == null) {
                return ServerStatus(serverId, false, null, "Service not initialized")
            }
            
            // Try to list books as health check (minimal fetch)
            withContext(Dispatchers.IO) {
                service.listBooks(page = 0, pageSize = 1)
            }
            
            ServerStatus(serverId, true, System.currentTimeMillis(), null)
        } catch (e: Exception) {
            ServerStatus(serverId, false, null, e.message)
        }
    }

    suspend fun checkAbsSsoEnabled(serverUrl: String): Boolean {
        return try {
            val service = com.owlsoda.pageportal.services.audiobookshelf.AudiobookshelfService(serverUrl, okHttpClient)
            service.checkSsoEnabled()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Create a service instance for the given type.
     */
    private fun createService(serviceType: ServiceType, serverUrl: String): BookService {
        return when (serviceType) {
            ServiceType.STORYTELLER -> com.owlsoda.pageportal.services.storyteller.StorytellerService(okHttpClient)
            ServiceType.AUDIOBOOKSHELF -> com.owlsoda.pageportal.services.audiobookshelf.AudiobookshelfService(serverUrl, okHttpClient)
            ServiceType.BOOKLORE -> com.owlsoda.pageportal.services.booklore.BookloreService(okHttpClient)
            ServiceType.LOCAL -> com.owlsoda.pageportal.services.local.LocalService()
        }
    }

    companion object {
        /**
         * Normalizes a server URL by adding protocols if missing and removing trailing slashes.
         * Handles common errors like "http:host" and selects http vs https based on port.
         */
        fun normalizeUrl(url: String): String {
            if (url.isBlank()) return ""
            
            var processed = url.trim()
            val original = processed
            
            // Fix common typos like "http:host" -> "http://host"
            if (processed.startsWith("http:") && !processed.startsWith("http://")) {
                processed = "http://" + processed.removePrefix("http:")
            } else if (processed.startsWith("https:") && !processed.startsWith("https://")) {
                processed = "https://" + processed.removePrefix("https:")
            } else if (processed.contains("http:") && !processed.contains("http://")) {
                processed = processed.replace("http:", "http://")
            }
            
            if (original != processed) {
                android.util.Log.d("ServiceManager", "Fixed URL typo: '$original' -> '$processed'")
            }

            val withProtocol = if (!processed.startsWith("http")) {
                val isPrivate = processed.startsWith("localhost") ||
                    processed.startsWith("127.0.0.1") ||
                    processed.startsWith("192.168.") ||
                    processed.startsWith("10.") ||
                    processed.contains(".local")
                
                // If it has a port, check if it's common non-SSL ports
                val hasPort = processed.contains(":")
                val isLikelyHttp = if (hasPort) {
                    val port = processed.substringAfterLast(":").toIntOrNull()
                    port != null && (port == 80 || port == 8080 || port == 6060 || port == 32400)
                } else false

                if (isPrivate || isLikelyHttp) {
                    "http://$processed"
                } else {
                    "https://$processed"
                }
            } else processed
            
            return if (withProtocol.endsWith("/")) withProtocol.dropLast(1) else withProtocol
        }
    }
}
