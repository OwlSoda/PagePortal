package com.owlsoda.pageportal.core.database.dao

import androidx.room.*
import com.owlsoda.pageportal.core.database.entity.ServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY createdAt DESC")
    fun getAllServers(): Flow<List<ServerEntity>>
    
    @Query("SELECT * FROM servers WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getActiveServers(): Flow<List<ServerEntity>>
    
    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getServerById(id: Long): ServerEntity?
    
    @Query("SELECT * FROM servers WHERE serverUrl = :url AND serviceType = :serviceType LIMIT 1")
    suspend fun getServerByUrlAndType(url: String, serviceType: String): ServerEntity?
    
    @Query("SELECT * FROM servers WHERE serviceType = :serviceType LIMIT 1")
    suspend fun getServerByServiceType(serviceType: String): ServerEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerEntity): Long
    
    @Update
    suspend fun updateServer(server: ServerEntity)
    
    @Query("UPDATE servers SET authToken = :token, lastSyncAt = :syncTime WHERE id = :serverId")
    suspend fun updateToken(serverId: Long, token: String?, syncTime: Long = System.currentTimeMillis())
    
    @Query("UPDATE servers SET isActive = :isActive WHERE id = :serverId")
    suspend fun setServerActive(serverId: Long, isActive: Boolean)
    
    @Delete
    suspend fun deleteServer(server: ServerEntity)
    
    @Query("DELETE FROM servers WHERE id = :serverId")
    suspend fun deleteServerById(serverId: Long)
    
    @Query("SELECT COUNT(*) FROM servers WHERE isActive = 1")
    suspend fun getActiveServerCount(): Int
}
