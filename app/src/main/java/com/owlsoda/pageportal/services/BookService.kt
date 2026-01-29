package com.owlsoda.pageportal.services

import kotlinx.coroutines.flow.Flow

/**
 * Common interface for all book services (Storyteller, Audiobookshelf, Booklore).
 * Each service implements this interface to provide a unified API.
 */
interface BookService {
    /**
     * The type of service this implementation represents.
     */
    val serviceType: ServiceType
    
    /**
     * Human-readable name for display in UI.
     */
    val displayName: String
    
    /**
     * Authenticate with the service.
     * @return AuthResult indicating success/failure and any tokens.
     */
    suspend fun authenticate(serverUrl: String, username: String, password: String): AuthResult
    
    /**
     * List all books from this service.
     * @param page Pagination page number (0-indexed)
     * @param pageSize Number of items per page
     */
    suspend fun listBooks(page: Int = 0, pageSize: Int = 50): List<ServiceBook>
    
    /**
     * Get detailed information about a specific book.
     */
    suspend fun getBookDetails(bookId: String): ServiceBookDetails
    
    /**
     * Get the user's reading/listening progress for a book.
     */
    suspend fun getProgress(bookId: String): ReadingProgress?
    
    /**
     * Update reading/listening progress.
     */
    suspend fun updateProgress(bookId: String, progress: ReadingProgress)
    
    /**
     * Download a book for offline access.
     * @return Flow emitting download progress updates.
     */
    suspend fun downloadBook(bookId: String): Flow<DownloadProgress>
    
    /**
     * Get the cover image URL for a book.
     */
    fun getCoverUrl(bookId: String): String
    
    /**
     * Check if this service supports a specific feature.
     */
    fun supportsFeature(feature: ServiceFeature): Boolean
}

enum class ServiceType {
    STORYTELLER,
    AUDIOBOOKSHELF,
    BOOKLORE
}

enum class ServiceFeature {
    EBOOK_READING,
    AUDIOBOOK_PLAYBACK,
    READALOUD_SYNC,
    DOWNLOAD,
    PROGRESS_SYNC,
    METADATA_EDIT,
    COMIC_READING  // CBZ/CBR support
}

enum class MediaFormat {
    AUDIOBOOK,      // M4B, MP3, etc.
    EBOOK,          // EPUB
    COMIC,          // CBZ, CBR
    PDF,
    READALOUD       // EPUB with SMIL sync
}

data class AuthResult(
    val success: Boolean,
    val token: String? = null,
    val userId: String? = null,
    val errorMessage: String? = null
)

data class ServiceBook(
    val serviceType: ServiceType,
    val serviceId: String,
    val title: String,
    val authors: List<String>,
    val narrators: List<String> = emptyList(),
    val isbn: String? = null,
    val asin: String? = null,
    val series: String? = null,
    val seriesIndex: Float? = null,
    val coverUrl: String? = null,
    val hasEbook: Boolean = false,
    val hasAudiobook: Boolean = false,
    val hasReadAloud: Boolean = false,
    val duration: Long? = null,  // audiobook duration in seconds
    val publishedYear: Int? = null,
    val description: String? = null
)

data class ServiceBookDetails(
    val book: ServiceBook,
    val chapters: List<Chapter> = emptyList(),
    val files: List<BookFile> = emptyList(),
    val totalDuration: Long? = null,
    val lastProgress: ReadingProgress? = null
)

data class Chapter(
    val id: String,
    val title: String,
    val startTime: Long = 0,  // for audiobooks
    val duration: Long = 0,
    val pageStart: Int = 0,   // for ebooks
    val pageEnd: Int = 0
)

data class BookFile(
    val id: String,
    val filename: String,
    val mimeType: String,
    val size: Long,
    val downloadUrl: String
)

data class ReadingProgress(
    val bookId: String,
    val currentPosition: Long = 0,  // milliseconds for audio, position for ebook
    val currentChapter: Int = 0,
    val percentComplete: Float = 0f,
    val isFinished: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class DownloadProgress(
    val bookId: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: DownloadStatus
) {
    val progress: Float get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}
