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
     * Strategy: Hybrid Resolver
     * 1. If timestamps differ by > 5 minutes: Latest timestamp wins (User intention).
     * 2. If timestamps differ by < 5 minutes: Largest percentComplete wins (Protects against ghost updates & clock drift).
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
                    val local = progressDao.getProgressByBookId(bookId)
                    
                    // 2. Get Remote Progress
                    val remote = try {
                        service.getProgress(book.serviceBookId)
                    } catch (e: Exception) {
                        android.util.Log.e("SyncRepository", "Failed to fetch remote progress for ${book.title}: ${e.message}")
                        null
                    }
                    
                    if (remote == null) {
                        // If remote failed or empty, try pushing local if it exists and is dirty
                        if (local != null && (local.syncedAt == null || local.lastUpdated > local.syncedAt!!)) {
                            pushProgress(bookId)
                        }
                        return@withContext Result.success(Unit)
                    }
                    
                    // 3. Resolve Conflict
                    val localTime = local?.lastUpdated ?: 0L
                    val remoteTime = remote.lastUpdated
                    val localPercent = local?.percentComplete ?: 0f
                    val remotePercent = remote.percentComplete
                    
                    val timeDiff = Math.abs(localTime - remoteTime)
                    val confidenceWindow = 5 * 60 * 1000 // 5 minutes
                    
                    val shouldPull: Boolean
                    val reason: String

                    when {
                        // No local data yet
                        local == null -> {
                            shouldPull = true
                            reason = "INITIAL_PULL"
                        }
                        
                        // Scenario A: Timestamps are far apart -> Trust the timestamp
                        timeDiff > confidenceWindow -> {
                            shouldPull = remoteTime > localTime
                            reason = if (shouldPull) "REMOTE_NEWER_EXTENDED" else "LOCAL_NEWER_EXTENDED"
                        }
                        
                        // Scenario B: Timestamps are close (Potential clock drift or ghost update)
                        // -> Tie-breaker: Furthest progress wins
                        else -> {
                            // Only switch if the progress difference is meaningful (> 0.1%)
                            if (Math.abs(remotePercent - localPercent) > 0.1f) {
                                shouldPull = remotePercent > localPercent
                                reason = if (shouldPull) "REMOTE_FURTHER_TIEBREAK" else "LOCAL_FURTHER_TIEBREAK"
                            } else {
                                // Progress is basically the same, just mark as synced if needed
                                if (local.syncedAt == null || local.syncedAt!! < local.lastUpdated) {
                                    progressDao.markSynced(bookId)
                                }
                                return@withContext Result.success(Unit)
                            }
                        }
                    }

                    if (shouldPull) {
                        android.util.Log.d("SyncRepository", "[PULL] '${book.title}' reason=$reason (R:${remotePercent}% vs L:${localPercent}%)")
                        logConflict(book.title, winner = "REMOTE", localPercent = localPercent, remotePercent = remotePercent, reason = reason)
                        
                        val newEntity = ProgressEntity(
                            id = local?.id ?: 0,
                            bookId = bookId,
                            currentPosition = remote.currentPosition,
                            currentChapter = remote.currentChapter,
                            percentComplete = remote.percentComplete,
                            isFinished = remote.isFinished,
                            lastUpdated = remoteTime,
                            syncedAt = System.currentTimeMillis()
                        )
                        progressDao.insertProgress(newEntity)
                    } else if (localTime > remoteTime + 2000 || reason == "LOCAL_FURTHER_TIEBREAK") {
                        // We only push if we are truly newer or furthest
                        android.util.Log.d("SyncRepository", "[PUSH] '${book.title}' reason=$reason (L:${localPercent}% vs R:${remotePercent}%)")
                        logConflict(book.title, winner = "LOCAL", localPercent = localPercent, remotePercent = remotePercent, reason = reason)
                        pushProgress(bookId)
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
    private fun logConflict(title: String, winner: String, localPercent: Float, remotePercent: Float, reason: String) {
        try {
            val logFile = java.io.File(context.filesDir, "sync_conflicts.log")
            val entry = "${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())} | $winner won | '$title' | L:${String.format("%.1f", localPercent)}% R:${String.format("%.1f", remotePercent)}% | $reason\n"
            logFile.appendText(entry)
            // Keep only last 200 lines up to 50kB to avoid unbounded growth
            val lines = logFile.readLines()
            if (lines.size > 200) logFile.writeText(lines.takeLast(200).joinToString("\n") + "\n")
        } catch (e: Exception) {
            android.util.Log.w("SyncRepository", "Failed to write conflict log: ${e.message}")
        }
    }
}
