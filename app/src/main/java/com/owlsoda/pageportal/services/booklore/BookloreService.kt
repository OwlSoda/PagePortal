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
        val cleanUrl = ServiceManager.normalizeUrl(serverUrl)
        this.baseUrl = cleanUrl
        this.authToken = authToken // Store locally, but AuthInterceptor reads from DB
        
        // Use global client - AuthInterceptor handles injection
        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl(if (cleanUrl.endsWith("/")) cleanUrl else "$cleanUrl/")
            .client(client)
            .build()
        
        this.api = retrofit.create(OpdsApi::class.java)
    }

    override suspend fun authenticate(serverUrl: String, username: String, password: String): AuthResult {
        return try {
            val isOidc = username == "OIDC User"
            val token = if (isOidc) "Bearer $password" else okhttp3.Credentials.basic(username, password)
            var cleanUrl = ServiceManager.normalizeUrl(serverUrl)
            
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
            
            // Helper function to try authentication with a specific URL
            suspend fun tryAuthenticate(url: String): Boolean {
                val tempRetrofit = retrofit2.Retrofit.Builder()
                    .baseUrl(if (url.endsWith("/")) url else "$url/")
                    .client(tempClient)
                    .build()
                
                val tempApi = tempRetrofit.create(OpdsApi::class.java)
                
                return try {
                    val response = tempApi.getFeed(url)
                    val bodyString = response.string()
                    
                    // Basic check: Does it look like XML?
                    if (bodyString.trim().startsWith("<") && !bodyString.trim().startsWith("<!")) {
                        OpdsParser().parse(bodyString.byteInputStream(), url)
                        true
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }
            }
            
            // Try common OPDS paths
            val pathsToTry = listOf("", "/opds", "/opds/v1.2", "/feed.xml")
            var success = false
            var finalUrl = cleanUrl
            
            for (path in pathsToTry) {
                val tryUrl = if (path.isEmpty()) cleanUrl else {
                    if (cleanUrl.endsWith("/")) cleanUrl + path.substring(1) else cleanUrl + path
                }
                if (tryAuthenticate(tryUrl)) {
                    success = true
                    finalUrl = tryUrl
                    break
                }
            }
            
            if (!success) {
                return AuthResult(
                    success = false, 
                    errorMessage = "Could not find a valid OPDS feed at $cleanUrl or common sub-paths. " +
                            "Please ensure Booklore is running and OPDS is enabled in settings."
                )
            }
            
            // If successful, configure the service
            configure(finalUrl, token)
            
            AuthResult(success = true, token = token, userId = username)
        } catch (e: Exception) {
            android.util.Log.e("BookloreService", "Authentication failed for $serverUrl", e)
            val message = when {
                e is java.net.UnknownHostException -> "Could not find server. Check the URL."
                e is java.net.ConnectException -> "Connection refused. Is the server running?"
                e is java.net.SocketTimeoutException -> "Connection timed out."
                e is javax.net.ssl.SSLHandshakeException -> "SSL Error. Try using HTTP."
                e is retrofit2.HttpException && e.code() == 401 -> "Invalid username or password."
                e is retrofit2.HttpException && e.code() == 404 -> "OPDS feed not found at this URL."
                else -> e.message ?: "Authentication failed"
            }
            AuthResult(
                success = false,
                errorMessage = message
            )
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
                    filename = "${entry.title}.${getExtension(entry.format, entry.mimeType)}",
                    mimeType = entry.mimeType ?: "application/octet-stream",
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
    
    private fun getExtension(format: MediaFormat, mimeType: String? = null): String {
        return when (format) {
            MediaFormat.AUDIOBOOK -> when {
                mimeType?.contains("mp4") == true || mimeType?.contains("m4a") == true || mimeType?.contains("m4b") == true -> "m4b"
                mimeType?.contains("mpeg") == true || mimeType?.contains("mp3") == true -> "mp3"
                mimeType?.contains("ogg") == true -> "ogg"
                mimeType?.contains("flac") == true -> "flac"
                else -> "m4b" // Default fallback
            }
            MediaFormat.EBOOK -> "epub"
            MediaFormat.PDF -> "pdf"
            MediaFormat.COMIC -> "cbz"
            else -> "bin"
        }
    }
}
