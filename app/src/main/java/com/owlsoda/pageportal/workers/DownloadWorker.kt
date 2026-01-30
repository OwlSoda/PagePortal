package com.owlsoda.pageportal.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.owlsoda.pageportal.services.ServiceManager
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.services.DownloadStatus
import com.owlsoda.pageportal.util.DownloadUtils
import okhttp3.OkHttpClient
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Worker for downloading book files in the background.
 * Uses manual EntryPoint injection to bypass HiltWorkerFactory complexity/instability.
 */
class DownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DownloadWorkerEntryPoint {
        fun serviceManager(): ServiceManager
        fun bookDao(): BookDao
        fun okHttpClient(): OkHttpClient
    }

    companion object {
        private const val TAG = "DownloadWorker"
        const val KEY_DB_BOOK_ID = "book_local_id"
        const val KEY_SERVER_ID = "server_id"
        const val KEY_SERVICE_BOOK_ID = "service_book_id"
        const val KEY_DOWNLOAD_TYPE = "download_type"
    }

    private fun logToFile(message: String) {
        try {
            val debugFile = java.io.File(applicationContext.filesDir, "download_debug.log")
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            debugFile.appendText("[$timestamp] $message\n")
        } catch (e: Exception) { }
    }

    override suspend fun doWork(): Result {
        logToFile("DownloadWorker (Std) started")
        
        // Manual Injection
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            DownloadWorkerEntryPoint::class.java
        )
        val bookDao = entryPoint.bookDao()
        val serviceManager = entryPoint.serviceManager()
        val okHttpClient = entryPoint.okHttpClient()

        val dbBookId = inputData.getLong(KEY_DB_BOOK_ID, -1L)
        val serverId = inputData.getLong(KEY_SERVER_ID, -1L)
        val serviceBookId = inputData.getString(KEY_SERVICE_BOOK_ID)
        val downloadType = inputData.getString(KEY_DOWNLOAD_TYPE) ?: "audio"

        logToFile("Inputs: dbId=$dbBookId, serverId=$serverId, type=$downloadType")

        if (dbBookId == -1L || serverId == -1L || serviceBookId == null) {
            logToFile("ERROR: Missing inputs")
            return Result.failure()
        }
        
        var downloadUrl: String? = null

        return try {
            val book = bookDao.getBookById(dbBookId)
            if (book == null) {
                logToFile("ERROR: Book not found in DB: $dbBookId")
                return Result.failure()
            }
            
            val service = serviceManager.getService(serverId)
            if (service == null) {
                logToFile("ERROR: Service not found: $serverId")
                return Result.failure()
            }

            logToFile("Fetching details for book: ${book.title}")
            val details = service.getBookDetails(serviceBookId)
            
            val format = when (downloadType) {
                "audio" -> DownloadUtils.DownloadFormat.AUDIO
                "ebook" -> DownloadUtils.DownloadFormat.EBOOK
                "readaloud" -> DownloadUtils.DownloadFormat.READALOUD
                else -> DownloadUtils.DownloadFormat.AUDIO
            }
            
            downloadUrl = when (format) {
                DownloadUtils.DownloadFormat.AUDIO -> 
                    details.files.firstOrNull { it.mimeType.startsWith("audio/") }?.downloadUrl
                DownloadUtils.DownloadFormat.EBOOK -> 
                    details.files.firstOrNull { it.mimeType == "application/epub+zip" }?.downloadUrl
                DownloadUtils.DownloadFormat.READALOUD -> 
                    details.files.firstOrNull { it.mimeType == "application/zip" || it.filename.contains("readaloud") }?.downloadUrl
            }
            
            if (downloadUrl == null) {
                val availableMimeTypes = details.files.map { "${it.filename} (${it.mimeType})" }
                logToFile("ERROR: No URL for $downloadType. Available: $availableMimeTypes")
                return Result.failure()
            }
            
            logToFile("Starting download: $downloadUrl")
            
            val targetFile = DownloadUtils.getFilePath(applicationContext.filesDir, book, format)
            targetFile.parentFile?.mkdirs()
            
            bookDao.updateDownloadStatus(dbBookId, DownloadStatus.DOWNLOADING.name, 0f, null)
            
            var lastLoggedProgress = 0
            DownloadUtils.downloadFile(
                client = okHttpClient,
                url = downloadUrl!!,
                file = targetFile,
                onProgress = { progress ->
                    val percent = (progress * 100).toInt()
                    if (percent >= lastLoggedProgress + 10) {
                        lastLoggedProgress = percent
                        if (percent % 20 == 0) logToFile("Progress: $percent%")
                    }
                }
            )
            
            val filePath = targetFile.absolutePath
            when (format) {
                DownloadUtils.DownloadFormat.AUDIO -> 
                    bookDao.updateAudiobookDownloaded(dbBookId, true, filePath)
                DownloadUtils.DownloadFormat.EBOOK -> 
                    bookDao.updateEbookDownloaded(dbBookId, true, filePath)
                DownloadUtils.DownloadFormat.READALOUD -> 
                    bookDao.updateReadAloudDownloaded(dbBookId, true, filePath)
            }
            
            bookDao.updateDownloadStatus(dbBookId, DownloadStatus.COMPLETED.name, 1f, filePath)
            logToFile("SUCCESS: $filePath")
            Result.success()
            
        } catch (e: Exception) {
            val urlInfo = if (downloadUrl != null) "URL: $downloadUrl" else "URL not resolved"
            logToFile("EXCEPTION ($urlInfo): ${e.message}")
            bookDao.updateDownloadStatus(dbBookId, DownloadStatus.FAILED.name, 0f, null)
            Result.failure()
        }
    }
}

