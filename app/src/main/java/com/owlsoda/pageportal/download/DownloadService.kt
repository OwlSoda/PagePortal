package com.owlsoda.pageportal.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.services.ServiceBook
import com.owlsoda.pageportal.services.ServiceType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

import javax.inject.Inject
import com.owlsoda.pageportal.data.preferences.PreferencesRepository
import kotlinx.coroutines.flow.first

/**
 * Foreground service for downloading audiobooks and ebooks.
 * Manages download queue, progress tracking, and retry logic.
 */
@AndroidEntryPoint
class DownloadService : LifecycleService() {
    
    @Inject
    lateinit var okHttpClient: OkHttpClient
    
    @Inject
    lateinit var bookDao: BookDao
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "download_channel"
        
        // Download state shared across the app
        private val _activeDownloads = MutableStateFlow<List<DownloadJob>>(emptyList())
        val activeDownloads: StateFlow<List<DownloadJob>> = _activeDownloads.asStateFlow()
        
        private val downloadQueue = mutableListOf<DownloadRequest>()
        private val activeJobs = mutableMapOf<String, Job>()
        
        fun startDownload(
            context: Context,
            bookId: String,
            serviceType: ServiceType,
            downloadUrl: String,
            fileName: String,
            coverUrl: String?,
            title: String,
            authToken: String?
        ) {
            val request = DownloadRequest(
                bookId = bookId,
                serviceType = serviceType,
                downloadUrl = downloadUrl,
                fileName = fileName,
                coverUrl = coverUrl,
                title = title,
                authToken = authToken
            )
            
            synchronized(downloadQueue) {
                if (downloadQueue.none { it.bookId == bookId }) {
                    downloadQueue.add(request)
                }
            }
            
            val intent = Intent(context, DownloadService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
        
        fun cancelDownload(bookId: String) {
            activeJobs[bookId]?.cancel()
            activeJobs.remove(bookId)
            
            synchronized(downloadQueue) {
                downloadQueue.removeAll { it.bookId == bookId }
            }
            
            _activeDownloads.value = _activeDownloads.value.filter { it.bookId != bookId }
        }
        
        fun getDownloadProgress(bookId: String): DownloadJob? {
            return _activeDownloads.value.find { it.bookId == bookId }
        }
    }
    
    private val downloadSemaphore = Semaphore(3) // Max concurrent downloads
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Starting downloads..."))
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        processQueue()
        return START_STICKY
    }
    
    private fun processQueue() {
        lifecycleScope.launch {
            while (true) {
                val request = synchronized(downloadQueue) {
                    downloadQueue.firstOrNull { activeJobs[it.bookId] == null }
                } ?: break
                
                val job = downloadSemaphore.withPermit {
                    performDownload(request)
                }
            }
            
            // Stop service when queue is empty
            if (_activeDownloads.value.all { it.isCompleted || it.isFailed }) {
                stopSelf()
            }
        }
    }
    
    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    private suspend fun performDownload(request: DownloadRequest) {
        val downloadJob = DownloadJob(
            bookId = request.bookId,
            serviceType = request.serviceType,
            fileName = request.fileName,
            title = request.title,
            status = "Downloading...",
            progress = 0f
        )
        
        _activeDownloads.value = _activeDownloads.value + downloadJob
        updateNotification(downloadJob)
        
        // Check Offline Mode
        val isOffline = preferencesRepository.isOfflineModeEnabled.first()
        if (isOffline) {
             val failed = downloadJob.copy(
                 isFailed = true,
                 status = "Failed: Offline Mode Enabled"
             )
             _activeDownloads.value = _activeDownloads.value.map {
                 if (it.bookId == request.bookId) failed else it
             }
             updateNotification(failed)
             synchronized(downloadQueue) {
                 downloadQueue.removeAll { it.bookId == request.bookId }
             }
             return
        }
        
        var retryCount = 0
        val maxRetries = 3
        
        while (retryCount <= maxRetries) {
            try {
                val requestBuilder = Request.Builder().url(request.downloadUrl)
                request.authToken?.let {
                    requestBuilder.addHeader("Authorization", "Bearer $it")
                }
                
                val response = okHttpClient.newCall(requestBuilder.build()).execute()
                
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}")
                }
                
                val body = response.body ?: throw Exception("Empty response")
                val contentLength = body.contentLength()
                
                val downloadDir = getDownloadDirectory(request.serviceType)
                downloadDir.mkdirs()
                val file = File(downloadDir, request.fileName)
                
                FileOutputStream(file).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Long = 0
                        var read: Int
                        
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            
                            val progress = if (contentLength > 0) {
                                bytesRead.toFloat() / contentLength
                            } else 0f
                            
                            val updated = downloadJob.copy(progress = progress)
                            _activeDownloads.value = _activeDownloads.value.map {
                                if (it.bookId == request.bookId) updated else it
                            }
                            updateNotification(updated)
                        }
                    }
                }
                
                // Success
                val completed = downloadJob.copy(
                    isCompleted = true,
                    progress = 1f,
                    status = "Completed"
                )
                _activeDownloads.value = _activeDownloads.value.map {
                    if (it.bookId == request.bookId) completed else it
                }
                updateNotification(completed)
                
                synchronized(downloadQueue) {
                    downloadQueue.removeAll { it.bookId == request.bookId }
                }
                return
                
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                retryCount++
                if (retryCount <= maxRetries) {
                    val updated = downloadJob.copy(status = "Retrying ($retryCount/$maxRetries)...")
                    _activeDownloads.value = _activeDownloads.value.map {
                        if (it.bookId == request.bookId) updated else it
                    }
                    delay(retryCount * 2000L) // Exponential backoff
                } else {
                    val failed = downloadJob.copy(
                        isFailed = true,
                        status = "Failed: ${e.message}"
                    )
                    _activeDownloads.value = _activeDownloads.value.map {
                        if (it.bookId == request.bookId) failed else it
                    }
                }
            }
        }
    }
    
    private fun getDownloadDirectory(serviceType: ServiceType): File {
        val baseDir = getExternalFilesDir(null) ?: filesDir
        return File(baseDir, "downloads/${serviceType.name.lowercase()}")
    }
    
    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Book download progress"
            }
            
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(text: String): android.app.Notification {
        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
        }
        
        return builder
            .setContentTitle("PagePortal Downloads")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(job: DownloadJob) {
        val text = if (job.isCompleted) {
            "Downloaded: ${job.title}"
        } else if (job.isFailed) {
            "Failed: ${job.title}"
        } else {
            "${job.title}: ${(job.progress * 100).toInt()}%"
        }
        
        val notification = createNotification(text)
        val notificationManager = getSystemService(android.app.NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}

data class DownloadRequest(
    val bookId: String,
    val serviceType: ServiceType,
    val downloadUrl: String,
    val fileName: String,
    val coverUrl: String?,
    val title: String,
    val authToken: String?
)

data class DownloadJob(
    val bookId: String,
    val serviceType: ServiceType,
    val fileName: String,
    val title: String,
    val progress: Float = 0f,
    val status: String = "",
    val isFailed: Boolean = false,
    val isCompleted: Boolean = false
)
