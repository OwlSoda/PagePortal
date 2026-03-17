package com.owlsoda.pageportal.features.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert

import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.owlsoda.pageportal.services.ServiceType
import androidx.compose.foundation.background
import androidx.compose.material3.LocalContentColor
import com.owlsoda.pageportal.ui.theme.BookCampPlayerBackground
import com.owlsoda.pageportal.ui.theme.BookCampPlayerText
import com.owlsoda.pageportal.ui.theme.BookCampPurple
import coil.compose.AsyncImage
import com.owlsoda.pageportal.features.auth.LoginScreen
import com.owlsoda.pageportal.ui.components.EmptyState
import com.owlsoda.pageportal.features.library.components.FastScroller
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch

private data class EmptyStateInfo(
    val icon: String,
    val title: String,
    val message: String,
    val buttonText: String? = null,
    val onAction: (() -> Unit)? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onBrowseClick: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    
    val pagerState = rememberPagerState(pageCount = { 4 })
    var showOverflowMenu by remember { mutableStateOf(false) }
    
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importBook(it) }
    }
    
    // Show login UI if no servers configured
    if (uiState.servers.isEmpty()) {
        LoginScreen(
            onLoginSuccess = { /* Auto-dismiss when hasServers becomes true */ }
        )
        return
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PagePortal",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    // Search (Primary) - Only if not already in main content area
                    // Link: View Mode toggle
                    IconButton(onClick = { 
                        // Redundant now but keeping for compatibility if needed elsewhere
                    }) {
                        // Empty or remove
                    }

                    // Settings (Primary)
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    
                    // Overflow for secondary actions
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            // Sort
                            DropdownMenuItem(
                                text = { Text("Sort Books") },
                                onClick = { 
                                    showSortMenu = true
                                    showOverflowMenu = false
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) }
                            )
                            
                            // Refresh
                            DropdownMenuItem(
                                text = { Text("Refresh Library") },
                                onClick = { 
                                    viewModel.refresh()
                                    showOverflowMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) }
                            )
                            
                            // Import
                            DropdownMenuItem(
                                text = { Text("Import Content") },
                                onClick = { 
                                    importLauncher.launch(arrayOf("application/epub+zip", "audio/*"))
                                    showOverflowMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Add, null) }
                            )

                            // Browse
                            DropdownMenuItem(
                                text = { Text("Browse Services") },
                                onClick = { 
                                    onBrowseClick()
                                    showOverflowMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Category, null) }
                            )
                            
                            // Offline Toggle
                            DropdownMenuItem(
                                text = { Text(if (uiState.isOfflineFilterActive) "Show Online Content" else "Offline Only") },
                                onClick = { 
                                    viewModel.toggleOfflineFilter()
                                    showOverflowMenu = false
                                },
                                leadingIcon = { 
                                    Icon(
                                        if (uiState.isOfflineFilterActive) Icons.Default.OfflinePin else Icons.Default.CloudQueue, 
                                        null,
                                        tint = if (uiState.isOfflineFilterActive) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                    ) 
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search books...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true
            )
            
            // Filter chips row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.filterHasAudiobook,
                        onClick = { viewModel.toggleAudiobookFilter() },
                        label = { Text("🎧 Audio") }
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.filterHasEbook,
                        onClick = { viewModel.toggleEbookFilter() },
                        label = { Text("📖 Ebook") }
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.filterHasReadAloud,
                        onClick = { viewModel.toggleReadAloudFilter() },
                        label = { Text("🗣️ ReadAloud") }
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.filterDownloaded,
                        onClick = { viewModel.toggleDownloadedFilter() },
                        label = { Text("📥 Downloaded") }
                    )
                }
            } // End of Filter Chips Row

            // Main Navigation Tabs
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 20.dp,
                containerColor = Color.Transparent,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                listOf("Home", "Authors", "Series", "Books").forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { 
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = { 
                            Text(
                                title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (pagerState.currentPage == index) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    )
                }
            }

            // Sync ViewMode with Pager
            LaunchedEffect(pagerState.currentPage) {
                val mode = when(pagerState.currentPage) {
                    0 -> ViewMode.Home
                    1 -> ViewMode.Authors
                    2 -> ViewMode.Series
                    else -> ViewMode.Grid
                }
                if (uiState.viewMode != mode) {
                    viewModel.setViewMode(mode)
                }
            }
            
            // Content
            val pullRefreshState = rememberPullToRefreshState()
            if (pullRefreshState.isAnimating && !uiState.isLoading) {
                LaunchedEffect(true) {
                    pullRefreshState.animateToHidden()
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullToRefresh(
                        state = pullRefreshState,
                        isRefreshing = uiState.isLoading,
                        onRefresh = { viewModel.refresh() }
                    )
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1
                ) { pageIndex ->
                    when (pageIndex) {
                        0 -> HomeTabContent(uiState, onBookClick, onSettingsClick)
                        1 -> AuthorsTabContent(uiState, viewModel)
                        2 -> SeriesTabContent(uiState, viewModel)
                        3 -> BooksTabContent(uiState, gridState, viewModel, onBookClick)
                    }
                }
                
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = uiState.isLoading,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
fun HomeTabContent(
    uiState: LibraryUiState,
    onBookClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    if (uiState.books.isEmpty() && !uiState.isLoading) {
        EmptyLibraryState(uiState, onSettingsClick)
    } else {
        HomeView(
            selectedTabId = uiState.serverTabs.getOrNull(uiState.selectedTabIndex)?.id ?: -1L,
            recentBooks = uiState.recentBooks,
            homeAuthors = uiState.homeAuthors,
            serviceMap = uiState.booksByService,
            onBookClick = onBookClick
        )
    }
}

@Composable
fun AuthorsTabContent(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel
) {
    if (uiState.uniqueAuthors.isEmpty() && !uiState.isLoading) {
        EmptyState(icon = "👤", title = "No Authors", message = "Authors will appear here once you have books.")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(uiState.uniqueAuthors) { author ->
                ListItem(
                    headlineContent = { Text(author) },
                    leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        viewModel.selectFilter(author)
                        viewModel.setViewMode(ViewMode.Grid)
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun SeriesTabContent(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel
) {
    if (uiState.uniqueSeries.isEmpty() && !uiState.isLoading) {
        EmptyState(icon = "📚", title = "No Series", message = "Series information will appear here once you have books.")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(uiState.uniqueSeries) { series ->
                ListItem(
                    headlineContent = { Text(series) },
                    leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        viewModel.selectFilter(series)
                        viewModel.setViewMode(ViewMode.Grid)
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun BooksTabContent(
    uiState: LibraryUiState,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    viewModel: LibraryViewModel,
    onBookClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Service Filter Row
        ScrollableTabRow(
            selectedTabIndex = uiState.selectedTabIndex,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
            divider = {}
        ) {
            uiState.serverTabs.forEachIndexed { index, tab ->
                Tab(
                    selected = uiState.selectedTabIndex == index,
                    onClick = { viewModel.selectTab(index) },
                    text = { 
                        val icon = when {
                            tab.id == -1L -> "📚"
                            tab.serviceType == ServiceType.AUDIOBOOKSHELF -> "🎧"
                            tab.serviceType == ServiceType.BOOKLORE -> "📖"
                            tab.serviceType == ServiceType.STORYTELLER -> "🗣️"
                            else -> "🔗"
                        }
                        Text(text = "$icon ${tab.name}", style = MaterialTheme.typography.labelMedium)
                    }
                )
            }
        }

        // Active filter badge if any
        if (uiState.selectedFilter != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filtered by: ${uiState.selectedFilter}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    IconButton(onClick = { viewModel.clearFilter() }) {
                        Icon(Icons.Default.Close, "Clear")
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.books, key = { it.id }) { book ->
                    BookCard(
                        book = book,
                        onClick = { onBookClick("u_${book.id}") }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyLibraryState(uiState: LibraryUiState, onSettingsClick: () -> Unit) {
    val info = when {
        uiState.servers.isEmpty() -> {
            EmptyStateInfo("🔌", "No Services Connected", "Go to Settings to add Storyteller or other services.", "Open Settings", onSettingsClick)
        }
        uiState.isOfflineFilterActive -> {
            EmptyStateInfo("☁️", "No Downloaded Books", "Download books while online to read them here.", null, null)
        }
        else -> {
            EmptyStateInfo("📚", "No Books Found", "Pull down to refresh or check your server connection.", null, null)
        }
    }
    
    EmptyState(
        icon = info.icon,
        title = info.title,
        message = info.message,
        buttonText = info.buttonText,
        onButtonClick = info.onAction
    )
}

@Composable
fun HomeView(
    selectedTabId: Long,
    recentBooks: List<UnifiedBookDisplay>,
    homeAuthors: List<AuthorDisplay>,
    serviceMap: Map<String, List<UnifiedBookDisplay>>,
    onBookClick: (String) -> Unit
) {
    val isAllTab = selectedTabId == -1L
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Continue Reading Section
        if (recentBooks.isNotEmpty()) {
            item {
                ContinueReadingSection(
                    books = recentBooks, 
                    onBookClick = onBookClick,
                    isCompact = !isAllTab
                )
            }
        }
        
        // Authors Section (Scalable Grid if specific tab, Carousel if All)
        if (homeAuthors.isNotEmpty()) {
            item {
                HomeAuthorsSection(
                    authors = homeAuthors,
                    isGrid = !isAllTab
                )
            }
        }
        
        // Service Rows (Only for "All" tab)
        if (isAllTab) {
            serviceMap.forEach { (serviceName, books) ->
                item {
                    ServiceCarousel(
                        title = serviceName,
                        books = books,
                        onBookClick = onBookClick
                    )
                }
            }
        } else {
            // If on a specific tab, show all books from this server in a grid at the bottom?
            // Or just rely on the sections above. 
            // The user said "RECENTS and AUTHORS sections are cutoff", so I'll focus on making those sections scalable.
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueReadingSection(
    books: List<UnifiedBookDisplay>,
    onBookClick: (String) -> Unit,
    isCompact: Boolean = false
) {
    val listState = rememberLazyListState()
    
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Continue Reading",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
        ) {
            items(books) { book ->
                BookCard(
                    book = book,
                    modifier = Modifier.width(if (isCompact) 140.dp else 180.dp),
                    onClick = { onBookClick("u_${book.id}") }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeAuthorsSection(
    authors: List<AuthorDisplay>,
    isGrid: Boolean = false
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Authors",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        if (isGrid) {
            // Responsive flow layout for authors (scales to fill width)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                maxItemsInEachRow = Int.MAX_VALUE
            ) {
                authors.forEach { author ->
                    HomeAuthorItem(
                        author = author,
                        modifier = Modifier.width(90.dp)
                    )
                }
            }
        } else {
            // Horizontal carousel
            val listState = rememberLazyListState()
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
            ) {
                items(authors) { author ->
                    HomeAuthorItem(
                        author = author,
                        modifier = Modifier.width(100.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeAuthorItem(
    author: AuthorDisplay,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable { /* TODO: Nav to author search */ },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = author.coverUrl,
            contentDescription = author.name,
            modifier = Modifier
                .size(70.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        
        Text(
            text = author.name,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp).padding(horizontal = 4.dp)
        )
        
        Text(
            text = "${author.bookCount} books",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServiceCarousel(
    title: String,
    books: List<UnifiedBookDisplay>,
    onBookClick: (String) -> Unit
) {
    val listState = rememberLazyListState()
    
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = { /* TODO: Filter by service */ }) {
                Text("See All")
            }
        }
        
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
        ) {
            items(books) { book ->
                BookCard(
                    book = book,
                    modifier = Modifier.width(150.dp),
                    onClick = { onBookClick("u_${book.id}") }
                )
            }
        }
    }
}

@Composable
fun BookCard(
    book: UnifiedBookDisplay,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // Cover image with large rounded corners and subtle shadow
        val coverUrl = book.audiobookCoverUrl ?: book.coverUrl
        
        Surface(
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.66f)
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = book.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Format badges overlay (top right)
            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (book.hasEbook) {
                        BadgeIcon(Icons.AutoMirrored.Filled.MenuBook)
                    }
                    if (book.hasAudiobook) {
                        BadgeIcon(Icons.Default.Headphones)
                    }
                }
            }
        }
        
        // Book info
        Column(
            modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp)
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            
            if (book.authors.isNotEmpty()) {
                Text(
                    text = book.authors,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )
            }
            
            if (book.isDownloading || book.listeningProgress > 0) {
                val progressValue = if (book.isDownloading) book.downloadProgress else book.listeningProgress
                val progressColor = if (book.isDownloading) MaterialTheme.colorScheme.primary else BookCampPurple
                
                LinearProgressIndicator(
                    progress = { progressValue },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                    color = progressColor,
                    trackColor = progressColor.copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
private fun BadgeIcon(icon: ImageVector) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = androidx.compose.foundation.shape.CircleShape,
        modifier = Modifier.size(28.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color.White
            )
        }
    }
}
