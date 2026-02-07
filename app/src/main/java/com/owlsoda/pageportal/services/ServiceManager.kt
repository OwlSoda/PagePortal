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
    private val okHttpClient: OkHttpClient
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
                // Create service instance
                val service = createService(serviceType, serverUrl)
                
                // Authenticate
                val authResult = service.authenticate(serverUrl, username, password)
                
                if (!authResult.success) {
                    return@withContext Result.failure(Exception(authResult.errorMessage ?: "Authentication failed"))
                }
                
                // Check if server already exists
                val existing = serverDao.getServerByUrlAndType(serverUrl, serviceType.name)
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
                    serverUrl = serverUrl,
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
}
