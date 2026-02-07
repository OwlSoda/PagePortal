package com.owlsoda.pageportal.data.repository

import android.content.Context
import androidx.work.*
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.services.DownloadStatus
import com.owlsoda.pageportal.util.DownloadUtils
import com.owlsoda.pageportal.workers.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao
) {
    private val workManager = WorkManager.getInstance(context)

    suspend fun startDownload(bookId: Long, serverId: Long, serviceBookId: String, downloadType: String? = null) {
        // Enqueue Work
        val inputData = workDataOf(
            DownloadWorker.KEY_DB_BOOK_ID to bookId,
            DownloadWorker.KEY_SERVER_ID to serverId,
            DownloadWorker.KEY_SERVICE_BOOK_ID to serviceBookId,
            DownloadWorker.KEY_DOWNLOAD_TYPE to downloadType
        )

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .addTag("download_$bookId")
            .build()
            
        // Log enqueue
        try {
            val debugFile = java.io.File(context.filesDir, "download_debug.log")
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            debugFile.appendText("[$timestamp] REPO: Enqueueing work for book $bookId (Server: $serverId, ID: $serviceBookId)\n")
        } catch (e: Exception) { }

        workManager.enqueueUniqueWork(
            "download_$bookId",
            ExistingWorkPolicy.REPLACE,
            request
        )
        
        // Optimistic update in DB
        bookDao.updateDownloadStatus(bookId, DownloadStatus.QUEUED.name, 0f, null)
    }

    suspend fun cancelDownload(bookId: Long) {
        workManager.cancelUniqueWork("download_$bookId")
        bookDao.updateDownloadStatus(bookId, DownloadStatus.CANCELLED.name, 0f, null)
    }

    suspend fun deleteDownload(bookId: Long, type: String? = null) {
        val book = bookDao.getBookById(bookId) ?: return
        
        // Delete Audiobook
        if (type == null || type == "audio") {
            if (book.isAudiobookDownloaded) {
                book.localFilePath?.let { path ->
                    try {
                        val file = java.io.File(path)
                        if (file.exists()) file.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                bookDao.updateAudiobookDownloaded(bookId, false, null)
            }
        }
        
        // Delete Ebook
        if (type == null || type == "ebook") {
            if (book.isEbookDownloaded) {
                try {
                    val file = DownloadUtils.getFilePath(context.filesDir, book, DownloadUtils.DownloadFormat.EBOOK)
                    if (file.exists()) file.delete()
                    
                    val pdfFile = DownloadUtils.getFilePath(context.filesDir, book, DownloadUtils.DownloadFormat.PDF)
                    if (pdfFile.exists()) pdfFile.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                bookDao.updateEbookDownloaded(bookId, false, null)
            }
        }

        // Delete ReadAloud
        if (type == null || type == "readaloud") {
            if (book.isReadAloudDownloaded) {
                try {
                    val file = DownloadUtils.getFilePath(context.filesDir, book, DownloadUtils.DownloadFormat.READALOUD)
                    if (file.exists()) file.delete()
                    
                    // Also clear cache
                    val cacheDir = java.io.File(context.cacheDir, "readaloud/$bookId")
                    if (cacheDir.exists()) cacheDir.deleteRecursively()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                bookDao.updateReadAloudDownloaded(bookId, false, null)
            }
        }
        
        // Final state reset if all are gone or if no type specified
        val updatedBook = bookDao.getBookById(bookId)
        if (updatedBook != null) {
            if (!updatedBook.isAudiobookDownloaded && !updatedBook.isEbookDownloaded && !updatedBook.isReadAloudDownloaded) {
                bookDao.updateDownloadStatus(bookId, "NONE", 0f, null)
            }
        }
    }
}
