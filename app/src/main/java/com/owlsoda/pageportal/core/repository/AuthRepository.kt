package com.owlsoda.pageportal.core.repository

import com.owlsoda.pageportal.core.database.dao.ServerDao
import com.owlsoda.pageportal.core.database.entity.ServerEntity
import com.owlsoda.pageportal.services.ServiceManager
import com.owlsoda.pageportal.services.ServiceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for handling authentication and session management.
 */
@Singleton
class AuthRepository @Inject constructor(
    val serviceManager: ServiceManager,
    private val serverDao: ServerDao
) {
    /**
     * Get all connected servers.
     */
    fun getServers(): Flow<List<ServerEntity>> = serverDao.getAllServers()
    
    /**
     * Get active servers.
     */
    fun getActiveServers(): Flow<List<ServerEntity>> = serverDao.getActiveServers()
    
    /**
     * Check if user has any connected servers.
     */
    suspend fun hasConnectedServers(): Boolean {
        return serverDao.getActiveServerCount() > 0
    }
    
    /**
     * Login to a server.
     */
    suspend fun login(
        serviceType: ServiceType,
        serverUrl: String,
        username: String,
        password: String
    ): Result<ServerEntity> {
        return serviceManager.addServer(
            serviceType = serviceType,
            serverUrl = serverUrl,
            username = username,
            password = password
        )
    }

    /**
     * Login using a pre-obtained token.
     */
    suspend fun loginWithToken(
        serviceType: ServiceType,
        serverUrl: String,
        token: String,
        username: String,
        userId: String? = null
    ): Result<ServerEntity> {
        return serviceManager.addServerWithToken(
            serviceType = serviceType,
            serverUrl = serverUrl,
            token = token,
            username = username,
            userId = userId
        )
    }

    /**
     * Check if Audiobookshelf server has SSO/OIDC enabled.
     */
    suspend fun checkAbsSso(serverUrl: String): Boolean {
        return serviceManager.checkAbsSsoEnabled(serverUrl)
    }
    
    /**
     * Logout from a specific server.
     */
    suspend fun logout(serverId: Long) {
        serviceManager.removeServer(serverId)
    }
    
    /**
     * Logout from all servers.
     */
    suspend fun logoutAll() {
        val servers = serverDao.getAllServers().first()
        servers.forEach { server ->
            serviceManager.removeServer(server.id)
        }
    }
    
    /**
     * Get a specific server by ID.
     */
    suspend fun getServer(serverId: Long): ServerEntity? {
        return serverDao.getServerById(serverId)
    }
}
