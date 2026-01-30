package com.owlsoda.pageportal.data.repository

import android.content.Context
import androidx.work.*
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.services.DownloadStatus
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
//            .setConstraints(
//                Constraints.Builder()
//                    .setRequiredNetworkType(NetworkType.CONNECTED)
//                    .build()
//            )
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

    suspend fun deleteDownload(bookId: Long) {
        val book = bookDao.getBookById(bookId) ?: return
        
        // Delete Audiobook
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
            bookDao.updateDownloadStatus(bookId, "NONE", 0f, null)
        }
        
        // Delete Ebook
        if (book.isEbookDownloaded) {
            // Logic if ebook path stored separately or same path
        }
        
        // General cleanup if only one path field
        bookDao.updateDownloadStatus(bookId, "NONE", 0f, null)
    }
}
