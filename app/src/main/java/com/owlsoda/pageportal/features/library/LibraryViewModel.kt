package com.owlsoda.pageportal.features.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.ServerDao
import com.owlsoda.pageportal.core.database.dao.UnifiedBookDao
import com.owlsoda.pageportal.core.database.entity.ServerEntity
import com.owlsoda.pageportal.data.preferences.PreferencesRepository
import com.owlsoda.pageportal.services.ServiceManager
import com.owlsoda.pageportal.services.ServiceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UnifiedBookDisplay(
    val id: Long,
    val title: String,
    val authors: String,
    val coverUrl: String?,
    val hasEbook: Boolean,
    val hasAudiobook: Boolean,
    val hasReadAloud: Boolean,
    val serverIds: Set<Long>,
    val isDownloaded: Boolean,
    val isDownloading: Boolean,
    val downloadProgress: Float,
    val series: String? = null,
    val seriesIndex: String? = null
)

data class ServerTab(
    val id: Long,
    val name: String,
    val serviceType: ServiceType?,
    val isConnected: Boolean = true,
    val bookCount: Int = 0
)

// View modes matching ReadaloudBooks
enum class ViewMode {
    Home,
    Grid,
    List,
    Authors,
    Series
}

// Sort options matching ReadaloudBooks
enum class SortOption(val displayName: String) {
    TitleAsc("Title (A-Z)"),
    TitleDesc("Title (Z-A)"),
    AuthorAsc("Author (A-Z)"),
    AuthorDesc("Author (Z-A)"),
    SeriesAsc("Series (A-Z)"),
    SeriesDesc("Series (Z-A)")
}

data class LibraryUiState(
    val books: List<UnifiedBookDisplay> = emptyList(),
    // Grouped content for Home Screen
    val recentBooks: List<UnifiedBookDisplay> = emptyList(),
    val booksByService: Map<String, List<UnifiedBookDisplay>> = emptyMap(),
    
    val servers: List<ServerEntity> = emptyList(),
    val serverTabs: List<ServerTab> = emptyList(),
    val selectedTabIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    
    // Filters
    val isOfflineFilterActive: Boolean = false,
    val filterHasAudiobook: Boolean = false,
    val filterHasEbook: Boolean = false,
    val filterHasReadAloud: Boolean = false,
    val filterDownloaded: Boolean = false,
    
    // Search
    val searchQuery: String = "",
    
    // View & Sort
    val viewMode: ViewMode = ViewMode.Home,
    val sortOption: SortOption = SortOption.TitleAsc,
    
    // Grouped data for Authors/Series views
    val uniqueAuthors: List<String> = emptyList(),
    val uniqueSeries: List<String> = emptyList(),
    val selectedFilter: String? = null,  // Selected author or series
    
    // Appearance
    val gridMinWidth: Int = 120
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val unifiedBookDao: UnifiedBookDao,
    private val serverDao: ServerDao,
    private val preferencesRepository: PreferencesRepository,
    private val serviceManager: ServiceManager,
    private val libraryRepository: com.owlsoda.pageportal.data.repository.LibraryRepository,
    private val downloadRepository: com.owlsoda.pageportal.data.repository.DownloadRepository,
    private val localBookImporter: com.owlsoda.pageportal.data.importer.LocalBookImporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState(isLoading = true))
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var allUnifiedBooks: List<UnifiedBookDisplay> = emptyList()
    private var isOfflineMode: Boolean = false

    init {
        observeServers()
        observeBooks()
        observeOfflineMode()
        observeGridSettings()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = libraryRepository.syncLibrary()
            _uiState.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message) }
        }
    }
    
    fun downloadSeries(seriesName: String) {
        viewModelScope.launch {
            val booksInSeries = allUnifiedBooks.filter { it.series == seriesName }
            booksInSeries.forEach { book ->
                if (!book.isDownloaded) {
                    downloadBook(book)
                }
            }
        }
    }
    
    fun downloadAuthor(authorName: String) {
        viewModelScope.launch {
            val booksByAuthor = allUnifiedBooks.filter { it.authors == authorName }
            booksByAuthor.forEach { book ->
                if (!book.isDownloaded) {
                    downloadBook(book)
                }
            }
        }
    }

    private suspend fun downloadBook(book: UnifiedBookDisplay) {
        val unifiedWithBooks = unifiedBookDao.getUnifiedBookWithBooksById(book.id) ?: return
        val books = unifiedWithBooks.books
        
        // Prioritize: Audio > Ebook > ReadAloud > First available
        val target = books.firstOrNull { it.hasAudiobook }
             ?: books.firstOrNull { it.hasEbook }
             ?: books.firstOrNull { it.hasReadAloud }
             ?: books.firstOrNull()
             ?: return

        try {
            downloadRepository.startDownload(target.id, target.serverId, target.serviceBookId)
        } catch (e: Exception) {
            // Log error or update state
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTabIndex = index, selectedFilter = null) }
        updateDisplayedBooks()
    }
    
    // ... existing filter toggles ...
    fun toggleOfflineFilter() {
        _uiState.update { it.copy(isOfflineFilterActive = !it.isOfflineFilterActive) }
        updateDisplayedBooks()
    }
    
    fun toggleAudiobookFilter() {
        _uiState.update { it.copy(filterHasAudiobook = !it.filterHasAudiobook) }
        updateDisplayedBooks()
    }
    
    fun toggleEbookFilter() {
        _uiState.update { it.copy(filterHasEbook = !it.filterHasEbook) }
        updateDisplayedBooks()
    }
    
    fun toggleReadAloudFilter() {
        _uiState.update { it.copy(filterHasReadAloud = !it.filterHasReadAloud) }
        updateDisplayedBooks()
    }
    
    fun toggleDownloadedFilter() {
        _uiState.update { it.copy(filterDownloaded = !it.filterDownloaded) }
        updateDisplayedBooks()
    }
    
    // Search
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        updateDisplayedBooks()
    }
    
    // View mode
    fun setViewMode(mode: ViewMode) {
        _uiState.update { it.copy(viewMode = mode, selectedFilter = null) }
        updateDisplayedBooks()
    }
    
    // Sort
    fun setSortOption(sort: SortOption) {
        _uiState.update { it.copy(sortOption = sort) }
        updateDisplayedBooks()
    }
    
    // Author/Series selection
    fun selectFilter(filter: String) {
        _uiState.update { it.copy(selectedFilter = filter) }
        updateDisplayedBooks()
    }
    
    fun clearFilter() {
        _uiState.update { it.copy(selectedFilter = null) }
        updateDisplayedBooks()
    }

    private fun observeServers() {
        viewModelScope.launch {
            serverDao.getAllServers().collect { servers ->
                _uiState.update { it.copy(servers = servers) }
                updateDisplayedBooks()
            }
        }
    }

    private fun observeOfflineMode() {
        viewModelScope.launch {
            preferencesRepository.isOfflineModeEnabled.collect { offline ->
                isOfflineMode = offline
                updateDisplayedBooks()
            }
        }
    }
    
    private fun observeGridSettings() {
        viewModelScope.launch {
            preferencesRepository.gridMinWidth.collect { width ->
                _uiState.update { it.copy(gridMinWidth = width) }
            }
        }
    }

    private fun observeBooks() {
        viewModelScope.launch {
            unifiedBookDao.getAllWithBooks().collect { unifiedList ->
                allUnifiedBooks = unifiedList.map { item ->
                    val books = item.books
                    val serverIds = books.map { it.serverId }.toSet()
                    
                    UnifiedBookDisplay(
                        id = item.unifiedBook.id,
                        title = item.unifiedBook.title,
                        authors = item.unifiedBook.author,
                        coverUrl = item.unifiedBook.coverUrl,
                        hasEbook = books.any { it.hasEbook },
                        hasAudiobook = books.any { it.hasAudiobook },
                        hasReadAloud = books.any { it.hasReadAloud },
                        serverIds = serverIds,
                        isDownloaded = books.any { 
                            it.downloadStatus == "COMPLETED" ||
                            it.isAudiobookDownloaded ||
                            it.isEbookDownloaded ||
                            it.isReadAloudDownloaded
                        },
                        isDownloading = books.any {
                            it.downloadStatus == "QUEUED" || it.downloadStatus == "DOWNLOADING"
                        },
                        downloadProgress = books.maxOfOrNull { it.downloadProgress } ?: 0f,
                        series = books.firstOrNull()?.series,
                        seriesIndex = books.firstOrNull()?.seriesIndex
                    )
                }
                updateDisplayedBooks()
            }
        }
    }

    private fun buildServerTabs(servers: List<ServerEntity>, books: List<UnifiedBookDisplay>): List<ServerTab> {
        val tabs = mutableListOf<ServerTab>()
        
        // "All" Tab
        tabs.add(ServerTab(
            id = -1,
            name = "All",
            serviceType = null,
            isConnected = true,
            bookCount = books.size
        ))
        
        // Server Tabs
        for (server in servers) {
            val count = books.count { it.serverIds.contains(server.id) }
            val sType = try {
                ServiceType.valueOf(server.serviceType)
            } catch (e: Exception) { null }
            
            tabs.add(ServerTab(
                id = server.id,
                name = server.displayName,
                serviceType = sType,
                isConnected = true,
                bookCount = count
            ))
        }
        return tabs
    }

    private fun updateDisplayedBooks() {
        val currentServers = _uiState.value.servers
        val newTabs = buildServerTabs(currentServers, allUnifiedBooks)
        val state = _uiState.value
        
        var newIndex = state.selectedTabIndex
        if (newIndex >= newTabs.size) {
            newIndex = 0
        }
        
        val selectedTab = newTabs.getOrNull(newIndex)
        var filtered = allUnifiedBooks
        
        // Filter by Tab
        if (selectedTab != null && selectedTab.id != -1L) {
             filtered = filtered.filter { book: UnifiedBookDisplay ->
                 book.serverIds.contains(selectedTab.id)
             }
        }
        
        // Apply global filters
        filtered = applyFilters(filtered, state)
        
        // Apply search
        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.lowercase().trim()
            filtered = filtered.filter { book ->
                book.title.lowercase().contains(query) ||
                book.authors.lowercase().contains(query) ||
                (book.series?.lowercase()?.contains(query) == true)
            }
        }
        
        // Build unique authors/series BEFORE applying author/series filter
        // Use 'filtered' here (which already has tab + global filters) so only 
        // authors/series with visible books appear in the list
        val uniqueAuthors = filtered.map { it.authors }.distinct().sorted()
        val uniqueSeries = filtered.mapNotNull { it.series }.distinct().sorted()
        
        // Apply view-specific filtering
        if (state.viewMode == ViewMode.Authors && state.selectedFilter != null) {
            filtered = filtered.filter { it.authors == state.selectedFilter }
        } else if (state.viewMode == ViewMode.Series && state.selectedFilter != null) {
            filtered = filtered.filter { it.series == state.selectedFilter }
        }
        
        // Apply sorting
        filtered = applySorting(filtered, state.sortOption, state.viewMode, state.selectedFilter)
        
        // Home Screen Data
        // Recent: For now, just take the first 10. Ideally, this would be based on lastAccessTime from DB.
        val recent = allUnifiedBooks.take(10)
        
        // Group by Service Name
        val serviceMap = newTabs
            .filter { it.id != -1L } // Exclude "All"
            .associate { tab ->
                val tabBooks = allUnifiedBooks.filter { book -> book.serverIds.contains(tab.id) }
                tab.name to tabBooks
            }
            .filter { it.value.isNotEmpty() }
        
        _uiState.value = state.copy(
            books = filtered,
            recentBooks = recent,
            booksByService = serviceMap,
            serverTabs = newTabs,
            selectedTabIndex = newIndex,
            uniqueAuthors = uniqueAuthors,
            uniqueSeries = uniqueSeries,
            isLoading = false
        )
    }
    
    private fun applyFilters(books: List<UnifiedBookDisplay>, state: LibraryUiState): List<UnifiedBookDisplay> {
        var result = books
        
        // Offline mode OR filter toggle
        if (state.isOfflineFilterActive || isOfflineMode || state.filterDownloaded) {
            result = result.filter { it.isDownloaded }
        }
        
        // If any format filter is active, we filter by the union of selected formats.
        // If no format filter is active, we show all formats.
        val hasFormatFilter = state.filterHasAudiobook || state.filterHasEbook || state.filterHasReadAloud
        
        if (hasFormatFilter) {
            result = result.filter { book ->
                (state.filterHasAudiobook && book.hasAudiobook) ||
                (state.filterHasEbook && book.hasEbook) ||
                (state.filterHasReadAloud && book.hasReadAloud)
            }
        }
        
        return result
    }
    
    private fun applySorting(
        books: List<UnifiedBookDisplay>, 
        sort: SortOption,
        viewMode: ViewMode,
        selectedFilter: String?
    ): List<UnifiedBookDisplay> {
        // Series view with selection: sort by series index
        if (viewMode == ViewMode.Series && selectedFilter != null) {
            return books.sortedWith(compareBy { 
                it.seriesIndex?.toDoubleOrNull() ?: Double.MAX_VALUE 
            })
        }
        
        return when (sort) {
            SortOption.TitleAsc -> books.sortedBy { normalizeTitle(it.title) }
            SortOption.TitleDesc -> books.sortedByDescending { normalizeTitle(it.title) }
            SortOption.AuthorAsc -> books.sortedBy { normalizeTitle(it.authors) }
            SortOption.AuthorDesc -> books.sortedByDescending { normalizeTitle(it.authors) }
            SortOption.SeriesAsc -> books.sortedBy { normalizeTitle(it.series ?: "zzz") }
            SortOption.SeriesDesc -> books.sortedByDescending { normalizeTitle(it.series ?: "") }
        }
    }
    
    // Normalize title by removing common prefixes like "The", "A", "An"
    private fun normalizeTitle(title: String): String {
        val lower = title.lowercase().trim()
        return when {
            lower.startsWith("the ") -> lower.removePrefix("the ")
            lower.startsWith("a ") -> lower.removePrefix("a ")
            lower.startsWith("an ") -> lower.removePrefix("an ")
            else -> lower
        }
    }

    fun importBook(uri: android.net.Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = localBookImporter.importBook(uri)
            if (result.isSuccess) {
                // Refresh list handling is automatic via Flow, but we might want to trigger a "server" refresh or just wait for DB emission
                _uiState.update { it.copy(isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Import failed: ${result.exceptionOrNull()?.message}") }
            }
        }
    }
}

