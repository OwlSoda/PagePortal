package com.owlsoda.pageportal.services.storyteller

import com.owlsoda.pageportal.services.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
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
    
    override val serviceType: ServiceType = ServiceType.STORYTELLER
    override val displayName: String = "Storyteller"
    
    private var api: StorytellerApi? = null
    private var baseUrl: String? = null
    private var authToken: String? = null
    
    /**
     * Configure the service with server URL and auth token.
     */
    fun configure(serverUrl: String, token: String?) {
        val cleanUrl = normalizeUrl(serverUrl)
        
        if (cleanUrl == baseUrl && token == authToken && api != null) return
        
        baseUrl = cleanUrl
        authToken = token
        
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder().apply {
                token?.let { addHeader("Authorization", "Bearer $it") }
            }.build()
            chain.proceed(request)
        }
        
        val client = baseOkHttpClient.newBuilder()
            .addInterceptor(authInterceptor)
            .build()
        
        api = Retrofit.Builder()
            .baseUrl("$cleanUrl/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(StorytellerApi::class.java)
    }
    
    override suspend fun authenticate(serverUrl: String, username: String, password: String): AuthResult {
        return try {
            val cleanUrl = normalizeUrl(serverUrl)
            
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
            AuthResult(
                success = false,
                errorMessage = e.message ?: "Authentication failed"
            )
        }
    }
    
    override suspend fun listBooks(page: Int, pageSize: Int): List<ServiceBook> {
        val response = getApi().listBooks(synced = true)
        return response.map { it.toServiceBook() }
    }
    
    override suspend fun getBookDetails(bookId: String): ServiceBookDetails {
        val response = getApi().getBookDetails(bookId)
        val position = try {
            getApi().getPosition(bookId)
        } catch (e: Exception) {
            null
        }
        
        return ServiceBookDetails(
            book = response.toServiceBook(),
            chapters = emptyList(),  // Storyteller doesn't expose chapter list in main API
            files = buildList {
                response.ebook?.let {
                    add(BookFile(
                        id = it.uuid,
                        filename = it.filepath ?: "ebook.epub",
                        mimeType = "application/epub+zip",
                        size = 0,
                        downloadUrl = getEbookDownloadUrl(bookId)
                    ))
                }
                response.audiobook?.let {
                    add(BookFile(
                        id = it.uuid,
                        filename = it.filepath ?: "audiobook.m4b",
                        mimeType = "audio/mp4",
                        size = 0,
                        downloadUrl = getAudiobookDownloadUrl(bookId)
                    ))
                }
                response.readaloud?.let {
                    if (it.status == "completed") {
                        add(BookFile(
                            id = it.uuid,
                            filename = it.filepath ?: "readaloud.zip",
                            mimeType = "application/zip",
                            size = 0,
                            downloadUrl = getSyncDownloadUrl(bookId)
                        ))
                    }
                }
            },
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
        return "${baseUrl}/api/v2/books/$bookId/cover"
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
    fun getEbookDownloadUrl(bookId: String): String = "${baseUrl}/api/v2/books/$bookId/files?format=ebook"
    fun getAudiobookDownloadUrl(bookId: String): String = "${baseUrl}/api/v2/books/$bookId/files?format=audiobook"
    fun getSyncDownloadUrl(bookId: String): String = "${baseUrl}/api/v2/books/$bookId/files?format=readaloud"
    
    private fun getApi(): StorytellerApi {
        return api ?: throw IllegalStateException("StorytellerService not configured. Call configure() first.")
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
    
    // Mapping extensions
    private fun BookResponse.toServiceBook(): ServiceBook {
        return ServiceBook(
            serviceType = ServiceType.STORYTELLER,
            serviceId = uuid,
            title = title,
            authors = authors.map { it.name },
            narrators = narrators?.map { it.name } ?: emptyList(),
            series = series?.firstOrNull()?.name,
            seriesIndex = series?.firstOrNull()?.seriesIndex?.toFloatOrNull(),
            coverUrl = getCoverUrl(uuid),
            hasEbook = ebook != null,
            hasAudiobook = audiobook != null,
            hasReadAloud = readaloud?.status == "completed",
            description = description,
            publishedYear = publicationDate?.take(4)?.toIntOrNull()
        )
    }
    
    private fun Position.toReadingProgress(bookId: String): ReadingProgress {
        return ReadingProgress(
            bookId = bookId,
            currentPosition = locator.locations.audioTimestampMs ?: 0,
            currentChapter = locator.locations.position ?: 0,
            percentComplete = ((locator.locations.totalProgression ?: locator.locations.progression) * 100).toFloat(),
            lastUpdated = timestamp
        )
    }
}
