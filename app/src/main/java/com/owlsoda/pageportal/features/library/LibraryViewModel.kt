package com.owlsoda.pageportal.features.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.ServerDao
import com.owlsoda.pageportal.core.database.dao.UnifiedBookDao
import com.owlsoda.pageportal.core.database.entity.ServerEntity
import com.owlsoda.pageportal.data.repository.LibraryRepository
import com.owlsoda.pageportal.data.preferences.PreferencesRepository
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
    val serverIds: Set<Long> // Store server IDs for filtering
)

data class LibraryUiState(
    val books: List<UnifiedBookDisplay> = emptyList(),
    val servers: List<ServerEntity> = emptyList(),
    val selectedTabIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val unifiedBookDao: UnifiedBookDao,
    private val serverDao: ServerDao,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    // Check if user has any configured servers
    val hasServers: StateFlow<Boolean> = serverDao.getActiveServers()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    
    // Keep track of all books (unified)
    private var allUnifiedBooks: List<UnifiedBookDisplay> = emptyList()
    private var isOfflineMode = false
    
    init {
        observeServers()
        observeBooks()
        observePreferences()
        // Only refresh if servers exist - prevents crash on first launch
        viewModelScope.launch {
            try {
                if (serverDao.getActiveServerCount() > 0) {
                    refresh()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    error = "Failed to initialize: ${e.message}"
                )
            }
        }
    }
    
    private fun observePreferences() {
        viewModelScope.launch {
            preferencesRepository.isOfflineModeEnabled.collectLatest { enabled ->
                isOfflineMode = enabled
                updateDisplayedBooks()
            }
        }
    }
    
    private fun observeServers() {
        viewModelScope.launch {
            serverDao.getActiveServers().collect { servers ->
                _uiState.value = _uiState.value.copy(servers = servers)
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
                        serverIds = serverIds
                    )
                }
                updateDisplayedBooks()
            }
        }
    }
    
    fun refresh() {
        viewModelScope.launch {
            try {
                if (isOfflineMode) return@launch // No online sync in offline mode
                
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                try {
                    val result = libraryRepository.syncLibrary()
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    
                    result.onFailure {
                        if (allUnifiedBooks.isEmpty()) {
                            _uiState.value = _uiState.value.copy(error = it.message ?: "Failed to sync library")
                        }
                    }
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = if (allUnifiedBooks.isEmpty()) {
                            "Failed to load library: ${e.message ?: "Unknown error"}"
                        } else null
                    )
                }
            } catch (e: Exception) {
                // Outer catch to prevent any crash
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }
    
    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTabIndex = index)
        updateDisplayedBooks()
    }
    
    private fun updateDisplayedBooks() {
        // Filter logic:
        // 1. If Offline Mode: Show NO items? Or only downloaded?
        //    Current UnifiedBookEntity doesn't track "isDownloaded".
        //    We need to check DownloadService OR rely on "hasDownloadedFile" which we don't track in DB properly yet.
        //    Phase 7 simplification: If offline mode is on, we just show the list but actions might fail if not downloaded.
        //    Ideally we would filter.
        //    Let's inject DownloadService or verify downloads? That's expensive for a list.
        //    For now, pass "isOfflineMode" to UI state and let UI show a banner.
        //    And maybe gray out items?
        
        // 2. Tab Filtering
        var filtered = allUnifiedBooks
        
        if (!isOfflineMode) {
             val servers = _uiState.value.servers
             val selectedServerId = if (_uiState.value.selectedTabIndex > 0 && 
                                       _uiState.value.selectedTabIndex - 1 < servers.size) {
                 servers[_uiState.value.selectedTabIndex - 1].id
             } else null

             if (selectedServerId != null) {
                 filtered = filtered.filter { book ->
                     book.serverIds.contains(selectedServerId)
                 }
             }
        }
        
        _uiState.value = _uiState.value.copy(
            books = filtered.sortedBy { it.title },
            isLoading = false
        )
    }
}
