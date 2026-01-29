package com.owlsoda.pageportal.features.comic

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.dao.ProgressDao
import com.owlsoda.pageportal.core.database.entity.ProgressEntity
import com.owlsoda.pageportal.reader.comic.ComicParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ComicReaderState(
    val bookId: String = "",
    val title: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val currentBitmap: Bitmap? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showControls: Boolean = true,
    val readingMode: ReadingMode = ReadingMode.SINGLE_PAGE,
    val isRightToLeft: Boolean = false  // Manga mode
)

enum class ReadingMode {
    SINGLE_PAGE,
    SCROLL_VERTICAL,
    SCROLL_HORIZONTAL,
    DOUBLE_PAGE  // For tablets
}

@HiltViewModel
class ComicReaderViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val progressDao: ProgressDao
) : ViewModel() {
    
    private val _state = MutableStateFlow(ComicReaderState())
    val state: StateFlow<ComicReaderState> = _state.asStateFlow()
    
    private val parser = ComicParser()
    private var comic: ComicParser.ComicBook? = null
    private val pageCache = mutableMapOf<Int, Bitmap>()
    private val maxCacheSize = 5
    
    fun loadComic(filePath: String, bookId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, bookId = bookId)
            
            val file = File(filePath)
            val result = parser.open(file)
            
            result.fold(
                onSuccess = { book ->
                    comic = book
                    _state.value = _state.value.copy(
                        title = book.title,
                        pageCount = book.pageCount,
                        isLoading = false
                    )
                    
                    // Load saved progress
                    loadSavedProgress(bookId)
                    
                    // Load first page
                    loadPage(_state.value.currentPage)
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to open comic"
                    )
                }
            )
        }
    }
    
    private suspend fun loadSavedProgress(bookId: String) {
        try {
            val id = bookId.toLongOrNull() ?: return
            progressDao.getProgressByBookId(id)?.let { progress ->
                val page = progress.currentChapter
                if (page > 0 && page < _state.value.pageCount) {
                    _state.value = _state.value.copy(currentPage = page)
                }
            }
        } catch (e: Exception) {
            // Ignore progress loading errors
        }
    }
    
    /**
     * Get a page directly for list-based viewers (Webtoon mode).
     * Uses internal cache but doesn't update the central 'currentPage' state.
     */
    suspend fun getPage(index: Int): Bitmap? {
        // Check cache first
        pageCache[index]?.let { return it }
        
        // Load from file
        return parser.loadPage(index, maxWidth = 2048).getOrNull()?.also { bitmap ->
            addToCache(index, bitmap)
        }
    }

    private fun loadPage(pageIndex: Int) {
        if (pageIndex < 0 || pageIndex >= _state.value.pageCount) return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            
            // Check cache first
            pageCache[pageIndex]?.let { cached ->
                _state.value = _state.value.copy(
                    currentPage = pageIndex,
                    currentBitmap = cached,
                    isLoading = false
                )
                preloadAdjacentPages(pageIndex)
                return@launch
            }
            
            // Load from file
            val result = parser.loadPage(pageIndex, maxWidth = 2048)
            
            result.fold(
                onSuccess = { bitmap ->
                    // Add to cache
                    addToCache(pageIndex, bitmap)
                    
                    _state.value = _state.value.copy(
                        currentPage = pageIndex,
                        currentBitmap = bitmap,
                        isLoading = false
                    )
                    
                    // Preload adjacent pages
                    preloadAdjacentPages(pageIndex)
                    
                    // Save progress
                    saveProgress()
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to load page ${pageIndex + 1}: ${error.message}"
                    )
                }
            )
        }
    }
    
    private fun addToCache(pageIndex: Int, bitmap: Bitmap) {
        pageCache[pageIndex] = bitmap
        
        // Evict old entries if cache is too large
        // For webtoon mode, we might want a slightly larger cache
        val limit = if (_state.value.readingMode == ReadingMode.SCROLL_VERTICAL) 10 else 5
        
        if (pageCache.size > limit) {
            val currentPage = _state.value.currentPage
            // In list mode, current page might not be accurate, so evict farthest keys
            // This is a simple heuristic 
            val keysToRemove = pageCache.keys
                .sortedByDescending { kotlin.math.abs(it - pageIndex) } // Remove farthest from requested index
                .take(pageCache.size - limit)
            
            keysToRemove.forEach { key ->
                pageCache[key]?.recycle()
                pageCache.remove(key)
            }
        }
    }
    
    private fun preloadAdjacentPages(currentPage: Int) {
        viewModelScope.launch {
            // Preload next and previous pages
            listOf(currentPage + 1, currentPage - 1, currentPage + 2).forEach { page ->
                if (page in 0 until _state.value.pageCount && page !in pageCache) {
                    parser.loadPage(page, maxWidth = 2048).onSuccess { bitmap ->
                        pageCache[page] = bitmap
                    }
                }
            }
        }
    }
    
    fun nextPage() {
        val next = if (_state.value.isRightToLeft) {
            _state.value.currentPage - 1
        } else {
            _state.value.currentPage + 1
        }
        
        if (next in 0 until _state.value.pageCount) {
            loadPage(next)
        }
    }
    
    fun previousPage() {
        val prev = if (_state.value.isRightToLeft) {
            _state.value.currentPage + 1
        } else {
            _state.value.currentPage - 1
        }
        
        if (prev in 0 until _state.value.pageCount) {
            loadPage(prev)
        }
    }
    
    fun goToPage(pageIndex: Int) {
        if (pageIndex in 0 until _state.value.pageCount) {
            loadPage(pageIndex)
        }
    }
    
    fun toggleControls() {
        _state.value = _state.value.copy(showControls = !_state.value.showControls)
    }
    
    fun setReadingMode(mode: ReadingMode) {
        _state.value = _state.value.copy(readingMode = mode)
    }
    
    fun toggleRightToLeft() {
        _state.value = _state.value.copy(isRightToLeft = !_state.value.isRightToLeft)
    }
    
    private fun saveProgress() {
        val state = _state.value
        if (state.bookId.isEmpty()) return
        
        viewModelScope.launch {
            val progress = ProgressEntity(
                bookId = state.bookId.toLongOrNull() ?: 0L,
                currentChapter = state.currentPage,
                percentComplete = if (state.pageCount > 0) {
                    ((state.currentPage + 1).toFloat() / state.pageCount * 100).coerceIn(0f, 100f)
                } else 0f,
                lastUpdated = System.currentTimeMillis()
            )
            progressDao.insertProgress(progress)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        saveProgress()
        pageCache.values.forEach { it.recycle() }
        pageCache.clear()
        parser.close()
    }
}
