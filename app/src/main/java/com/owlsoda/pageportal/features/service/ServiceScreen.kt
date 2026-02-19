package com.owlsoda.pageportal.features.service

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.owlsoda.pageportal.features.library.LibraryViewModel
import com.owlsoda.pageportal.features.library.UnifiedBookDisplay
import com.owlsoda.pageportal.features.library.ViewMode
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import com.owlsoda.pageportal.features.library.BookCard
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.owlsoda.pageportal.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceScreen(
    serviceType: String, // "Storyteller", "Audiobookshelf", "Booklore"
    onBookClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Filter books for this service
    // Note: This filtering logic might ideally move to VM to avoid large lists in UI
    val serviceBooks = remember(uiState.books, serviceType) {
        // Find the tab ID for this service
        val tabId = uiState.serverTabs.find { 
            it.name.contains(serviceType, ignoreCase = true) 
        }?.id ?: -999L
        
        uiState.books.filter { it.serverIds.contains(tabId) }
    }

    val pagerState = rememberPagerState(pageCount = { 4 })
    val titles = listOf("Recent", "Authors", "Series", "All")
    val icons = listOf(Icons.Filled.Archive, Icons.Filled.Person, Icons.Filled.Folder, Icons.AutoMirrored.Filled.List)

    val pullRefreshState = rememberPullToRefreshState()
    
    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = { viewModel.refresh() },
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            CenterAlignedTopAppBar(
                title = { Text(serviceType) },
                actions = {
                    // Filter/Sort can go here
                    IconButton(onClick = { /* TODO: Sort Dialog */ }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, "Sort")
                    }
                }
            )

            // Tab Row
            TabRow(selectedTabIndex = pagerState.currentPage) {
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) },
                        icon = { Icon(icons[index], null) }
                    )
                }
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> RecentPage(serviceBooks, onBookClick)
                    1 -> AuthorsPage(serviceBooks, onAuthorClick)
                    2 -> SeriesPage(serviceBooks, onSeriesClick)
                    3 -> AllBooksPage(serviceBooks, onBookClick)
                }
            }
        }
    }
}

@Composable
fun RecentPage(books: List<UnifiedBookDisplay>, onBookClick: (String) -> Unit) {
    // Assuming 'books' is already sorted or we sort here by added date (id often proxies this)
    val recent = books.sortedByDescending { it.id }.take(50) // Placeholder sort
    
    if (recent.isEmpty()) {
        EmptyState(Icons.AutoMirrored.Filled.LibraryBooks, "No books found", "Recently added books will appear here.")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(recent, key = { it.id }) { book ->
            BookCard(book = book, onClick = { onBookClick("u_${book.id}") })
        }
    }
}

@Composable
fun AllBooksPage(books: List<UnifiedBookDisplay>, onBookClick: (String) -> Unit) {
     if (books.isEmpty()) {
        EmptyState(Icons.AutoMirrored.Filled.LibraryBooks, "No books found", "Books from this service will appear here.")
        return
    }
    
    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(books, key = { it.id }) { book ->
            BookCard(book = book, onClick = { onBookClick("u_${book.id}") })
        }
    }
}

@Composable
fun AuthorsPage(books: List<UnifiedBookDisplay>, onAuthorClick: (String) -> Unit) {
    val authors = remember(books) { books.map { it.authors }.distinct().sorted() }
    
    if (authors.isEmpty()) {
        EmptyState(Icons.Filled.Person, "No authors found", "Authors from this service will appear here.")
        return
    }

    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding = PaddingValues(16.dp)
    ) {
        items(authors.size) { index ->
            val author = authors[index]
            ListItem(
                headlineContent = { Text(author) },
                leadingContent = { Icon(Icons.Filled.Person, null) },
                modifier = Modifier.clickable { 
                    onAuthorClick(author)
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun SeriesPage(books: List<UnifiedBookDisplay>, onSeriesClick: (String) -> Unit) {
    val seriesList = remember(books) { books.mapNotNull { it.series }.distinct().sorted() }
    
     if (seriesList.isEmpty()) {
        EmptyState(Icons.Filled.Folder, "No series found", "Series from this service will appear here.")
        return
    }

    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding = PaddingValues(16.dp)
    ) {
        items(seriesList.size) { index ->
            val series = seriesList[index]
            ListItem(
                headlineContent = { Text(series) },
                leadingContent = { Icon(Icons.Filled.Folder, null) },
                modifier = Modifier.clickable { 
                   onSeriesClick(series)
                }
            )
             HorizontalDivider()
        }
    }
}
