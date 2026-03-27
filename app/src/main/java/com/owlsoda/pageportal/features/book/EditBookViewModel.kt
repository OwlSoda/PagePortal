package com.owlsoda.pageportal.features.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.entity.BookEntity
import com.owlsoda.pageportal.core.extensions.parseAuthors
import com.owlsoda.pageportal.core.extensions.parseTags
import com.owlsoda.pageportal.data.repository.LibraryRepository
import com.owlsoda.pageportal.services.MetadataUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditBookState(
    val book: BookEntity? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    
    // Form fields
    val title: String = "",
    val authors: String = "",
    val series: String = "",
    val seriesIndex: String = "",
    val description: String = "",
    val tags: String = "",
    val coverImage: ByteArray? = null,
    val coverMimeType: String? = null
)

@HiltViewModel
class EditBookViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EditBookState())
    val state: StateFlow<EditBookState> = _state.asStateFlow()

    fun loadBook(bookId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val book = bookDao.getBookById(bookId)
            if (book != null) {
                _state.update {
                    it.copy(
                        book = book,
                        isLoading = false,
                        title = book.title,
                        authors = book.authors.parseAuthors().joinToString("; "),
                        series = book.series ?: "",
                        seriesIndex = book.seriesIndex ?: "",
                        description = book.description ?: "",
                        tags = book.tags.parseTags().joinToString("; ")
                    )
                }
            } else {
                _state.update { it.copy(isLoading = false, error = "Book not found") }
            }
        }
    }

    fun updateTitle(value: String) = _state.update { it.copy(title = value) }
    fun updateAuthors(value: String) = _state.update { it.copy(authors = value) }
    fun updateSeries(value: String) = _state.update { it.copy(series = value) }
    fun updateSeriesIndex(value: String) = _state.update { it.copy(seriesIndex = value) }
    fun updateDescription(value: String) = _state.update { it.copy(description = value) }
    fun updateTags(value: String) = _state.update { it.copy(tags = value) }
    
    fun updateCoverImage(bytes: ByteArray, mimeType: String) {
        _state.update { it.copy(coverImage = bytes, coverMimeType = mimeType) }
    }

    fun save() {
        val currentBook = _state.value.book ?: return
        
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            
            val metadata = MetadataUpdate(
                title = _state.value.title,
                authors = _state.value.authors.split(";").map { it.trim() }.filter { it.isNotEmpty() },
                series = _state.value.series.ifBlank { null },
                seriesIndex = _state.value.seriesIndex.toFloatOrNull(),
                description = _state.value.description.ifBlank { null },
                tags = _state.value.tags.split(";").map { it.trim() }.filter { it.isNotEmpty() },
                coverImage = _state.value.coverImage,
                coverMimeType = _state.value.coverMimeType
            )
            
            val result = libraryRepository.updateBookMetadata(currentBook.id, metadata)
            
            if (result.isSuccess) {
                _state.update { it.copy(isSaving = false, success = true) }
            } else {
                _state.update { 
                    it.copy(
                        isSaving = false, 
                        error = result.exceptionOrNull()?.message ?: "Unknown error saving"
                    ) 
                }
            }
        }
    }
    
    fun resetSuccess() {
        _state.update { it.copy(success = false) }
    }
}
