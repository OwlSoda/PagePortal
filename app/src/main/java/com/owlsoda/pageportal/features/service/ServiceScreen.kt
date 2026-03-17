package com.owlsoda.pageportal.features.service

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.text.style.TextOverflow
import com.owlsoda.pageportal.features.library.BookCard
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState

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
    
    // Sort state
    var currentSort by remember { mutableStateOf("RECENT") }
    var showSortDialog by remember { mutableStateOf(false) }
    
    // Filter books for this service
    val serviceBooks = remember(uiState.books, serviceType, currentSort, uiState.serverTabs) {
        val tabIds = uiState.serverTabs.filter { tab ->
            tab.serviceType?.name?.equals(serviceType, ignoreCase = true) == true
        }.map { it.id }.toSet()
        
        val filtered = uiState.books.filter { book -> 
            book.serverIds.any { it in tabIds } 
        }
        
        when (currentSort) {
            "TITLE" -> filtered.sortedBy { it.title.lowercase() }
            "AUTHOR" -> filtered.sortedBy { it.authors.lowercase() }
            "SERIES" -> filtered.sortedWith(compareBy({ it.series?.lowercase() ?: "\uFFFF" }, { it.seriesIndex?.toDoubleOrNull() ?: Double.MAX_VALUE }))
            else -> filtered.sortedByDescending { it.addedAt }
        }
    }

    val pagerState = rememberPagerState(pageCount = { 4 })
    val titles = listOf("Recent", "Authors", "Series", "All")
    val icons = listOf(Icons.Default.Archive, Icons.Default.Person, Icons.Default.Folder, Icons.AutoMirrored.Filled.List)

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
                    IconButton(onClick = { showSortDialog = true }) {
                        Icon(Icons.Default.Sort, "Sort")
                    }
                }
            )

            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { 
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Visible
                            ) 
                        },
                        icon = { Icon(icons[index], contentDescription = null, modifier = Modifier.size(20.dp)) }
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
    
    // Sort Dialog
    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Sort By") },
            text = {
                Column {
                    val options = listOf(
                        "RECENT" to "Recently Added",
                        "TITLE" to "Title",
                        "AUTHOR" to "Author",
                        "SERIES" to "Series"
                    )
                    options.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentSort = value
                                    showSortDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label)
                            RadioButton(
                                selected = currentSort == value,
                                onClick = {
                                    currentSort = value
                                    showSortDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RecentPage(books: List<UnifiedBookDisplay>, onBookClick: (String) -> Unit) {
    val recent = books.sortedByDescending { it.addedAt }.take(50)
    
    if (recent.isEmpty()) {
        EmptyStateMessage("No books found")
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
        EmptyStateMessage("No books found")
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
        EmptyStateMessage("No authors found")
        return
    }

    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding = PaddingValues(16.dp)
    ) {
        items(authors.size) { index ->
            val author = authors[index]
            ListItem(
                headlineContent = { Text(author) },
                leadingContent = { Icon(Icons.Default.Person, null) },
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
        EmptyStateMessage("No series found")
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
                   onSeriesClick(series)
                }
            )
             HorizontalDivider()
        }
    }
}

@Composable
fun EmptyStateMessage(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(msg, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
