package com.owlsoda.pageportal.features.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.dao.ProgressDao
import com.owlsoda.pageportal.core.database.dao.ServerDao
import com.owlsoda.pageportal.core.database.dao.UnifiedBookDao
import com.owlsoda.pageportal.core.database.entity.BookEntity
import com.owlsoda.pageportal.download.DownloadService
import com.owlsoda.pageportal.services.ServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookDetailState(
    val book: BookEntity? = null,
    val progressPercent: Float = 0f,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val unifiedBookDao: UnifiedBookDao,
    private val progressDao: ProgressDao,
    private val serverDao: ServerDao,
    private val serviceManager: ServiceManager
) : ViewModel() {
    
    private val _state = MutableStateFlow(BookDetailState())
    val state: StateFlow<BookDetailState> = _state.asStateFlow()
    
    private var currentBookId: String? = null
    // Keep track of all linked books for this unified entry
    private var linkedBooks: List<BookEntity> = emptyList()
    
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
                    
                    // Synthesize display book (use first as base, but combine flags)
                    val base = linkedBooks.first()
                    val displayBook = base.copy(
                        title = unified.unifiedBook.title,
                        authors = unified.unifiedBook.author, // Unified uses display string
                        coverUrl = unified.unifiedBook.coverUrl ?: base.coverUrl,
                        description = unified.unifiedBook.description ?: base.description,
                        hasEbook = linkedBooks.any { it.hasEbook },
                        hasAudiobook = linkedBooks.any { it.hasAudiobook },
                        hasReadAloud = linkedBooks.any { it.hasReadAloud }
                    )
                    
                    // Load progress (use best progress from linked books?)
                    val progress = progressDao.getProgressByBookId(base.id)
                    
                    _state.value = _state.value.copy(
                        book = displayBook,
                        progressPercent = progress?.percentComplete ?: 0f,
                        isLoading = false
                    )
                    
                    // Check downloads for ALL linked books
                    linkedBooks.forEach { observeDownloadProgress(it.serviceBookId) }
                    
                } else {
                    // Legacy/Direct Book ID
                    val idLong = bookId.toLongOrNull()
                    val book = if (idLong != null) bookDao.getBookById(idLong) else null
                    
                    if (book == null) {
                        _state.value = _state.value.copy(isLoading = false, error = "Book not found")
                        return@launch
                    }
                    
                    linkedBooks = listOf(book)
                    val progress = progressDao.getProgressByBookId(book.id)
                    
                    _state.value = _state.value.copy(
                        book = book,
                        progressPercent = progress?.percentComplete ?: 0f,
                        isLoading = false
                    )
                    observeDownloadProgress(book.serviceBookId)
                }
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load book"
                )
            }
        }
    }
    
    private fun observeDownloadProgress(realBookId: String) {
        viewModelScope.launch {
            DownloadService.activeDownloads.collect { downloads ->
                val download = downloads.find { it.bookId == realBookId }
                if (download != null) {
                     _state.value = _state.value.copy(
                        isDownloading = !download.isCompleted && !download.isFailed,
                        downloadProgress = download.progress,
                        isDownloaded = download.isCompleted
                    )
                }
            }
        }
    }
    
    fun startDownload(context: android.content.Context) {
        val target = linkedBooks.firstOrNull { it.hasAudiobook } 
            ?: linkedBooks.firstOrNull { it.hasEbook } 
            ?: linkedBooks.firstOrNull() 
            ?: return
        
        viewModelScope.launch {
            try {
                val server = serverDao.getServerById(target.serverId)
                val token = server?.authToken
                
                 val service = serviceManager.getService(target.serverId)
                 val details = service?.getBookDetails(target.serviceBookId)
                 val url = details?.files?.firstOrNull()?.downloadUrl
                 
                 if (url != null && server != null) { // server must be valid to determine type
                     DownloadService.startDownload(
                         context = context,
                         bookId = target.serviceBookId,
                         serviceType = server.toServiceType(),
                         downloadUrl = url,
                         fileName = "${target.title}.bin",
                         coverUrl = target.coverUrl,
                         title = target.title,
                         authToken = token
                     )
                 } else {
                     _state.value = _state.value.copy(error = "Download URL not found")
                 }
            } catch (e: Exception) {
                 _state.value = _state.value.copy(error = e.message)
            }
        }
    }
    
    fun cancelDownload() {
        linkedBooks.forEach { DownloadService.cancelDownload(it.serviceBookId) }
    }
    
    fun unlinkBook() {
        viewModelScope.launch {
            try {
                // Break the link for all books in this unified set
                linkedBooks.forEach { book ->
                    // Set unified ID to null and set manual flag to true to prevent auto-rematch
                    val updated = book.copy(
                        unifiedBookId = null,
                        isManuallyLinked = true
                    )
                    bookDao.updateBook(updated)
                }
                
                // If there was a Unified Entry, we might want to delete it if it's now empty?
                // The current schema doesn't cascade delete unified entries automatically if empty?
                // For now, simpler to just unlink. Garbage collection of empty unified books can be a separate task.
                
                // Navigate back is handled by UI
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Failed to unlink: ${e.message}")
            }
        }
    }
}
