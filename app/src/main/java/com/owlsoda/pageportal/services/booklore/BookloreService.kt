package com.owlsoda.pageportal.services.booklore

import com.owlsoda.pageportal.services.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import java.util.UUID

class BookloreService(
    private val client: okhttp3.OkHttpClient
) : BookService {

    override val serviceType = ServiceType.BOOKLORE
    override val displayName = "Booklore (OPDS)"
    
    // In-memory cache to bridge listBooks and getBookDetails
    private val entryCache = mutableMapOf<String, OpdsParser.OpdsEntry>()
    
    private var api: OpdsApi? = null
    private var baseUrl: String = ""
    private var authToken: String? = null
    


    fun configure(serverUrl: String, authToken: String?) {
        this.baseUrl = serverUrl
        
        val authClient = if (!authToken.isNullOrBlank()) {
            client.newBuilder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    val request = original.newBuilder()
                        .header("Authorization", authToken)
                        .build()
                    chain.proceed(request)
                }
                .build()
        } else {
            client
        }
        
        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl(if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/")
            .client(authClient)
            .build()
        
        this.api = retrofit.create(OpdsApi::class.java)
    }

    override suspend fun authenticate(serverUrl: String, username: String, password: String): AuthResult {
        return try {
            val token = okhttp3.Credentials.basic(username, password)
            
            // Create temporary client with generous timeouts for authentication
            val tempClient = client.newBuilder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val original = chain.request()
                    val request = original.newBuilder()
                        .header("Authorization", token)
                        .build()
                    chain.proceed(request)
                }
                .build()
            
            // Create temporary API for validation
            val tempRetrofit = retrofit2.Retrofit.Builder()
                .baseUrl(if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/")
                .client(tempClient)
                .build()
            
            val tempApi = tempRetrofit.create(OpdsApi::class.java)
            
            // Try to fetch and parse the feed to validate credentials
            val response = tempApi.getFeed(serverUrl)
            OpdsParser().parse(response.byteStream(), serverUrl)
            
            // If successful, configure the service
            configure(serverUrl, token)
            
            AuthResult(success = true, token = token, userId = username)
        } catch (e: retrofit2.HttpException) {
            val message = when (e.code()) {
                401 -> "Invalid username or password"
                403 -> "Access forbidden"
                404 -> "OPDS feed not found at this URL"
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

    override suspend fun listBooks(page: Int, pageSize: Int): List<ServiceBook> {
        val currentApi = api ?: return emptyList()
        if (baseUrl.isEmpty()) return emptyList()
        
        try {
            val response = currentApi.getFeed(baseUrl)
            val entries = OpdsParser().parse(response.byteStream(), baseUrl)
            
            entries.forEach { entryCache[it.id] = it }
            
            return entries.map { entry ->
                ServiceBook(
                    serviceType = ServiceType.BOOKLORE,
                    serviceId = entry.id,
                    title = entry.title,
                    authors = listOf(entry.author),
                    description = entry.summary,
                    coverUrl = entry.coverUrl,
                    hasEbook = entry.format == MediaFormat.EBOOK || entry.format == MediaFormat.PDF,
                    hasAudiobook = entry.format == MediaFormat.AUDIOBOOK,
                    hasReadAloud = entry.format == MediaFormat.READALOUD
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    override suspend fun getBookDetails(bookId: String): ServiceBookDetails {
        val entry = entryCache[bookId] ?: throw Exception("Book not found in cache. Sync library first.")
        
        val book = ServiceBook(
            serviceType = ServiceType.BOOKLORE,
            serviceId = entry.id,
            title = entry.title,
            authors = listOf(entry.author),
            description = entry.summary,
            coverUrl = entry.coverUrl,
            hasEbook = entry.format == MediaFormat.EBOOK || entry.format == MediaFormat.PDF,
            hasAudiobook = entry.format == MediaFormat.AUDIOBOOK,
        )
        
        val files = if (entry.downloadUrl != null) {
            listOf(
                BookFile(
                    id = "mainfile",
                    filename = "${entry.title}.${getExtension(entry.format)}",
                    mimeType = "application/octet-stream",
                    size = 0,
                    downloadUrl = entry.downloadUrl
                )
            )
        } else emptyList()

        return ServiceBookDetails(
            book = book,
            files = files,
            chapters = emptyList()
        )
    }

    override suspend fun getProgress(bookId: String): ReadingProgress? = null

    override suspend fun updateProgress(bookId: String, progress: ReadingProgress) {}

    override suspend fun downloadBook(bookId: String): Flow<DownloadProgress> {
        return flow { emit(DownloadProgress(bookId, 0, 0, DownloadStatus.FAILED)) }
    }

    override fun getCoverUrl(bookId: String): String {
        return entryCache[bookId]?.coverUrl ?: ""
    }

    override fun supportsFeature(feature: ServiceFeature): Boolean {
        return when (feature) {
            ServiceFeature.EBOOK_READING, 
            ServiceFeature.AUDIOBOOK_PLAYBACK,
            ServiceFeature.DOWNLOAD,
            ServiceFeature.COMIC_READING -> true
            else -> false
        }
    }
    
    private fun getExtension(format: MediaFormat): String {
        return when (format) {
            MediaFormat.AUDIOBOOK -> "m4b"
            MediaFormat.EBOOK -> "epub"
            MediaFormat.PDF -> "pdf"
            MediaFormat.COMIC -> "cbz"
            else -> "bin"
        }
    }
}
