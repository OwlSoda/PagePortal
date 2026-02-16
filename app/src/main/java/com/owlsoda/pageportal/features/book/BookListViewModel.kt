package com.owlsoda.pageportal.features.book

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.entity.BookEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.net.URLDecoder

data class BookListUiState(
    val title: String = "",
    val books: List<BookEntity> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class BookListViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val collectionDao: com.owlsoda.pageportal.core.database.dao.CollectionDao,
    private val serverDao: com.owlsoda.pageportal.core.database.dao.ServerDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookListUiState(isLoading = true))
    val uiState: StateFlow<BookListUiState> = _uiState.asStateFlow()

    private val filterType: String = checkNotNull(savedStateHandle["type"])
    private val filterValue: String = URLDecoder.decode(checkNotNull(savedStateHandle["value"]), "UTF-8")
    private val serviceType: String? = savedStateHandle["serviceType"]

    init {
        // Initial title, updated later for Collections
        if (filterType != "COLLECTION") {
            _uiState.update { it.copy(title = filterValue) }
        }
        loadBooks()
    }

    private fun loadBooks() {
        viewModelScope.launch {
            val flow = when (filterType) {
                "AUTHOR" -> {
                    if (serviceType != null) {
                        val normalizedType = serviceType.uppercase()
                        val servers = serverDao.getServersByServiceType(normalizedType)
                        val serverIds = servers.map { it.id }
                        bookDao.getBooksByAuthorAndServerIds(filterValue, serverIds)
                    } else {
                        bookDao.getBooksByAuthor(filterValue)
                    }
                }
                "SERIES" -> {
                    _uiState.update { it.copy(title = filterValue) }
                    if (serviceType != null) {
                        val normalizedType = serviceType.uppercase()
                        val servers = serverDao.getServersByServiceType(normalizedType)
                        val serverIds = servers.map { it.id }
                        bookDao.getBooksBySeriesAndServerIds(filterValue, serverIds)
                    } else {
                         bookDao.getBooksBySeries(filterValue)
                    }
                }
                "COLLECTION" -> {
                     val collectionId = filterValue.toLongOrNull() ?: 0L
                     val collection = collectionDao.getCollectionById(collectionId)
                     if (collection != null) {
                         _uiState.update { it.copy(title = collection.name) }
                         val gson = com.google.gson.Gson()
                         val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                         val bookIds: List<String> = gson.fromJson(collection.bookIds, type)
                         bookDao.getBooksByServiceIds(collection.serverId, bookIds)
                     } else {
                         kotlinx.coroutines.flow.flowOf(emptyList())
                     }
                }
                else -> bookDao.getAllBooks()
            }

            flow.collect { books ->
                _uiState.update { 
                    it.copy(
                        books = books,
                        isLoading = false
                    )
                }
            }
        }
    }
}
