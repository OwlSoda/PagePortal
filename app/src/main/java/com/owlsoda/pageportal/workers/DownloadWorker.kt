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
import okhttp3.Request
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Worker for downloading book files in the background.
 * Uses manual EntryPoint injection to bypass HiltWorkerFactory complexity/instability.
 *
 * Features:
 * - Pre-flight URL validation (HEAD request before download)
 * - User-facing error messages stored to DB
 * - Auth conflict prevention (skips AuthInterceptor when URL has token=)
 * - Smart download strategy (single vs multi-part)
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
        fun libraryRepository(): com.owlsoda.pageportal.data.repository.LibraryRepository
    }

    companion object {
        private const val TAG = "DownloadWorker"
        const val KEY_DB_BOOK_ID = "book_local_id"
        const val KEY_SERVER_ID = "server_id"
        const val KEY_SERVICE_BOOK_ID = "service_book_id"
        const val KEY_DOWNLOAD_TYPE = "download_type"
    }

    private fun logToFile(message: String) {
        com.owlsoda.pageportal.util.LogManager.log(TAG, message)
    }


    /**
     * Pre-flight validation: HEAD request to check URL returns a downloadable file.
     * Returns null on success, or a user-friendly error message on failure.
     */
    private suspend fun validateDownloadUrl(client: OkHttpClient, url: String, headers: Map<String, String> = emptyMap()): String? {
        return try {
            // Use GET with Range: bytes=0-0 instead of HEAD. 
            // Many servers/proxies return 404 for HEAD on dynamic file routes but allow GET.
            val request = Request.Builder()
                .url(url)
                .addHeader("Range", "bytes=0-0")
                .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                val code = response.code
                val contentType = response.header("Content-Type") ?: ""
                
                // Detailed header logging for debugging 404/Auth issues
                logToFile("Pre-flight: HTTP $code, Content-Type: $contentType")
                val responseHeaders = response.headers.names().joinToString(", ") { name ->
                    "$name: ${response.header(name)}"
                }
                logToFile("Pre-flight Headers: $responseHeaders")

                when {
                    code == 401 || code == 403 -> "Authentication expired — try logging out and back in"
                    code == 404 -> "File not found on server — the book may need reprocessing"
                    code == 500 -> "Server error (500) — try again later"
                    code in 502..504 -> "Server is temporarily unavailable — try again later"
                    code == 429 -> "Server is rate-limiting requests — wait a moment and try again"
                    code >= 400 -> "Server returned error $code"
                    contentType.contains("text/html") -> {
                        logToFile("WARNING: Server returned HTML instead of binary file")
                        "Server returned an error page instead of the file"
                    }
                    else -> null // Success (usually 200 or 206)
                }
            }
        } catch (e: java.net.UnknownHostException) {
            "Cannot reach server — check your connection"
        } catch (e: java.net.ConnectException) {
            "Connection refused — is the server running?"
        } catch (e: java.net.SocketTimeoutException) {
            "Server took too long to respond"
        } catch (e: javax.net.ssl.SSLException) {
            "SSL/TLS error — check server certificate"
        } catch (e: Exception) {
            val stackTrace = e.stackTrace.take(10).joinToString("\n") { "  at $it" }
            logToFile("Pre-flight exception: ${e.javaClass.simpleName}: ${e.message}\n$stackTrace")
            // Don't fail here — let the actual download attempt provide a better error
            null
        }
    }

    /**
     * Map a download exception to a user-friendly error message.
     */
    private fun getErrorMessage(e: Throwable): String {
        return when (e) {
            is java.net.SocketTimeoutException -> "Download timed out — server was too slow"
            is java.net.UnknownHostException -> "Cannot reach server — check your connection"
            is java.net.ConnectException -> "Connection refused — is the server running?"
            is javax.net.ssl.SSLException -> "SSL/TLS error — check server certificate"
            is java.io.EOFException -> "Connection dropped unexpectedly — try again"
            is java.io.IOException -> {
                val msg = e.message ?: ""
                when {
                    msg.contains("HTTP 401") || msg.contains("HTTP 403") -> "Authentication expired — re-login required"
                    msg.contains("HTTP 404") -> "File not found on server"
                    msg.contains("HTTP 429") -> "Server rate limit — wait and try again"
                    msg.contains("HTTP 5") -> "Server error — try again later"
                    msg.contains("unexpected end") -> "Download interrupted — try again"
                    else -> "Network error: $msg"
                }
            }
            else -> {
                val msg = e.message ?: ""
                when {
                    msg.contains("HTTP 401") || msg.contains("HTTP 403") -> "Authentication expired — re-login required"
                    msg.contains("HTTP 404") -> "File not found on server"
                    msg.contains("HTTP 429") -> "Server rate limit — wait and try again"
                    msg.contains("HTTP 5") -> "Server error — try again later"
                    msg.contains("Content-Type") -> "Server returned wrong file type"
                    else -> "Download failed: ${e.javaClass.simpleName}"
                }
            }
        }
    }

    override suspend fun doWork(): Result {
        logToFile("DownloadWorker started")
        
        // Manual Injection
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            DownloadWorkerEntryPoint::class.java
        )
        val bookDao = entryPoint.bookDao()
        val serviceManager = entryPoint.serviceManager()
        val baseOkHttpClient = entryPoint.okHttpClient()
        val libraryRepository = entryPoint.libraryRepository()

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
            var book = bookDao.getBookById(dbBookId)
            if (book == null) {
                logToFile("ERROR: Book not found in DB: $dbBookId")
                return Result.failure()
            }
            
            // --- Step 0: Refresh latest metadata from server ---
            logToFile("Refreshing latest metadata from server...")
            try {
                libraryRepository.refreshBookMetadata(dbBookId)
            } catch (e: Exception) {
                logToFile("Metadata refresh failed (non-fatal): ${e.message}")
            }

            // Capture stable local reference for smart-casting
            val currentBook = bookDao.getBookById(dbBookId) ?: book
            
            val service = serviceManager.getService(serverId)
            if (service == null) {
                logToFile("ERROR: Service not found: $serverId")
                bookDao.updateDownloadStatus(dbBookId, DownloadStatus.FAILED.name, 0f, null, error = "Service connection lost — re-add the server")
                return Result.failure()
            }

            logToFile("Fetching details for book: ${currentBook.title}")
            val details = service.getBookDetails(serviceBookId)
            
            val format = when (downloadType) {
                "audio" -> DownloadUtils.DownloadFormat.AUDIO
                "ebook" -> DownloadUtils.DownloadFormat.EBOOK
                "pdf" -> DownloadUtils.DownloadFormat.PDF
                "readaloud" -> DownloadUtils.DownloadFormat.READALOUD
                else -> DownloadUtils.DownloadFormat.AUDIO
            }
            
            // --- File matching (broad) ---
            val availableFiles = details.files
            logToFile("Found ${availableFiles.size} candidate files for book $serviceBookId")
            availableFiles.forEach { logToFile("  - File: ${it.filename}, Mime: ${it.mimeType}, URL: ${it.downloadUrl.take(80)}...") }

            downloadUrl = when (format) {
                DownloadUtils.DownloadFormat.AUDIO -> 
                    availableFiles.firstOrNull { it.mimeType.startsWith("audio/") || it.filename.endsWith(".m4b") || it.filename.endsWith(".mp3") }?.downloadUrl
                DownloadUtils.DownloadFormat.EBOOK -> 
                    availableFiles.firstOrNull { it.mimeType == "application/epub+zip" || it.mimeType == "application/epub" || it.filename.endsWith(".epub") }?.downloadUrl
                DownloadUtils.DownloadFormat.PDF ->
                    availableFiles.firstOrNull { it.mimeType == "application/pdf" }?.downloadUrl
                DownloadUtils.DownloadFormat.READALOUD -> 
                    availableFiles.firstOrNull { it.mimeType == "application/zip" || it.filename.contains("readaloud") || it.filename.contains("sync") }?.downloadUrl
            }

            if (downloadUrl == null) {
                val available = availableFiles.joinToString { "${it.filename} (${it.mimeType})" }
                val errorMsg = if (downloadType == "readaloud") {
                    when (currentBook.processingStatus) {
                        "processing" -> "ReadAloud is still aligning on the server (${(currentBook.processingProgress ?: 0f) * 100}%). Please wait."
                        "queued" -> "ReadAloud is queued for alignment on the server. Please wait."
                        "failed" -> "ReadAloud alignment failed on the server. Please re-trigger alignment."
                        else -> "ReadAloud file not found on server — alignment may not have been started."
                    }
                } else if (availableFiles.isEmpty()) {
                    "No files available for this book — the server may not have processed it yet"
                } else {
                    "No $downloadType file found — available: $available"
                }
                logToFile("ERROR: $errorMsg")
                Log.e(TAG, errorMsg)
                bookDao.updateDownloadStatus(dbBookId, DownloadStatus.FAILED.name, 0f, null, error = errorMsg)
                return Result.failure()
            }
            
            var lastNotificationProgress = 0
            
            // --- Step 4: Use base client (AuthInterceptor handles standard auth) ---
            val downloadClient = baseOkHttpClient
            
            // Prepare auth headers (needed for pre-flight and multi-part)
            val headers = mutableMapOf<String, String>()
            val serviceEntity = serviceManager.getServiceEntity(serverId)
            if (serviceEntity?.authToken != null) {
                if (serviceEntity.serviceType == "BOOKLORE") {
                    headers["Authorization"] = serviceEntity.authToken
                } else {
                    headers["Authorization"] = "Bearer ${serviceEntity.authToken}"
                }
            }
            
            // --- Step 1: Pre-flight URL validation ---
            // Skip pre-flight for Storyteller /files endpoints — these are dynamic file-serving
            // routes that don't support Range requests and return 404 for partial GETs even
            // though a full download works fine.
            val isDynamicFileEndpoint = downloadUrl.contains("/files?format=")
            if (isDynamicFileEndpoint) {
                logToFile("Skipping pre-flight for dynamic file endpoint: $downloadType")
            } else {
                logToFile("Pre-flight check for $downloadType")
                val validationError = validateDownloadUrl(downloadClient, downloadUrl, headers)
                if (validationError != null) {
                    logToFile("Pre-flight FAILED: $validationError")
                    bookDao.updateDownloadStatus(dbBookId, DownloadStatus.FAILED.name, 0f, null, error = validationError)
                    return Result.failure()
                }
                logToFile("Pre-flight OK")
            }
            
            // Log sanitized URL
            val logUrl = downloadUrl.replace(Regex("token=[^&]+"), "token=REDACTED")
            logToFile("Resolved URL for $downloadType: $logUrl")
            
            val targetFile = DownloadUtils.getFilePath(applicationContext.filesDir, currentBook, format)
            targetFile.parentFile?.mkdirs()
            
            bookDao.updateDownloadStatus(dbBookId, DownloadStatus.DOWNLOADING.name, 0f, null, error = null)
            
            // Show initial notification
            setForeground(createForegroundInfo(0, currentBook.title))
            
            logToFile("Starting download to: ${targetFile.absolutePath}")
            
            // --- Step 3: Smart download strategy (DownloadUtils decides internally) ---
            DownloadUtils.downloadFile(
                client = downloadClient,
                url = downloadUrl,
                file = targetFile,
                headers = headers,
                onProgress = { progress ->
                    val percent = (progress * 100).toInt()
                    if (percent > lastNotificationProgress + 2) {
                        lastNotificationProgress = percent
                        setForeground(createForegroundInfo(percent, currentBook.title))
                        bookDao.updateDownloadStatus(dbBookId, DownloadStatus.DOWNLOADING.name, progress, null)
                    }
                }
            )
            
            // --- Step 5: Post-download validation ---
            val validationResult = DownloadUtils.validateDownloadedFile(targetFile, format)
            if (validationResult != null) {
                logToFile("Post-download validation FAILED: $validationResult")
                targetFile.delete()
                bookDao.updateDownloadStatus(dbBookId, DownloadStatus.FAILED.name, 0f, null, error = validationResult)
                return Result.failure()
            }

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
            bookDao.updateDownloadStatus(dbBookId, DownloadStatus.COMPLETED.name, 1f, filePath, error = null)
            
            // Post-processing for ReadAloud
            if (format == DownloadUtils.DownloadFormat.READALOUD) {
                logToFile("Post-processing ReadAloud: Unzipping...")
                try {
                    val destDir = java.io.File(applicationContext.cacheDir, "readaloud/$dbBookId")
                    DownloadUtils.unzipFile(targetFile, destDir)
                    logToFile("ReadAloud unzipped to: ${destDir.absolutePath}")
                } catch (e: Exception) {
                    logToFile("ReadAloud unzip FAILED: ${e.message}")
                }
            }
            
            setForeground(createForegroundInfo(100, currentBook.title))
            logToFile("SUCCESS: $filePath")
            Result.success()
            
        } catch (e: NumberFormatException) {
            val trace = Log.getStackTraceString(e)
            val lines = trace.split("\n").take(5).joinToString("\n")
            logToFile("CRITICAL: NumberFormatException for input: ${e.message}\n$trace")
            bookDao.updateDownloadStatus(dbBookId, DownloadStatus.FAILED.name, 0f, null, error = "Critical error: malformed data (${e.message})\n$lines")
            Result.failure()
        } catch (e: Throwable) {
            val urlInfo = if (downloadUrl != null) "URL: ${downloadUrl.replace(Regex("token=[^&]+"), "token=REDACTED")}" else "URL not resolved"
            val errorDetails = "${e.javaClass.simpleName}: ${e.message}"
            logToFile("CRITICAL ERROR ($urlInfo): $errorDetails")
            Log.e(TAG, "DownloadWorker CRITICAL ERROR: $errorDetails", e)
            
            // Stack trace to log file
            val stackSummary = e.stackTrace.take(8).joinToString("\n") { "  at $it" }
            logToFile("Stack:\n$stackSummary")
            
            // User-facing error message
            val userMessage = getErrorMessage(e)
            bookDao.updateDownloadStatus(dbBookId, DownloadStatus.FAILED.name, 0f, null, error = userMessage)
            
            if (runAttemptCount < 5 && e is java.io.IOException) {
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
