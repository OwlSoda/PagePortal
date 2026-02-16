package com.owlsoda.pageportal.data.repository

import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.dao.ProgressDao
import com.owlsoda.pageportal.core.database.entity.ProgressEntity
import com.owlsoda.pageportal.services.ServiceManager
import com.owlsoda.pageportal.services.ReadingProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val progressDao: ProgressDao,
    private val bookDao: BookDao,
    private val serviceManager: ServiceManager
) {
    
    /**
     * Sync progress for a specific book.
     * Strategy: Latest timestamp wins.
     */
    suspend fun syncProgress(bookId: Long) {
        withContext(Dispatchers.IO) {
            try {
                val book = bookDao.getBookById(bookId) ?: return@withContext
                val service = serviceManager.getService(book.serverId.toLong()) ?: return@withContext
                
                // 1. Get Local Progress
                val localProgress = progressDao.getProgressByBookId(bookId)
                
                // 2. Get Remote Progress
                val remoteProgress = service.getProgress(book.serviceBookId) ?: return@withContext
                
                // 3. Compare Timestamps
                val localTime = localProgress?.lastUpdated ?: 0L
                val remoteTime = remoteProgress.lastUpdated
                
                // If remote is significantly newer (e.g., > 2 seconds difference to avoid clock skew issues)
                // Or if we have no local progress
                if (remoteTime > localTime + 2000 || localProgress == null) {
                    android.util.Log.d("SyncRepository", "Remote progress is newer. Updating local DB.")
                    
                    val newEntity = ProgressEntity(
                        id = localProgress?.id ?: 0,
                        bookId = bookId,
                        currentPosition = remoteProgress.currentPosition,
                        currentChapter = remoteProgress.currentChapter,
                        percentComplete = remoteProgress.percentComplete,
                        isFinished = remoteProgress.isFinished,
                        lastUpdated = remoteTime,
                        syncedAt = System.currentTimeMillis() // Mark as in-sync
                    )
                    
                    progressDao.insertProgress(newEntity)
                    return@withContext
                }
                
                // If local is significantly newer, push to server
                if (localTime > remoteTime + 2000) {
                    android.util.Log.d("SyncRepository", "Local progress is newer. Pushing to server.")
                    pushProgress(bookId)
                    return@withContext
                }
                
                // If timestamps are close, assume in sync
                android.util.Log.d("SyncRepository", "Progress already in sync.")
            } catch (e: Exception) {
                android.util.Log.e("SyncRepository", "Sync failed for book $bookId: ${e.message}")
            }
        }
    }
    
    /**
     * Push local progress to remote server.
     */
    suspend fun pushProgress(bookId: Long) {
        withContext(Dispatchers.IO) {
            try {
                val book = bookDao.getBookById(bookId) ?: return@withContext
                val service = serviceManager.getService(book.serverId.toLong()) ?: return@withContext
                val localProgress = progressDao.getProgressByBookId(bookId) ?: return@withContext
                
                val update = ReadingProgress(
                    bookId = book.serviceBookId,
                    currentPosition = localProgress.currentPosition,
                    currentChapter = localProgress.currentChapter,
                    percentComplete = localProgress.percentComplete,
                    isFinished = localProgress.isFinished,
                    lastUpdated = localProgress.lastUpdated
                )
                
                service.updateProgress(book.serviceBookId, update)
                progressDao.markSynced(bookId, System.currentTimeMillis())
                android.util.Log.d("SyncRepository", "Successfully pushed progress for book $bookId")
            } catch (e: Exception) {
                android.util.Log.e("SyncRepository", "Push failed for book $bookId: ${e.message}")
            }
        }
    }
    
    /**
     * Push all unsynced progress to remote servers.
     * Useful for batch sync or when coming online.
     */
    suspend fun pushAllUnsynced() {
        withContext(Dispatchers.IO) {
            val unsynced = progressDao.getUnsyncedProgress() // I verified this DAO method exists (or similar)
            // Wait, I saw getUnsyncedProgress defined in the DAO view?
            // "SELECT * FROM progress WHERE syncedAt IS NULL OR lastUpdated > syncedAt"
            // Yes, it exists.
            
            unsynced.forEach { progress ->
                pushProgress(progress.bookId)
            }
        }
    }
}
