package com.owlsoda.pageportal.core.database.dao

import androidx.room.*
import com.owlsoda.pageportal.core.database.entity.CollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections WHERE serverId = :serverId ORDER BY name ASC")
    fun getCollectionsByServer(serverId: Long): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE serverId = :serverId ORDER BY name ASC")
    suspend fun getCollectionsList(serverId: Long): List<CollectionEntity>

    @Query("SELECT * FROM collections ORDER BY name ASC")
    fun getAllCollections(): Flow<List<CollectionEntity>>
    
    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getCollectionById(id: Long): CollectionEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity)
    
    @Query("DELETE FROM collections WHERE serverId = :serverId")
    suspend fun deleteCollectionsByServer(serverId: Long)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollections(collections: List<CollectionEntity>)
}
