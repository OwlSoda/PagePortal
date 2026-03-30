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
    val audioSizeBytes: Long = 0,
    val formattedAudioSize: String = "0 MB",
    val ebookSizeBytes: Long = 0,
    val formattedEbookSize: String = "0 MB",
    val cacheSizeBytes: Long = 0,
    val formattedCacheSize: String = "0 MB",
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

    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    size += getFolderSize(child)
                }
            }
        } else {
            size = file.length()
        }
        return size
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            // Observe the Flow of downloaded books
            bookDao.getDownloadedBooks().collect { downloadedBooks ->
                _uiState.update { it.copy(isLoading = true) }
                
                var audioSize = 0L
                var ebookSize = 0L
                
                val storageItems = downloadedBooks.mapNotNull { book ->
                    // Note: BookEntity has localFilePath for audio/ebook/readaloud updates
                    val path = book.localFilePath
                    
                    var fileSize = 0L
                    var found = false
                    
                    if (path != null) {
                        val file = File(path)
                         if (file.exists()) {
                            fileSize = file.length()
                            found = true
                        }
                    } 
                    
                    if (!found) {
                         // Fallback check standard path
                         val filename = "book_${book.id}.${if (book.hasAudiobook) "m4b" else "epub"}" 
                         val file = File(context.filesDir, "downloads/$filename")
                         if (file.exists()) {
                            fileSize = file.length()
                            found = true
                         }
                    }
                    
                    if (found) {
                        if (book.hasAudiobook) audioSize += fileSize
                        else if (book.hasEbook) ebookSize += fileSize
                        
                        StorageItem(
                            bookId = book.id,
                            title = book.title,
                            author = book.authors,
                            coverUrl = book.coverUrl,
                            sizeBytes = fileSize,
                            formattedSize = formatFileSize(fileSize)
                        )
                    } else null
                }
                
                // Calculate Cache Size (ReadAloud unzipped)
                val cacheDir = File(context.cacheDir, "readaloud")
                val cacheSize = if (cacheDir.exists()) getFolderSize(cacheDir) else 0L
                
                val totalSize = audioSize + ebookSize + cacheSize
                
                _uiState.update { 
                    it.copy(
                        items = storageItems, 
                        audioSizeBytes = audioSize,
                        formattedAudioSize = formatFileSize(audioSize),
                        ebookSizeBytes = ebookSize,
                        formattedEbookSize = formatFileSize(ebookSize),
                        cacheSizeBytes = cacheSize,
                        formattedCacheSize = formatFileSize(cacheSize),
                        totalSizeBytes = totalSize,
                        formattedTotalSize = formatFileSize(totalSize),
                        isLoading = false
                    ) 
                }
            }
        }
    }
    
    fun clearReadAloudCache() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val cacheDir = File(context.cacheDir, "readaloud")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
            // Trigger UI update
            val currentCacheSize = getFolderSize(cacheDir) // should be 0
            _uiState.update { 
                it.copy(
                    cacheSizeBytes = currentCacheSize,
                    formattedCacheSize = formatFileSize(currentCacheSize),
                    totalSizeBytes = it.audioSizeBytes + it.ebookSizeBytes + currentCacheSize,
                    formattedTotalSize = formatFileSize(it.audioSizeBytes + it.ebookSizeBytes + currentCacheSize)
                ) 
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
