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
import androidx.compose.material.icons.filled.Sort
import com.owlsoda.pageportal.features.library.SortOption
import com.owlsoda.pageportal.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceScreen(
    serviceType: String, // "Storyteller", "Audiobookshelf", "Booklore"
    onBookClick: (String) -> Unit,
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
    val icons = listOf(Icons.Default.Archive, Icons.Default.Person, Icons.Default.Folder, Icons.AutoMirrored.Filled.List)

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        CenterAlignedTopAppBar(
            title = { Text(serviceType) },
            actions = {
               // Filter/Sort can go here
               IconButton(onClick = { /* TODO: Sort Dialog */ }) {
                   Icon(Icons.Default.Sort, "Sort")
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
                1 -> AuthorsPage(serviceBooks, viewModel)
                2 -> SeriesPage(serviceBooks, viewModel)
                3 -> AllBooksPage(serviceBooks, onBookClick)
            }
        }
    }
}

@Composable
fun RecentPage(books: List<UnifiedBookDisplay>, onBookClick: (String) -> Unit) {
    // Assuming 'books' is already sorted or we sort here by added date (id often proxies this)
    val recent = books.sortedByDescending { it.id }.take(50) // Placeholder sort
    
    if (recent.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Archive,
            contentDescription = "No recent books",
            title = "No Books Found",
            message = "Recently added books will appear here."
        )
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
        EmptyState(
            icon = Icons.AutoMirrored.Filled.List,
            contentDescription = "No books",
            title = "No Books Found",
            message = "This service appears to be empty."
        )
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
fun AuthorsPage(books: List<UnifiedBookDisplay>, viewModel: LibraryViewModel) {
    val authors = remember(books) { books.map { it.authors }.distinct().sorted() }
    
    if (authors.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Person,
            contentDescription = "No authors",
            title = "No Authors Found",
            message = "No authors found on this service."
        )
        return
    }

    // Reuse the LibraryViewModel's filtering mechanism or navigate to a filtered view
    // For now, let's just show a list
    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding = PaddingValues(16.dp)
    ) {
        items(authors.size) { index ->
            val author = authors[index]
            ListItem(
                headlineContent = { Text(author) },
                leadingContent = { Icon(Icons.Default.Person, null) },
                modifier = Modifier.clickable { 
                    // TODO: Navigate to author detail or filter
                    viewModel.selectFilter(author)
                    viewModel.setViewMode(ViewMode.Authors) // This might need adjustment for new nav
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun SeriesPage(books: List<UnifiedBookDisplay>, viewModel: LibraryViewModel) {
    val seriesList = remember(books) { books.mapNotNull { it.series }.distinct().sorted() }
    
     if (seriesList.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Folder,
            contentDescription = "No series",
            title = "No Series Found",
            message = "No series found on this service."
        )
        return
    }

    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding = PaddingValues(16.dp)
    ) {
        items(seriesList.size) { index ->
            val series = seriesList[index]
            ListItem(
                headlineContent = { Text(series) },
                leadingContent = { Icon(Icons.Default.Folder, null) },
                modifier = Modifier.clickable { 
                   viewModel.selectFilter(series)
                   viewModel.setViewMode(ViewMode.Series)
                }
            )
             HorizontalDivider()
        }
    }
}

