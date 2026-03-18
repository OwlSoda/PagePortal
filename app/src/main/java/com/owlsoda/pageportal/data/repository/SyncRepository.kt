package com.owlsoda.pageportal.data.repository

import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.dao.ProgressDao
import com.owlsoda.pageportal.core.database.entity.ProgressEntity
import com.owlsoda.pageportal.services.ServiceManager
import com.owlsoda.pageportal.services.ReadingProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _isSyncing = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val isSyncing: StateFlow<Map<Long, Boolean>> = _isSyncing.asStateFlow()

    /**
     * Sync progress for a specific book.
     * Strategy: Latest timestamp wins.
     */
    suspend fun syncProgress(bookId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                _isSyncing.value = _isSyncing.value + (bookId to true)
                val book = bookDao.getBookById(bookId) ?: return@withContext Result.failure(Exception("Book not found"))
                val service = serviceManager.getService(book.serverId) ?: return@withContext Result.failure(Exception("Service not found"))
                
                // 1. Get Local Progress
                val localProgress = progressDao.getProgressByBookId(bookId)
                
                // 2. Get Remote Progress
                val remoteProgress = try {
                    service.getProgress(book.serviceBookId)
                } catch (e: Exception) {
                    android.util.Log.e("SyncRepository", "Failed to fetch remote progress for ${book.title}: ${e.message}")
                    null
                }
                
                if (remoteProgress == null) {
                    // If remote failed or empty, try pushing local if it exists
                    if (localProgress != null && (localProgress.syncedAt == null || localProgress.lastUpdated > localProgress.syncedAt!!)) {
                        pushProgress(bookId)
                    }
                    return@withContext Result.success(Unit)
                }
                
                // 3. Compare Timestamps
                val localTime = localProgress?.lastUpdated ?: 0L
                val remoteTime = remoteProgress.lastUpdated
                
                // If remote is significantly newer (e.g., > 2 seconds difference to avoid clock skew issues)
                // Or if we have no local progress
                if (remoteTime > localTime + 2000 || localProgress == null) {
                    android.util.Log.d("SyncRepository", "Remote progress for '${book.title}' is newer ($remoteTime vs $localTime). Updating local DB.")
                    
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
                    return@withContext Result.success(Unit)
                }
                
                // If local is significantly newer, push to server
                if (localTime > remoteTime + 2000) {
                    android.util.Log.d("SyncRepository", "Local progress for '${book.title}' is newer ($localTime vs $remoteTime). Pushing to server.")
                    pushProgress(bookId)
                    return@withContext Result.success(Unit)
                }
                
                // If timestamps are close, mark as synced locally if it wasn't already
                android.util.Log.d("SyncRepository", "Progress for '${book.title}' already in sync.")
                if (localProgress != null && (localProgress.syncedAt == null || localProgress.syncedAt!! < localProgress.lastUpdated)) {
                    progressDao.markSynced(bookId)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                android.util.Log.e("SyncRepository", "Sync failed for book $bookId: ${e.message}")
                Result.failure(e)
            } finally {
                _isSyncing.value = _isSyncing.value - bookId
            }
        }
    }
    
    /**
     * Push local progress to remote server.
     */
    suspend fun pushProgress(bookId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val book = bookDao.getBookById(bookId) ?: return@withContext Result.failure(Exception("Book not found"))
                val service = serviceManager.getService(book.serverId) ?: return@withContext Result.failure(Exception("Service not found"))
                val localProgress = progressDao.getProgressByBookId(bookId) ?: return@withContext Result.failure(Exception("No local progress to push"))
                
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
                android.util.Log.d("SyncRepository", "Successfully pushed progress for book '${book.title}'")
                Result.success(Unit)
            } catch (e: Exception) {
                android.util.Log.e("SyncRepository", "Push failed for book $bookId: ${e.message}")
                Result.failure(e)
            }
        }
    }
    
    /**
     * Push/Pull all unsynced progress to remote servers.
     */
    suspend fun syncAll(): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val unsynced = progressDao.getUnsyncedProgress()
                var count = 0
                for (progress in unsynced) {
                    val res = syncProgress(progress.bookId)
                    if (res.isSuccess) count++
                }
                Result.success(count)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun pushAllUnsynced() = syncAll()
}
