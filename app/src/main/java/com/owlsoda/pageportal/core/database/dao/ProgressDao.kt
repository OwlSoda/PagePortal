package com.owlsoda.pageportal.core.database.dao

import androidx.room.*
import com.owlsoda.pageportal.core.database.entity.ProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    suspend fun getProgressByBookId(bookId: Long): ProgressEntity?
    
    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    fun observeProgressByBookId(bookId: Long): Flow<ProgressEntity?>
    
    @Query("SELECT * FROM progress WHERE percentComplete > 0 AND isFinished = 0 ORDER BY lastUpdated DESC")
    fun getInProgressBooks(): Flow<List<ProgressEntity>>
    
    @Query("SELECT * FROM progress WHERE isFinished = 1 ORDER BY lastUpdated DESC")
    fun getFinishedBooks(): Flow<List<ProgressEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: ProgressEntity): Long
    
    @Update
    suspend fun updateProgress(progress: ProgressEntity)
    
    @Query("""
        UPDATE progress 
        SET currentPosition = :position, 
            currentChapter = :chapter, 
            percentComplete = :percent, 
            lastUpdated = :timestamp
        WHERE bookId = :bookId
    """)
    suspend fun updatePosition(
        bookId: Long, 
        position: Long, 
        chapter: Int, 
        percent: Float, 
        timestamp: Long = System.currentTimeMillis()
    )
    
    @Query("UPDATE progress SET isFinished = :finished WHERE bookId = :bookId")
    suspend fun setFinished(bookId: Long, finished: Boolean)
    
    @Query("UPDATE progress SET syncedAt = :syncTime WHERE bookId = :bookId")
    suspend fun markSynced(bookId: Long, syncTime: Long = System.currentTimeMillis())
    
    @Query("SELECT * FROM progress WHERE syncedAt IS NULL OR lastUpdated > syncedAt")
    suspend fun getUnsyncedProgress(): List<ProgressEntity>
    
    @Query("SELECT * FROM progress ORDER BY lastUpdated DESC")
    suspend fun getAllProgress(): List<ProgressEntity>
    
    @Delete
    suspend fun deleteProgress(progress: ProgressEntity)
    
    @Query("DELETE FROM progress WHERE bookId = :bookId")
    suspend fun deleteProgressByBookId(bookId: Long)
}
