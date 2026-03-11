package com.owlsoda.pageportal.services.audiobookshelf

import android.os.Build
import com.owlsoda.pageportal.services.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID

/**
 * BookService implementation for Audiobookshelf servers.
 * Supports audiobooks, podcasts, and ebooks.
 */
class AudiobookshelfService(
    private val serverUrl: String,
    private val okHttpClient: OkHttpClient
) : BookService {
    
    private var api: AudiobookshelfApi? = null
    private var authToken: String? = null
    private var userId: String? = null
    private var serverName: String? = null
    private var defaultLibraryId: String? = null
    private val deviceId = UUID.randomUUID().toString()
    
    private val normalizedUrl = normalizeUrl(serverUrl)

    private fun getApi(): AudiobookshelfApi {
        if (api == null) {
            val baseUrl = if (normalizedUrl.endsWith("/")) normalizedUrl else "$normalizedUrl/"
            api = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AudiobookshelfApi::class.java)
        }
        return checkNotNull(api) { "AudiobookshelfApi not initialized. Call authenticate() first." }
    }
    
    private fun bearerToken(): String {
        return "Bearer ${authToken ?: throw IllegalStateException("Not authenticated")}"
    }
    
    override suspend fun authenticate(
        serverUrl: String,
        username: String,
        password: String
    ): AuthResult {
        return try {
            // Normalize the URL
            val cleanUrl = normalizeUrl(serverUrl)

            // Create temporary client with generous timeouts for authentication
            val tempClient = okHttpClient.newBuilder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            // Create temporary API instance for login
            val baseUrl = if (cleanUrl.endsWith("/")) cleanUrl else "$cleanUrl/"
            val tempApi = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(tempClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AudiobookshelfApi::class.java)
            
            val response = tempApi.login(AbsLoginRequest(username, password))
            
            authToken = response.user.token
            userId = response.user.id
            serverName = response.serverSettings?.serverName
            defaultLibraryId = response.userDefaultLibraryId
            
            // Reinitialize the main API with the new token
            api = null // Force recreation with new client
            
            AuthResult(
                success = true,
                token = response.user.token,
                userId = response.user.id
            )
        } catch (e: retrofit2.HttpException) {
            val message = when (e.code()) {
                401 -> "Invalid username or password"
                403 -> "Access forbidden"
                else -> "Server error: ${e.code()}"
            }
            AuthResult(success = false, errorMessage = message)
        } catch (e: java.net.UnknownHostException) {
            AuthResult(success = false, errorMessage = "Cannot reach server. Please check the URL.")
        } catch (e: java.net.SocketTimeoutException) {
            AuthResult(success = false, errorMessage = "Connection timeout. Server not responding.")
        } catch (e: Exception) {
            AuthResult(success = false, errorMessage = e.message ?: "Authentication failed")
        }
    }
    
    suspend fun checkSsoEnabled(): Boolean {
        return try {
            val status = getApi().getStatus()
            status.serverSettings.authOpenIDAutoLaunch || status.serverSettings.authOpenIDEnabled
        } catch (e: Exception) {
            false
        }
    }

    suspend fun oauthCallback(state: String, code: String, verifier: String): AuthResult {
        return try {
            val response = getApi().oauthCallback(state, code, verifier)
            authToken = response.user.token
            userId = response.user.id
            serverName = response.serverSettings?.serverName
            defaultLibraryId = response.userDefaultLibraryId
            
            api = null // Force recreation with new client
            
            AuthResult(
                success = true,
                token = response.user.token,
                userId = response.user.id
            )
        } catch (e: Exception) {
            AuthResult(success = false, errorMessage = e.message ?: "OAuth callback failed")
        }
    }

    suspend fun validateToken(token: String): Boolean {
        return try {
            authToken = token
            getApi().getMe(bearerToken())
            true
        } catch (e: Exception) {
            authToken = null
            false
        }
    }
    
    fun setAuthToken(token: String) {
        authToken = token
    }
    
    override suspend fun listBooks(page: Int, pageSize: Int): List<ServiceBook> {
        return try {
            val validTypes = setOf("book", "audiobook")
            // Fetch all libraries matches valid types (usually type "book" covers both)
            val allLibraries = getApi().getLibraries(bearerToken())
            val targetLibraries = allLibraries.libraries.filter { it.mediaType in validTypes }

            if (targetLibraries.isEmpty()) {
                android.util.Log.w("AudiobookshelfService", "No valid book/audiobook libraries found.")
                return emptyList()
            }
            
            android.util.Log.d("AudiobookshelfService", "Listing books from ${targetLibraries.size} libraries (page $page)")

            // Query all target libraries in parallel
            coroutineScope {
                targetLibraries.map { library ->
                    async {
                        try {
                            val response = getApi().getLibraryItems(
                                token = bearerToken(),
                                libraryId = library.id,
                                page = page,
                                limit = pageSize
                            )
                            response.results.map { it.toServiceBook() }
                        } catch (e: Exception) {
                            android.util.Log.e("AudiobookshelfService", "Failed to list library ${library.name}", e)
                            emptyList<ServiceBook>()
                        }
                    }
                }.awaitAll().flatten()
            }
        } catch (e: Exception) {
            android.util.Log.e("AudiobookshelfService", "Failed to list books", e)
            emptyList()
        }
    }
    
    override suspend fun getBookDetails(bookId: String): ServiceBookDetails {
        val item = getApi().getItem(bearerToken(), bookId)
        val book = item.toServiceBook()
        
        // Map chapters
        val chapters = item.media.chapters?.map { chapter ->
            Chapter(
                id = chapter.id.toString(),
                title = chapter.title,
                startTime = (chapter.start * 1000).toLong(),
                duration = ((chapter.end - chapter.start) * 1000).toLong()
            )
        } ?: emptyList()
        
        // Map files (ebook/audio/cover)
        val files = mutableListOf<BookFile>()
        
        item.media.audioFiles?.firstOrNull()?.let { audio ->
            val filename = audio.metadata?.filename ?: "audio"
            // Use inode (ino) as fileId if available, otherwise filename might work
            val fileId = audio.ino ?: filename
            
            android.util.Log.d("AudiobookshelfService", "Found audio file: ino=${audio.ino}, filename=$filename")
            
            // Try specific file download endpoint
            // Common format: /api/items/{itemId}/file/{fileId}/download
             val encodedFileId = java.net.URLEncoder.encode(fileId, "UTF-8").replace("+", "%20")
             val url = "$normalizedUrl/api/items/$bookId/file/$encodedFileId/download?token=$authToken"
            
            files.add(
                BookFile(
                    id = filename,
                    filename = filename,
                    mimeType = audio.mimeType ?: "audio/mpeg",
                    size = audio.metadata?.size ?: 0L,
                    downloadUrl = url
                )
            )
        }
        
        item.media.ebookFile?.let { ebook ->
            val filename = ebook.metadata?.filename ?: "book"
            val format = ebook.ebookFormat ?: "epub"
            // Use inode as fileId
            val fileId = ebook.ino ?: filename
            
            val mimeType = when(format.lowercase()) {
                "epub" -> "application/epub+zip"
                "pdf" -> "application/pdf"
                "cbz" -> "application/vnd.comicbook+zip"
                "cbr" -> "application/vnd.comicbook-rar"
                else -> "application/octet-stream"
            }
            
            // Common format: /api/items/{itemId}/file/{fileId}/download
            // Encode fileId to handle spaces/special characters
            val encodedFileId = java.net.URLEncoder.encode(fileId, "UTF-8").replace("+", "%20")
            val url = "$normalizedUrl/api/items/$bookId/file/$encodedFileId/download?token=$authToken"
            
            files.add(
                BookFile(
                    id = filename,
                    filename = filename,
                    mimeType = mimeType,
                    size = ebook.metadata?.size ?: 0L,
                    downloadUrl = url
                )
            )
        }

        return ServiceBookDetails(
            book = book,
            chapters = chapters,
            files = files,
            totalDuration = book.duration,
            lastProgress = item.userMediaProgress?.toServiceProgress(bookId)
        )
    }
    
    suspend fun searchBooks(query: String): List<ServiceBook> {
        return try {
            val validTypes = setOf("book", "audiobook")
            val allLibraries = getApi().getLibraries(bearerToken())
            val targetLibraries = allLibraries.libraries.filter { it.mediaType in validTypes }
            
            if (targetLibraries.isEmpty()) return emptyList()
            
            coroutineScope {
                targetLibraries.map { library ->
                    async {
                        try {
                            val response = getApi().search(bearerToken(), library.id, query)
                            response.book?.map { it.libraryItem.toServiceBook() } ?: emptyList()
                        } catch (e: Exception) {
                            emptyList<ServiceBook>()
                        }
                    }
                }.awaitAll().flatten()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    override suspend fun getProgress(bookId: String): ReadingProgress? {
        return try {
            val progress = getApi().getProgress(bearerToken(), bookId)
            // Convert to ReadingProgress
            ReadingProgress(
                bookId = bookId,
                currentPosition = (progress.currentTime * 1000).toLong(),
                percentComplete = (progress.progress * 100).toFloat(),
                isFinished = progress.isFinished,
                lastUpdated = progress.lastUpdate
            )
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun updateProgress(bookId: String, progress: ReadingProgress) {
        try {
            val update = AbsProgressUpdate(
                currentTime = progress.currentPosition.toDouble() / 1000,
                progress = progress.percentComplete.toDouble() / 100,
                isFinished = progress.isFinished
            )
            getApi().updateProgress(bearerToken(), bookId, update)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun getStreamUrl(bookId: String): String? {
        return try {
            val deviceInfo = AbsDeviceInfo(
                deviceId = deviceId,
                clientName = "PagePortal",
                clientVersion = "1.0.0",
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                sdkVersion = Build.VERSION.SDK_INT
            )
            
            val session = getApi().startPlayback(
                token = bearerToken(),
                itemId = bookId,
                request = AbsPlayRequest(deviceInfo = deviceInfo)
            )
            
            // Return the content URL from the first audio track
            session.audioTracks?.firstOrNull()?.contentUrl?.let { url ->
                // Make absolute URL if needed
                if (url.startsWith("http")) url else "$normalizedUrl$url"
            }
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun getDownloadUrl(bookId: String): String? {
        // ABS provides direct file download
        return "$normalizedUrl/api/items/$bookId/file"
    }
    
    suspend fun getEbookUrl(bookId: String): String? {
        return try {
            val item = getApi().getItem(bearerToken(), bookId)
            item.media.ebookFile?.let { ebook ->
                "$normalizedUrl/api/items/$bookId/ebook"
            }
        } catch (e: Exception) {
            null
        }
    }
    
    override val serviceType = ServiceType.AUDIOBOOKSHELF
    
    override val displayName = "Audiobookshelf"
    
    override fun getCoverUrl(bookId: String): String = "$normalizedUrl/api/items/$bookId/cover"

    override fun supportsFeature(feature: ServiceFeature): Boolean {
        return when (feature) {
             ServiceFeature.AUDIOBOOK_PLAYBACK,
             ServiceFeature.EBOOK_READING,
             ServiceFeature.DOWNLOAD,
             ServiceFeature.PROGRESS_SYNC,
             ServiceFeature.COMIC_READING -> true
             else -> false
        }
    }
    
    override suspend fun downloadBook(bookId: String): Flow<DownloadProgress> {
         return flow { } // TODO: Implement if needed, or rely on DownloadService fetching URL
    }
    
    private fun normalizeUrl(url: String): String {
        val withProtocol = if (!url.startsWith("http")) {
            val isPrivate = if (url.startsWith("localhost") ||
                url.startsWith("127.0.0.1") ||
                url.startsWith("192.168.") ||
                url.startsWith("10.")) {
                true
            } else if (url.startsWith("172.")) {
                val parts = url.split('.')
                if (parts.size >= 2) {
                    val second = parts[1].toIntOrNull()
                    second != null && second in 16..31
                } else {
                    false
                }
            } else {
                false
            }

            if (isPrivate) {
                "http://$url"
            } else {
                "https://$url"
            }
        } else url
        return if (withProtocol.endsWith("/")) withProtocol.dropLast(1) else withProtocol
    }

    // Extension functions to convert ABS models to service models
    
    private fun AbsLibraryItem.toServiceBook(): ServiceBook {
        val metadata = media.metadata
        val progress = userMediaProgress
        
        return ServiceBook(
            serviceType = ServiceType.AUDIOBOOKSHELF,
            serviceId = id,
            title = metadata?.title ?: "Unknown",
            authors = metadata?.authors?.map { it.name } 
                ?: listOfNotNull(metadata?.authorName),
            narrators = metadata?.narrators 
                ?: listOfNotNull(metadata?.narratorName),
            description = metadata?.description,
            coverUrl = if (media.coverPath != null) {
                "$normalizedUrl/api/items/$id/cover"
            } else null,
            duration = media.duration?.times(1000)?.toLong(),
            hasAudiobook = (media.numAudioFiles ?: 0) > 0,
            hasEbook = media.ebookFile != null,
            hasReadAloud = false, // ABS doesn't support read-aloud
            series = metadata?.series?.firstOrNull()?.name 
                ?: metadata?.seriesName,
            seriesIndex = metadata?.series?.firstOrNull()?.sequence?.toFloatOrNull(),
            publishedYear = metadata?.publishedYear?.toIntOrNull(),
            isbn = metadata?.isbn,
            asin = metadata?.asin
        )
    }
    
    private fun AbsMediaProgress.toServiceProgress(bookId: String): ReadingProgress {
        return ReadingProgress(
            bookId = bookId,
            currentPosition = (currentTime * 1000).toLong(),
            percentComplete = (progress * 100).toFloat(),
            currentChapter = 0,
            isFinished = isFinished,
            lastUpdated = lastUpdate
        )
    }
}
