package com.owlsoda.pageportal.features.settings.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.UnifiedBookDao
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

data class StorageItem(
    val bookId: Long,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val sizeBytes: Long,
    val formattedSize: String
)

data class StorageUiState(
    val items: List<StorageItem> = emptyList(),
    val totalSizeBytes: Long = 0,
    val formattedTotalSize: String = "0 MB",
    val isLoading: Boolean = false
)

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val unifiedBookDao: UnifiedBookDao,
    private val bookDao: BookDao, // Need direct access to book entities for file paths if stored there, or just iterate common paths
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageUiState(isLoading = true))
    val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()

    init {
        observeDownloads()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            // Observe the Flow of downloaded books
            bookDao.getDownloadedBooks().collect { downloadedBooks ->
                _uiState.update { it.copy(isLoading = true) }
                
                val storageItems = downloadedBooks.mapNotNull { book ->
                    // Logic to find file. 
                    // Note: BookEntity has localFilePath for audio/ebook/readaloud updates
                    // We should check that field first.
                    val path = book.localFilePath
                    
                    if (path != null) {
                        val file = File(path)
                         if (file.exists()) {
                            val size = file.length()
                            StorageItem(
                                bookId = book.id,
                                title = book.title,
                                author = book.authors,
                                coverUrl = book.coverUrl,
                                sizeBytes = size,
                                formattedSize = formatFileSize(size)
                            )
                        } else null
                    } else {
                         // Fallback check standard path if DB path is missing (optional)
                         val filename = "book_${book.id}.${if (book.hasAudiobook) "m4b" else "epub"}" 
                         val file = File(context.filesDir, "downloads/$filename")
                         if (file.exists()) {
                            val size = file.length()
                            StorageItem(
                                bookId = book.id,
                                title = book.title,
                                author = book.authors,
                                coverUrl = book.coverUrl,
                                sizeBytes = size,
                                formattedSize = formatFileSize(size)
                            )
                         } else null
                    }
                }
                
                val totalSize = storageItems.sumOf { it.sizeBytes }
                
                _uiState.update { 
                    it.copy(
                        items = storageItems, 
                        totalSizeBytes = totalSize,
                        formattedTotalSize = formatFileSize(totalSize),
                        isLoading = false
                    ) 
                }
            }
        }
    }
    
    fun deleteItem(item: StorageItem) {
        viewModelScope.launch {
             downloadRepository.deleteDownload(item.bookId)
        }
    }

    private fun formatFileSize(size: Long): String {
        val mb = size / (1024.0 * 1024.0)
        return String.format("%.1f MB", mb)
    }
}
