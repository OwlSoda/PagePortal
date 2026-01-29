package com.owlsoda.pageportal.features.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.dao.ProgressDao
import com.owlsoda.pageportal.download.DownloadService
import com.owlsoda.pageportal.reader.epub.EpubBook
import com.owlsoda.pageportal.reader.epub.EpubParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import javax.inject.Inject

data class ReaderUiState(
    val book: EpubBook? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentChapterIndex: Int = 0,
    val fontSize: Int = 100, // Percent
    val theme: ReaderTheme = ReaderTheme.LIGHT
)

enum class ReaderTheme(val backgroundColor: String, val textColor: String) {
    LIGHT("#FFFFFF", "#000000"),
    DARK("#121212", "#E0E0E0"),
    SEPIA("#F4ECD8", "#5B4636")
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val progressDao: ProgressDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    
    // Parser instance to serve resources
    private val parser = EpubParser()
    
    fun loadBook(bookId: String, context: android.content.Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val id = bookId.toLongOrNull()
            if (id == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Invalid book ID")
                return@launch
            }
            val bookEntity = bookDao.getBookById(id)
            if (bookEntity == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Book not found")
                return@launch
            }
            
            // Determine file path. Assuming DownloadService standard location or a way to get it
            // For this implementation, we assume files are in context.filesDir/downloads/
            // and named based on download logic.
            // TODO: Unify file path logic with DownloadService
            val fileName = "${bookEntity.title.replace(Regex("[^a-zA-Z0-9]"), "_")}.epub" 
            val file = File(context.filesDir, "downloads/$fileName")
            
            if (!file.exists()) {
                 _uiState.value = _uiState.value.copy(isLoading = false, error = "File not found: ${file.absolutePath}")
                 return@launch
            }
            
            val result = parser.parse(file)
            
            result.fold(
                onSuccess = { epubBook ->
                     _uiState.value = _uiState.value.copy(
                         book = epubBook,
                         isLoading = false
                     )
                     // Load saved progress
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to parse EPUB: ${error.message}"
                    )
                }
            )
        }
    }
    
    /**
     * Called by WebViewClient to intercept requests.
     * Path should be relative to OPF base path or root?
     * Our parser stores manifest hrefs relative to OPF.
     * But WebView might request absolute paths.
     * Strategy: We serve everything under http://localhost/
     */
    fun getResource(url: String): InputStream? {
        val book = _uiState.value.book ?: return null
        
        // Remove localhost base
        val path = url.removePrefix("http://localhost/")
        
        // If path matches a chapter href, we get that.
        // But internal resources (css, images) in HTML might be relative.
        // e.g. ../Styles/style.css
        // We need to resolve this against the current chapter's path if possible?
        // Actually, if we use loadDataWithBaseUrl("http://localhost/OEBPS/"), 
        // then "Styles/style.css" becomes "http://localhost/OEBPS/Styles/style.css".
        // The parser has raw zip paths.
        
        // Simple approach: Try to find the exact path in the zip.
        return parser.getInputStream(path) ?: parser.getInputStream(book.basePath + path)
    }
    
    fun nextChapter() {
        val currentState = _uiState.value
        val book = currentState.book ?: return
        if (currentState.currentChapterIndex < book.chapters.size - 1) {
            _uiState.value = currentState.copy(currentChapterIndex = currentState.currentChapterIndex + 1)
        }
    }
    
    fun previousChapter() {
        if (_uiState.value.currentChapterIndex > 0) {
            _uiState.value = _uiState.value.copy(currentChapterIndex = _uiState.value.currentChapterIndex - 1)
        }
    }
    
    fun setFontSize(percent: Int) {
        _uiState.value = _uiState.value.copy(fontSize = percent.coerceIn(50, 200))
    }
    
    fun setTheme(theme: ReaderTheme) {
        _uiState.value = _uiState.value.copy(theme = theme)
    }

    override fun onCleared() {
        super.onCleared()
        parser.close()
    }
}
