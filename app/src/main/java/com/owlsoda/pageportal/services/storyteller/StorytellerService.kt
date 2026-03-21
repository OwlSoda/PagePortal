package com.owlsoda.pageportal.services.storyteller

import android.util.Log
import com.owlsoda.pageportal.services.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Implementation of BookService for Storyteller servers.
 * Wraps StorytellerApi and maps responses to unified models.
 */
class StorytellerService(
    private val baseOkHttpClient: OkHttpClient
) : BookService {

    companion object {
        private const val TAG = "StorytellerService"
    }
    
    override val serviceType: ServiceType = ServiceType.STORYTELLER
    override val displayName: String = "Storyteller"
    
    private var api: StorytellerApi? = null
    private var baseUrl: String? = null
    private var authToken: String? = null
    
    /**
     * Configure the service with server URL and auth token.
     */
    fun configure(serverUrl: String, authToken: String?) {
        val cleanUrl = ServiceManager.normalizeUrl(serverUrl)
        this.baseUrl = if (cleanUrl.endsWith("/")) cleanUrl else "$cleanUrl/"
        this.authToken = authToken
        
        android.util.Log.d("StorytellerService", "Configuring with URL: $baseUrl, Token present: ${authToken != null}")
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl!!) // Use non-null assertion as baseUrl is set above
            .client(baseOkHttpClient) // Use baseOkHttpClient as per original context
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        this.api = retrofit.create(StorytellerApi::class.java)
    }

    suspend fun authenticateWithToken(serverUrl: String, token: String): AuthResult {
        return try {
            configure(serverUrl, token)
            // Validate by fetching current user
            val api = this.api ?: throw Exception("Service not configured")
            val user = api.getCurrentUser()
            
            AuthResult(
                success = true,
                token = token,
                userId = user.id,
                username = user.name,
                errorMessage = null
            )
        } catch (e: Exception) {
            val message = when {
                e is java.net.UnknownHostException -> "Could not find server. Check the URL."
                e is javax.net.ssl.SSLHandshakeException -> "SSL Error. Try using HTTP if HTTPS is not configured correctly."
                e.message?.contains("401") == true -> "Session expired or invalid token."
                else -> "Token validation failed: ${e.message}"
            }
            AuthResult(success = false, errorMessage = message)
        }
    }
    
    override suspend fun authenticate(serverUrl: String, username: String, password: String): AuthResult {
        return try {
            val cleanUrl = ServiceManager.normalizeUrl(serverUrl)
            
            // Create temporary client for login with generous timeouts
            val tempClient = baseOkHttpClient.newBuilder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
            
            val tempApi = Retrofit.Builder()
                .baseUrl("$cleanUrl/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(tempClient)
                .build()
                .create(StorytellerApi::class.java)
            
            val textMediaType = "text/plain".toMediaType()
            val response = tempApi.login(
                username = username.toRequestBody(textMediaType),
                password = password.toRequestBody(textMediaType)
            )
            
            // Configure with the new token
            configure(serverUrl, response.accessToken)
            
            AuthResult(
                success = true,
                token = response.accessToken,
                userId = null  // Storyteller doesn't return user ID in token response
            )
        } catch (e: Exception) {
            android.util.Log.e("StorytellerService", "Authentication failed for $serverUrl", e)
            val message = when {
                e is java.net.UnknownHostException -> "Could not find server. Check the URL and your connection."
                e is java.net.ConnectException -> "Connection refused. Is the server running and accessible?"
                e is java.net.SocketTimeoutException -> "Connection timed out. The server is taking too long to respond."
                e is javax.net.ssl.SSLHandshakeException -> "SSL Error. If your server uses a self-signed certificate, you might need to use HTTP or add the certificate to Android."
                e.message?.contains("401") == true -> "Invalid username or password."
                e.message?.contains("404") == true -> "Storyteller API not found. Is the URL correct?"
                else -> e.message ?: "Authentication failed"
            }
            AuthResult(
                success = false,
                errorMessage = message
            )
        }
    }
    
    override suspend fun listBooks(page: Int, pageSize: Int): List<ServiceBook> {
        if (page > 0) return emptyList() 
        return try {
            android.util.Log.d("StorytellerService", "Fetching books from Storyteller API... URL: $baseUrl, Token present: ${authToken != null}")
            val response = getApi().listBooks(synced = null)
            android.util.Log.d("StorytellerService", "Received ${response.size} books from Storyteller")
            
            response.mapNotNull { it.toServiceBook() }
        } catch (e: Exception) {
            android.util.Log.e("StorytellerService", "Failed to fetch books from Storyteller. URL: $baseUrl, Token present: ${authToken != null}", e)
            emptyList()
        }
    }
    
    override suspend fun getBookDetails(bookId: String): ServiceBookDetails {
        val response = getApi().getBookDetails(bookId)
        val position = try {
            getApi().getPosition(bookId)
        } catch (e: Exception) {
            null
        }
        
                // Helper to construct URL from path (handling spaces)
        fun getUrl(path: String?): String? {
             if (path.isNullOrBlank()) return null
             if (path.startsWith("http")) return path
             val cleanBase = baseUrl?.trimEnd('/') ?: return null
             val cleanPath = path.trimStart('/')
             // Encode path to handle spaces
             val encodedPath = cleanPath.split("/").joinToString("/") { segment ->
                 java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
             }
             return "$cleanBase/$encodedPath"
        }
        
        // Debug duration reporting
        val locDuration = position?.locator?.locations?.totalDurationMs
        Log.d(TAG, "Duration Info for $bookId: locDurationMs=$locDuration")

        val durationSeconds = extractDuration(response) ?: locDuration?.let { it / 1000 }
        
        val serviceBook = response.toServiceBook() ?: throw Exception("Failed to map book details for $bookId")
        
        return ServiceBookDetails(
            book = serviceBook.copy(duration = durationSeconds),
            chapters = emptyList(),  // Storyteller doesn't expose chapter list in main API
            files = buildList {
                // Ebook
                response.ebook?.let {
                    // Always use API endpoint for downloads to avoid 404s on folder paths
                    val url = getEbookDownloadUrl(bookId)
                    add(BookFile(
                        id = it.uuid,
                        filename = it.filepath?.substringAfterLast('/') ?: "ebook.epub",
                        mimeType = "application/epub+zip",
                        size = 0,
                        downloadUrl = url
                    ))
                }
                // Audiobook
                response.audiobook?.let {
                    // Always use API endpoint for downloads
                    val url = getAudiobookDownloadUrl(bookId)
                    add(BookFile(
                        id = it.uuid,
                        filename = it.filepath?.substringAfterLast('/') ?: "audiobook.m4b",
                        mimeType = "audio/mp4",
                        size = 0,
                        downloadUrl = url
                    ))
                }
                // ReadAloud
                response.readaloud?.let {
                    Log.d(TAG, "Book $bookId ReadAloud status: ${it.status}")
                    val isAvailable = true
                    if (isAvailable) {
                        // Always use API endpoint for downloads
                        val url = getSyncDownloadUrl(bookId)
                        add(BookFile(
                            id = it.uuid,
                            filename = it.filepath?.substringAfterLast('/') ?: "readaloud.zip",
                            mimeType = "application/zip",
                            size = 0,
                            downloadUrl = url
                        ))
                    }
                }
            },
            totalDuration = durationSeconds,
            lastProgress = position?.toReadingProgress(bookId)
        )
    }
    
    override suspend fun getProgress(bookId: String): ReadingProgress? {
        return try {
            getApi().getPosition(bookId)?.toReadingProgress(bookId)
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun updateProgress(bookId: String, progress: ReadingProgress) {
        val position = Position(
            locator = Locator(
                href = "",
                mediaType = "application/xhtml+xml",
                locations = Locations(
                    progression = progress.percentComplete.toDouble() / 100.0,
                    position = progress.currentChapter,
                    totalProgression = progress.percentComplete.toDouble() / 100.0,
                    audioTimestampMs = progress.currentPosition
                )
            ),
            timestamp = progress.lastUpdated
        )
        getApi().updatePosition(bookId, position)
    }

    override suspend fun updateMetadata(bookId: String, metadata: MetadataUpdate): Result<ServiceBook> {
        return try {
            val api = getApi()
            
            // Phase 1: Update metadata via JSON PUT
            val metadataRequest = MetadataRequest(
                title = metadata.title,
                description = metadata.description,
                seriesName = metadata.series,
                seriesIndex = metadata.seriesIndex,
                creatorNames = metadata.authors,
                tagNames = metadata.tags
            )
            
            var response = api.updateMetadataJson(bookId, metadataRequest)
            
            // Phase 2: Update cover if provided
            metadata.coverImage?.let { imageBytes ->
                val mimeType = metadata.coverMimeType ?: "image/jpeg"
                val requestBody = imageBytes.toRequestBody(mimeType.toMediaType())
                val coverPart = MultipartBody.Part.createFormData("cover", "cover.jpg", requestBody)
                response = api.updateCover(bookId, coverPart)
            }

            response.toServiceBook()?.let { Result.success(it) } 
                ?: Result.failure(Exception("Failed to map updated book metadata"))
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            Log.e(TAG, "Server error (${e.code()}): $errorBody", e)
            Result.failure(Exception("Server error (${e.code()}): $errorBody", e))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update metadata for book $bookId", e)
            Result.failure(e)
        }
    }
    
    override suspend fun downloadBook(bookId: String): Flow<DownloadProgress> {
        // This will be implemented by DownloadService
        return flow {
            emit(DownloadProgress(
                bookId = bookId,
                bytesDownloaded = 0,
                totalBytes = 0,
                status = DownloadStatus.QUEUED
            ))
        }
    }
    
    override fun getCoverUrl(bookId: String): String {
        val base = baseUrl?.trimEnd('/') ?: return ""
        return "$base/api/v2/books/$bookId/cover"
    }
    
    override fun supportsFeature(feature: ServiceFeature): Boolean {
        return when (feature) {
            ServiceFeature.EBOOK_READING -> true
            ServiceFeature.AUDIOBOOK_PLAYBACK -> true
            ServiceFeature.READALOUD_SYNC -> true
            ServiceFeature.DOWNLOAD -> true
            ServiceFeature.PROGRESS_SYNC -> true
            ServiceFeature.METADATA_EDIT -> true
            ServiceFeature.COMIC_READING -> false // Storyteller doesn't support comics yet
        }
    }
    
    // URL helpers
    fun getEbookDownloadUrl(bookId: String): String {
        val base = baseUrl?.trimEnd('/') ?: return ""
        val encodedToken = java.net.URLEncoder.encode(authToken ?: "", "UTF-8")
        return "$base/api/v2/books/$bookId/files?format=ebook&token=$encodedToken"
    }
    
    fun getAudiobookDownloadUrl(bookId: String): String {
        val base = baseUrl?.trimEnd('/') ?: return ""
        val encodedToken = java.net.URLEncoder.encode(authToken ?: "", "UTF-8")
        return "$base/api/v2/books/$bookId/files?format=audiobook&token=$encodedToken"
    }
    
    fun getSyncDownloadUrl(bookId: String): String {
        val base = baseUrl?.trimEnd('/') ?: return ""
        val encodedToken = java.net.URLEncoder.encode(authToken ?: "", "UTF-8")
        return "$base/api/v2/books/$bookId/files?format=readaloud&token=$encodedToken"
    }

    suspend fun triggerReadAloudProcessing(bookId: String): Result<Unit> {
        return try {
            getApi().processBook(bookId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger ReadAloud processing for $bookId", e)
            Result.failure(e)
        }
    }
    
    fun getWebReaderUrl(bookId: String): String {
        val base = baseUrl?.trimEnd('/') ?: return ""
        val token = authToken ?: ""
        // Storyteller v2 web reader usually lives at /books/{uuid} in the web UI.
        // We append the token for auto-login if the server supports it,
        // or we'll handle cookie injection in the WebView.
        return "$base/books/$bookId?token=$token"
    }
    
    private fun getApi(): StorytellerApi {
        return api ?: throw IllegalStateException("StorytellerService not configured. Call configure() first.")
    }
    
    private fun normalizeUrl(url: String): String {
        return ServiceManager.normalizeUrl(url)
    }
    
    // Mapping extensions
    private fun BookResponse.toServiceBook(): ServiceBook? {
        return try {
            ServiceBook(
                serviceType = ServiceType.STORYTELLER,
                serviceId = uuid,
                title = title,
                authors = authors?.map { it.name } ?: emptyList(),
                narrators = narrators?.map { it.name } ?: emptyList(),
                series = series?.firstOrNull()?.name,
                seriesIndex = series?.firstOrNull()?.seriesIndex?.toFloatOrNull(),
                coverUrl = getCoverUrl(uuid),
                audiobookCoverUrl = if (audiobook != null) "${getCoverUrl(uuid)}?format=square" else null,
                hasEbook = ebook != null,
                hasAudiobook = audiobook != null,
                hasReadAloud = readaloud != null && (readaloud.status == "completed" || readaloud.status == "ready" || !readaloud.filepath.isNullOrBlank()),
                description = description,
                duration = extractDuration(this),
                publishedYear = publicationDate?.take(4)?.toIntOrNull(),
                tags = tags?.map { it.name } ?: emptyList(),
                collections = collections?.map { CollectionRef(it.uuid, it.name) } ?: emptyList()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to map book: $title ($uuid)", e)
            null
        }
    }
    
    private fun extractDuration(book: BookResponse): Long? {
        // Priority 1: totalDurationMs (ms -> s)
        book.totalDurationMs?.let { if (it > 0) return it / 1000 }
        
        // Priority 2: total_duration/duration (s)
        book.totalDuration?.let { if (it > 0) return it.toLong() }
        book.duration?.let { if (it > 0) return it.toLong() }
        
        // Priority 3: audiobook level
        book.audiobook?.let { ab ->
            ab.totalDurationMs?.let { if (it > 0) return it / 1000 }
            ab.totalDuration?.let { if (it > 0) return it.toLong() }
            ab.duration?.let { if (it > 0) return it.toLong() }
        }
        
        return null
    }
    
    private fun Position.toReadingProgress(bookId: String): ReadingProgress {
        return ReadingProgress(
            bookId = bookId,
            currentPosition = locator.locations.audioTimestampMs ?: 0,
            currentChapter = locator.locations.position ?: 0,
            percentComplete = (((locator.locations.totalProgression ?: locator.locations.progression ?: 0.0) * 100).toFloat()),
            lastUpdated = timestamp
        )
    }
}
