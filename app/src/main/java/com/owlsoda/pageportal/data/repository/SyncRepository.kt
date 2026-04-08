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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val progressDao: ProgressDao,
    private val bookDao: BookDao,
    private val serviceManager: ServiceManager,
    @ApplicationContext private val context: Context
) {
    private val _isSyncing = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val isSyncing: StateFlow<Map<Long, Boolean>> = _isSyncing.asStateFlow()

    // Per-book mutexes prevent concurrent syncs from racing each other.
    // Without this, two coroutines can both read "local > remote" and push in parallel.
    private val bookMutexes = java.util.concurrent.ConcurrentHashMap<Long, Mutex>()

    /**
     * Sync progress for a specific book.
     * Strategy: Latest timestamp wins.
     * Thread-safe: per-book mutex prevents concurrent syncs racing.
     */
    suspend fun syncProgress(bookId: Long): Result<Unit> {
        val mutex = bookMutexes.getOrPut(bookId) { Mutex() }
        return mutex.withLock {
            withContext(Dispatchers.IO) {
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
                        // If remote failed or empty, try pushing local if it exists and is dirty
                        if (localProgress != null && (localProgress.syncedAt == null || localProgress.lastUpdated > localProgress.syncedAt!!)) {
                            pushProgress(bookId)
                        }
                        return@withContext Result.success(Unit)
                    }
                    
                    // 3. Compare Timestamps
                    val localTime = localProgress?.lastUpdated ?: 0L
                    val remoteTime = remoteProgress.lastUpdated
                    
                    // Remote is significantly newer (> 2s) or we have no local → pull down
                    if (remoteTime > localTime + 2000 || localProgress == null) {
                        android.util.Log.d("SyncRepository", "[PULL] '${book.title}' remote=$remoteTime > local=$localTime")
                        logConflict(book.title, winner = "REMOTE", localTime = localTime, remoteTime = remoteTime)
                        
                        val newEntity = ProgressEntity(
                            id = localProgress?.id ?: 0,
                            bookId = bookId,
                            currentPosition = remoteProgress.currentPosition,
                            currentChapter = remoteProgress.currentChapter,
                            percentComplete = remoteProgress.percentComplete,
                            isFinished = remoteProgress.isFinished,
                            lastUpdated = remoteTime,
                            syncedAt = System.currentTimeMillis()
                        )
                        progressDao.insertProgress(newEntity)
                        return@withContext Result.success(Unit)
                    }
                    
                    // Local is significantly newer → push to server
                    if (localTime > remoteTime + 2000) {
                        android.util.Log.d("SyncRepository", "[PUSH] '${book.title}' local=$localTime > remote=$remoteTime")
                        logConflict(book.title, winner = "LOCAL", localTime = localTime, remoteTime = remoteTime)
                        pushProgress(bookId)
                        return@withContext Result.success(Unit)
                    }
                    
                    // Timestamps within 2s → in-sync, just mark if needed
                    android.util.Log.d("SyncRepository", "[IN-SYNC] '${book.title}' local=$localTime remote=$remoteTime")
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

    /**
     * Appends a one-line entry to sync_conflicts.log inside filesDir.
     * This log is shown in the Sync Diagnostics screen and helps
     * diagnose which device "wins" most often.
     */
    private fun logConflict(title: String, winner: String, localTime: Long, remoteTime: Long) {
        try {
            val logFile = java.io.File(context.filesDir, "sync_conflicts.log")
            val entry = "${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())} | $winner won | '$title' | local=$localTime remote=$remoteTime\n"
            logFile.appendText(entry)
            // Keep only last 200 lines to avoid unbounded growth
            val lines = logFile.readLines()
            if (lines.size > 200) logFile.writeText(lines.takeLast(200).joinToString("\n") + "\n")
        } catch (e: Exception) {
            android.util.Log.w("SyncRepository", "Failed to write conflict log: ${e.message}")
        }
    }
}
