package com.owlsoda.pageportal.features.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.entity.ServerEntity
import com.owlsoda.pageportal.data.repository.LibraryRepository
import com.owlsoda.pageportal.services.ServiceBook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerSearchResult(
    val server: ServerEntity,
    val books: List<ServiceBook>
)

data class GlobalSearchUiState(
    val query: String = "",
    val results: List<ServerSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
    val bookInLibraryIds: Set<String> = emptySet()
)

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val bookDao: BookDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlobalSearchUiState())
    val uiState: StateFlow<GlobalSearchUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        observeSearchQuery()
        observeLibraryBooks()
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        viewModelScope.launch {
            _searchQuery
                .debounce(500)
                .filter { it.length >= 2 }
                .distinctUntilChanged()
                .collect { query ->
                    performSearch(query)
                }
        }
    }

    private fun observeLibraryBooks() {
        viewModelScope.launch {
            bookDao.getAllBooks().collect { books ->
                val ids = books.map { it.serviceBookId }.toSet()
                _uiState.update { it.copy(bookInLibraryIds = ids) }
            }
        }
    }

    fun updateQuery(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(query = query) }
        if (query.isEmpty()) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false) }
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.update { it.copy(isSearching = true, error = null) }
        try {
            val results = libraryRepository.searchServers(query)
            val searchResults = results.map { pair: Pair<ServerEntity, List<ServiceBook>> ->
                ServerSearchResult(pair.first, pair.second)
            }
            _uiState.update { it.copy(results = searchResults, isSearching = false) }
        } catch (e: Exception) {
            _uiState.update { it.copy(isSearching = false, error = e.message) }
        }
    }

    fun addToLibrary(server: ServerEntity, book: ServiceBook) {
        viewModelScope.launch {
            libraryRepository.importBook(server, book)
        }
    }
}
