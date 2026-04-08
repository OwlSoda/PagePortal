package com.owlsoda.pageportal.features.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.entity.BookEntity
import com.owlsoda.pageportal.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueueDashboardViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    val activeJobs: StateFlow<List<BookEntity>> = bookDao.getActiveProcessingBooks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun cancelJob(bookId: Long) {
        viewModelScope.launch {
            libraryRepository.cancelReadAloud(bookId)
            libraryRepository.refreshBookMetadata(bookId) // refresh processing state immediately
        }
    }
}
