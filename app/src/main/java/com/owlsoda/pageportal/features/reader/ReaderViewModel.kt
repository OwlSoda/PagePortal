package com.owlsoda.pageportal.features.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.dao.HighlightDao
import com.owlsoda.pageportal.core.database.dao.ProgressDao
import com.owlsoda.pageportal.core.database.dao.ServerDao
import com.owlsoda.pageportal.core.database.entity.HighlightEntity
import com.owlsoda.pageportal.data.preferences.PreferencesRepository
import com.owlsoda.pageportal.download.DownloadService
import com.owlsoda.pageportal.reader.epub.EpubBook
import com.owlsoda.pageportal.reader.epub.EpubParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject

data class SearchResult(
    val chapterIndex: Int,
    val locator: String,
    val previewText: String
)



data class ReaderUiState(
    val book: EpubBook? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentChapterIndex: Int = 0,
    val fontSize: Int = 100, // Percent
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val fontFamily: String = "Serif",
    val lineHeight: Float = 1.5f,
    val margin: Int = 1,
    val highlights: List<HighlightEntity> = emptyList(),
    val searchResults: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false
)

enum class ReaderTheme(val backgroundColor: String, val textColor: String) {
    LIGHT("#FFFFFF", "#000000"),
    DARK("#121212", "#E0E0E0"),
    SEPIA("#F4ECD8", "#5B4636");
    
    companion object {
        fun fromString(value: String): ReaderTheme {
            return try {
                valueOf(value.uppercase())
            } catch (e: Exception) {
                 LIGHT
            }
        }
    }
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val progressDao: ProgressDao,
    private val serverDao: ServerDao,
    private val highlightDao: HighlightDao,
    private val preferencesRepository: PreferencesRepository,
    private val libraryRepository: com.owlsoda.pageportal.data.repository.LibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    
    init {
        observePreferences()
    }
    
    // Parser instance to serve resources
    private val parser = EpubParser()
    private var currentBookId: Long? = null
    
    fun loadBook(bookId: String, context: android.content.Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val id = bookId.toLongOrNull()
            if (id == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Invalid book ID")
                return@launch
            }
            currentBookId = id
            loadHighlights(id)
            
            val bookEntity = bookDao.getBookById(id)
            if (bookEntity == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Book not found")
                return@launch
            }
            
            // Get server to determine service type
            val server = serverDao.getServerById(bookEntity.serverId)
            if (server == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Server not found for this book")
                return@launch
            }
            
            // Match DownloadService pattern: downloads/{serviceType}/{title}.bin
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val serviceTypeName = server.serviceType.lowercase()
            val fileName = "${bookEntity.title}.bin"
            val file = File(baseDir, "downloads/$serviceTypeName/$fileName")
            
            if (!file.exists()) {
                 _uiState.value = _uiState.value.copy(isLoading = false, error = "Book not downloaded. Please download it first from the book details page.")
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
                     viewModelScope.launch {
                         val progress = progressDao.getProgressByBookId(id)
                         if (progress != null) {
                             _uiState.update { it.copy(currentChapterIndex = progress.currentChapter) }
                             // Note: granular scroll restoration would require injecting JS after onPageFinished
                         }
                     }
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

    private fun observePreferences() {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                preferencesRepository.readerFontSize,
                preferencesRepository.readerTheme,
                preferencesRepository.readerFontFamily,
                preferencesRepository.readerLineHeight,
                preferencesRepository.readerMargin
            ) { size, theme, family, height, margin ->
                ReaderPreferences(size, theme, family, height, margin)
            }.collect { prefs ->
                _uiState.update { 
                    it.copy(
                        fontSize = (prefs.fontSize * 100).toInt(),
                        theme = ReaderTheme.fromString(prefs.theme),
                        fontFamily = prefs.fontFamily,
                        lineHeight = prefs.lineHeight,
                        margin = prefs.margin
                    ) 
                }
            }
        }
    }
    
    data class ReaderPreferences(
        val fontSize: Float,
        val theme: String,
        val fontFamily: String,
        val lineHeight: Float,
        val margin: Int
    )

    private fun loadHighlights(bookId: Long) {
        viewModelScope.launch {
            highlightDao.getHighlightsForBook(bookId).collect { highlights ->
                _uiState.value = _uiState.value.copy(highlights = highlights)
            }
        }
    }

    fun addHighlight(chapterIndex: Int, cfi: String, text: String, color: String = "#FFFF00") {
        val bookId = currentBookId ?: return
        viewModelScope.launch {
            val highlight = HighlightEntity(
                bookId = bookId,
                chapterIndex = chapterIndex,
                cfi = cfi,
                selectedText = text,
                color = color
            )
            highlightDao.insertHighlight(highlight)
        }
    }
    
    fun deleteHighlight(highlight: HighlightEntity) {
        viewModelScope.launch {
            highlightDao.deleteHighlight(highlight)
        }
    }

    fun searchBook(query: String) {
        if (query.length < 3) return
        
        _uiState.value = _uiState.value.copy(isSearching = true)
        
        viewModelScope.launch(Dispatchers.IO) {
            val book = _uiState.value.book
            if (book == null) {
                _uiState.value = _uiState.value.copy(isSearching = false)
                return@launch
            }

            val results = mutableListOf<SearchResult>()
            
            book.chapters.forEachIndexed { index, chapter ->
                // Basic text search implementation
                // In production, this should be optimized and run on chunks
                val inputStream = parser.getInputStream(book.basePath + chapter.href) ?: return@forEachIndexed
                try {
                    val content = inputStream.bufferedReader().use { it.readText() }
                    // Strip HTML tags for searching (naive regex)
                    val textContent = content.replace(Regex("<[^>]*>"), " ")
                    
                    var startIndex = 0
                    while (true) {
                        val foundIndex = textContent.indexOf(query, startIndex, ignoreCase = true)
                        if (foundIndex == -1) break
                        
                        val startPreview = (foundIndex - 20).coerceAtLeast(0)
                        val endPreview = (foundIndex + query.length + 20).coerceAtMost(textContent.length)
                        val preview = "..." + textContent.substring(startPreview, endPreview).replace("\n", " ") + "..."
                        
                        // We need a way to link back to this position. 
                        // Without mapped CFI, we can only link to the chapter.
                        results.add(SearchResult(index, "chapter_$index", preview))
                        
                        startIndex = foundIndex + query.length
                        if (results.size > 50) break // Limit results
                    }
                } catch (e: Exception) {
                    // Skip chapter on error
                }
            }
            
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isSearching = false, searchResults = results)
            }
        }
    }
    
    fun clearSearch() {
        _uiState.value = _uiState.value.copy(isSearching = false, searchResults = emptyList())
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
        viewModelScope.launch {
            preferencesRepository.setReaderFontSize(percent / 100f)
        }
    }
    
    fun setTheme(theme: ReaderTheme) {
        viewModelScope.launch {
            preferencesRepository.setReaderTheme(theme.name)
        }
    }
    
    fun setFontFamily(family: String) {
        viewModelScope.launch {
            preferencesRepository.setReaderFontFamily(family)
        }
    }
    
    fun setLineHeight(height: Float) {
        viewModelScope.launch {
            preferencesRepository.setReaderLineHeight(height)
        }
    }
    
    fun setMargin(margin: Int) {
        viewModelScope.launch {
            preferencesRepository.setReaderMargin(margin)
        }
    }

    private var syncJob: kotlinx.coroutines.Job? = null

    fun onProgressChanged(chapterIndex: Int, progressInChapter: Float) {
        val bookId = currentBookId ?: return
        val book = _uiState.value.book ?: return
        
        viewModelScope.launch {
            // Calculate total percentage
            val totalChapters = book.chapters.size
            if (totalChapters == 0) return@launch
            
            val clippedProgress = progressInChapter.coerceIn(0f, 1f)
            val totalProgress = ((chapterIndex + clippedProgress) / totalChapters) * 100f
            
            progressDao.updatePosition(
                bookId = bookId,
                position = 0, 
                chapter = chapterIndex,
                percent = totalProgress
            )
            
            // Debounce network sync
            syncJob?.cancel()
            syncJob = viewModelScope.launch {
                kotlinx.coroutines.delay(5000) // 5 seconds debounce
                libraryRepository.syncProgress(bookId)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Try to sync one last time if there's a pending job
        syncJob?.let {
            if (it.isActive) {
                currentBookId?.let { id ->
                    // We can't use viewModelScope as it's being cancelled, 
                    // but we can fire a sync if needed. 
                    // Actually repository should probably handle this or use workmanager.
                }
            }
        }
        parser.close()
    }
}
