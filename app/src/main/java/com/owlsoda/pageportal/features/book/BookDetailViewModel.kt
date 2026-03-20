package com.owlsoda.pageportal.features.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.dao.ProgressDao
import com.owlsoda.pageportal.core.database.dao.ServerDao
import com.owlsoda.pageportal.core.database.dao.UnifiedBookDao
import com.owlsoda.pageportal.core.database.entity.BookEntity
import com.owlsoda.pageportal.services.ServiceManager
import com.owlsoda.pageportal.data.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookDetailState(
    val book: BookEntity? = null,
    val progressPercent: Float = 0f,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val isLoading: Boolean = true,
    val error: String? = null,
    val webReaderUrl: String? = null,
    val lastSyncAt: Long? = null
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val unifiedBookDao: UnifiedBookDao,
    private val progressDao: ProgressDao,
    private val serverDao: ServerDao,
    private val serviceManager: ServiceManager,
    private val libraryRepository: com.owlsoda.pageportal.data.repository.LibraryRepository,
    private val downloadRepository: com.owlsoda.pageportal.data.repository.DownloadRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {
    
    val isSyncing = syncRepository.isSyncing
    
    private val _state = MutableStateFlow(BookDetailState())
    val state: StateFlow<BookDetailState> = _state.asStateFlow()
    
    private var currentBookId: String? = null
    // Keep track of all linked books for this unified entry
    private var linkedBooks: List<BookEntity> = emptyList()
    
    private var downloadJob: kotlinx.coroutines.Job? = null
    private var progressJob: kotlinx.coroutines.Job? = null
    
    fun loadBook(bookId: String) {
        if (currentBookId == bookId) return
        currentBookId = bookId
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            try {
                if (bookId.startsWith("u_")) {
                    // Unified Book
                    val unifiedId = bookId.removePrefix("u_").toLongOrNull() ?: return@launch
                    val unified = unifiedBookDao.getUnifiedBookWithBooksById(unifiedId)
                    
                    if (unified == null || unified.books.isEmpty()) {
                         _state.value = _state.value.copy(isLoading = false, error = "Unified book not found")
                         return@launch
                    }
                    
                    linkedBooks = unified.books
                    
                    // Synthesize display book
                    val base = linkedBooks.first()
                    val displayBook = base.copy(
                        title = unified.unifiedBook.title,
                        authors = unified.unifiedBook.author,
                        coverUrl = unified.unifiedBook.coverUrl ?: base.coverUrl,
                        audiobookCoverUrl = unified.unifiedBook.audiobookCoverUrl ?: base.audiobookCoverUrl,
                        description = unified.unifiedBook.description ?: base.description,
                        hasEbook = linkedBooks.any { it.hasEbook },
                        hasAudiobook = linkedBooks.any { it.hasAudiobook },
                        hasReadAloud = linkedBooks.any { it.hasReadAloud }
                    )
                    
                    val progress = progressDao.getProgressByBookId(base.id)
                    
                    observeDownloadStatus(base.id)
                    observeProgress(base.id)
                    observeBookMetadata(base.id)
                    
                    _state.value = _state.value.copy(
                        book = displayBook,
                        progressPercent = progress?.percentComplete ?: 0f,
                        webReaderUrl = getWebReaderUrl(base),
                        lastSyncAt = base.lastSyncAt,
                        isLoading = false
                    )

                    // Start automatic sync
                    viewModelScope.launch {
                        syncRepository.syncProgress(base.id)
                    }
                    
                } else {
                    // Legacy/Direct Book ID
                    val idLong = bookId.toLongOrNull()
                    val book = if (idLong != null) bookDao.getBookById(idLong) else null
                    
                    if (book == null) {
                        _state.value = _state.value.copy(isLoading = false, error = "Book not found")
                        return@launch
                    }
                    
                    linkedBooks = listOf(book)
                    
                    observeDownloadStatus(book.id)
                    observeProgress(book.id)
                    observeBookMetadata(book.id)
                    
                    val progress = progressDao.getProgressByBookId(book.id)
                    
                    _state.value = _state.value.copy(
                        book = book,
                        progressPercent = progress?.percentComplete ?: 0f,
                        webReaderUrl = getWebReaderUrl(book),
                        lastSyncAt = book.lastSyncAt,
                        isLoading = false
                    )

                    // Start automatic sync
                    viewModelScope.launch {
                        syncRepository.syncProgress(book.id)
                    }
                }
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load book"
                )
            }
        }
    }
    
    private fun observeDownloadStatus(bookId: Long) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            bookDao.observeBook(bookId).collect { book ->
                if (book != null) {
                    val status = book.downloadStatus
                    _state.value = _state.value.copy(
                        isDownloading = status == "DOWNLOADING" || status == "QUEUED",
                        isDownloaded = status == "COMPLETED",
                        downloadProgress = book.downloadProgress
                    )
                    // Update display book if needed to reflect local path
                    if (book.localFilePath != _state.value.book?.localFilePath) {
                         // _state.value = _state.value.copy(book = book) // Careful with overwrite
                    }
                }
            }
        }
    }
    private fun observeProgress(bookId: Long) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            progressDao.observeProgressByBookId(bookId).collect { progress ->
                if (progress != null) {
                    _state.update { it.copy(progressPercent = progress.percentComplete) }
                }
            }
        }
    }

    private fun observeBookMetadata(bookId: Long) {
        viewModelScope.launch {
            bookDao.observeBook(bookId).collect { book ->
                if (book != null) {
                    _state.update { it.copy(lastSyncAt = book.lastSyncAt) }
                }
            }
        }
    }
    
    fun startDownload(type: String? = null) {
        // Find best candidate
        val target = if (type != null) {
             when(type) {
                 "audio" -> linkedBooks.firstOrNull { it.hasAudiobook }
                 "ebook" -> linkedBooks.firstOrNull { it.hasEbook }
                 "readaloud" -> linkedBooks.firstOrNull { it.hasReadAloud }
                 else -> linkedBooks.firstOrNull()
             }
        } else {
             linkedBooks.firstOrNull { it.hasAudiobook } 
             ?: linkedBooks.firstOrNull { it.hasEbook } 
             ?: linkedBooks.firstOrNull()
        } ?: return
        
        viewModelScope.launch {
            try {
                // If readaloud requested, also ensure ebook is downloaded
                if (type == "readaloud") {
                    val hasEbook = target.isEbookDownloaded || linkedBooks.any { it.isEbookDownloaded }
                    if (!hasEbook && target.hasEbook) {
                        downloadRepository.startDownload(target.id, target.serverId, target.serviceBookId, "ebook")
                    }
                }
                
                downloadRepository.startDownload(target.id, target.serverId, target.serviceBookId, type)
            } catch (e: Exception) {
                 _state.value = _state.value.copy(error = e.message)
            }
        }
    }
    
    fun cancelDownload() {
        // Cancel all linked
        viewModelScope.launch {
            linkedBooks.forEach { downloadRepository.cancelDownload(it.id) }
        }
    }
    
    fun deleteDownload(type: String) {
        viewModelScope.launch {
            linkedBooks.forEach { downloadRepository.deleteDownload(it.id, type) }
        }
    }
    
    fun unlinkBook() {
        viewModelScope.launch {
            try {
                linkedBooks.forEach { book ->
                    val updated = book.copy(
                        unifiedBookId = null,
                        isManuallyLinked = true
                    )
                    bookDao.updateBook(updated)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Failed to unlink: ${e.message}")
            }
        }
    }
    
    fun triggerReadAloudCreation() {
        val book = _state.value.book ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val result = libraryRepository.triggerReadAloud(book.id)
            if (result.isSuccess) {
                // Success - status will be updated via DAO observation
                _state.value = _state.value.copy(isLoading = false)
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to trigger ReadAloud creation"
                )
            }
        }
    }
    
    fun syncNow() {
        val book = _state.value.book ?: return
        viewModelScope.launch {
            syncRepository.syncProgress(book.id)
        }
    }

    private suspend fun getWebReaderUrl(book: BookEntity): String? {
        val service = serviceManager.getService(book.serverId)
        return if (service is com.owlsoda.pageportal.services.storyteller.StorytellerService) {
            service.getWebReaderUrl(book.serviceBookId)
        } else {
            null
        }
    }
}
