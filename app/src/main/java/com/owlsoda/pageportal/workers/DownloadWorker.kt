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
                "pdf" -> DownloadUtils.DownloadFormat.PDF
                "readaloud" -> DownloadUtils.DownloadFormat.READALOUD
                else -> DownloadUtils.DownloadFormat.AUDIO
            }
            
            downloadUrl = when (format) {
                DownloadUtils.DownloadFormat.AUDIO -> 
                    details.files.firstOrNull { it.mimeType.startsWith("audio/") }?.downloadUrl
                DownloadUtils.DownloadFormat.EBOOK -> 
                    details.files.firstOrNull { it.mimeType == "application/epub+zip" }?.downloadUrl
                DownloadUtils.DownloadFormat.PDF ->
                    details.files.firstOrNull { it.mimeType == "application/pdf" }?.downloadUrl
                DownloadUtils.DownloadFormat.READALOUD -> 
                    details.files.firstOrNull { it.mimeType == "application/zip" || it.filename.contains("readaloud") }?.downloadUrl
            }
            
            if (downloadUrl == null) {
                val availableMimeTypes = details.files.map { "${it.filename} (${it.mimeType})" }
                logToFile("ERROR: No URL for $downloadType. Available: $availableMimeTypes")
                // Also log to regular Logcat
                Log.e(TAG, "No download URL for $downloadType. Book: ${book.title}. Available: $availableMimeTypes")
                return Result.failure()
            }
            
            // Log full URL for debugging (sanitized token)
            val logUrl = downloadUrl.replace(Regex("token=[^&]+"), "token=REDACTED")
            logToFile("Resolved URL for $downloadType: $logUrl")
            Log.d(TAG, "Download URL: $logUrl")
            
            logToFile("Starting download: $downloadUrl")
            
            val targetFile = DownloadUtils.getFilePath(applicationContext.filesDir, book, format)
            targetFile.parentFile?.mkdirs()
            
            bookDao.updateDownloadStatus(dbBookId, DownloadStatus.DOWNLOADING.name, 0f, null)
            
            // Show initial notification
            setForeground(createForegroundInfo(0, book.title))
            
            var lastLoggedProgress = 0
            var lastNotificationProgress = 0
            
            // Prepare headers
            val headers = mutableMapOf<String, String>()
            val hasTokenInUrl = downloadUrl.contains("token=")
            val serviceEntity = serviceManager.getServiceEntity(serverId)
            
            if (!hasTokenInUrl && serviceEntity?.authToken != null) {
                if (serviceEntity.serviceType == "BOOKLORE") {
                    headers["Authorization"] = serviceEntity.authToken
                } else {
                    headers["Authorization"] = "Bearer ${serviceEntity.authToken}"
                }
            }
            
            logToFile("Starting robust download to: ${targetFile.absolutePath}")
            
            DownloadUtils.downloadFile(
                client = okHttpClient,
                url = downloadUrl,
                file = targetFile,
                headers = headers,
                numParts = 6, // Parallel parts for speed
                onProgress = { progress ->
                    val percent = (progress * 100).toInt()
                    
                    // Reporting to DB and Notification
                    if (percent > lastNotificationProgress + 2) {
                        lastNotificationProgress = percent
                        setForeground(createForegroundInfo(percent, book.title))
                        bookDao.updateDownloadStatus(dbBookId, DownloadStatus.DOWNLOADING.name, progress, null)
                    }
                }
            )

            val filePath = targetFile.absolutePath
            when (format) {
                DownloadUtils.DownloadFormat.AUDIO -> 
                    bookDao.updateAudiobookDownloaded(dbBookId, true, filePath)
                DownloadUtils.DownloadFormat.EBOOK -> 
                    bookDao.updateEbookDownloaded(dbBookId, true, filePath)
                DownloadUtils.DownloadFormat.PDF ->
                    bookDao.updateEbookDownloaded(dbBookId, true, filePath)
                DownloadUtils.DownloadFormat.READALOUD -> 
                    bookDao.updateReadAloudDownloaded(dbBookId, true, filePath)
            }
            bookDao.updateDownloadStatus(dbBookId, DownloadStatus.COMPLETED.name, 1f, filePath)
            
            // Post-processing for ReadAloud
            if (format == DownloadUtils.DownloadFormat.READALOUD) {
                logToFile("Post-processing ReadAloud: Unzipping...")
                try {
                    val destDir = java.io.File(applicationContext.cacheDir, "readaloud/$dbBookId")
                    DownloadUtils.unzipFile(targetFile, destDir)
                    logToFile("ReadAloud unzipped to: ${destDir.absolutePath}")
                } catch (e: Exception) {
                    logToFile("ReadAloud unzip FAILED: ${e.message}")
                    // Don't fail the whole download, but log it
                }
            }
            
            setForeground(createForegroundInfo(100, book.title))
            logToFile("SUCCESS: $filePath")
            Result.success()
            
        } catch (e: Throwable) {
            val urlInfo = if (downloadUrl != null) "URL: ${downloadUrl.replace(Regex("token=[^&]+"), "token=REDACTED")}" else "URL not resolved"
            val errorDetails = "${e.javaClass.simpleName}: ${e.message}"
            logToFile("CRITICAL ERROR ($urlInfo): $errorDetails")
            Log.e(TAG, "DownloadWorker CRITICAL ERROR: $errorDetails", e)
            
            // Log stack trace summary to log file
            val stackSummary = e.stackTrace.take(8).joinToString("\n") { "  at $it" }
            logToFile("Stack:\n$stackSummary")
            
            bookDao.updateDownloadStatus(dbBookId, DownloadStatus.FAILED.name, 0f, null)
            
            if (runAttemptCount < 3 && e is java.io.IOException) {
                 logToFile("Retrying (attempt $runAttemptCount)...")
                 Result.retry()
            } else {
                 Result.failure()
            }
        }
    }
    
    private fun createForegroundInfo(progress: Int, title: String): androidx.work.ForegroundInfo {
        val channelId = "downloads"
        val notificationId = 1001 + (inputData.getLong(KEY_DB_BOOK_ID, 0).toInt()) 
        // Unique ID per book
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId, "Downloads", android.app.NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = applicationContext.getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        val progressText = if (progress == -1) "Downloading..." else "$progress%"
        
        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Downloading: $title")
            .setTicker("Downloading: $title")
            .setContentText(progressText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, if (progress == -1) 0 else progress, progress == -1)
            .setOnlyAlertOnce(true)
            .build()
            
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return androidx.work.ForegroundInfo(
                notificationId, 
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }
        return androidx.work.ForegroundInfo(notificationId, notification)
    }
}

