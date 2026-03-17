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

import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
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
                    // View mode toggle
                    IconButton(onClick = { 
                        val nextMode = when (uiState.viewMode) {
                            ViewMode.Home -> ViewMode.Grid
                            ViewMode.Grid -> ViewMode.List
                            ViewMode.List -> ViewMode.Authors
                            ViewMode.Authors -> ViewMode.Series
                            ViewMode.Series -> ViewMode.Home
                        }
                        viewModel.setViewMode(nextMode)
                    }) {
                        Icon(
                            when (uiState.viewMode) {
                                ViewMode.Home -> Icons.Default.Home
                                ViewMode.Grid -> Icons.Default.GridView
                                ViewMode.List -> Icons.AutoMirrored.Filled.ViewList
                                else -> Icons.Default.GridView
                            },
                            contentDescription = "View: ${uiState.viewMode.name}"
                        )
                    }
                    
                    // Sort dropdown
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            option.displayName,
                                            color = if (option == uiState.sortOption) 
                                                MaterialTheme.colorScheme.primary 
                                            else 
                                                MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = { 
                                        viewModel.setSortOption(option)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                    
                    IconToggleButton(
                        checked = uiState.isOfflineFilterActive,
                        onCheckedChange = { viewModel.toggleOfflineFilter() }
                    ) {
                        if (uiState.isOfflineFilterActive) {
                            Icon(Icons.Default.OfflinePin, "Show All", tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.CloudQueue, "Show Downloaded Only")
                        }
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onBrowseClick) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = "Browse"
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    
                    // Import Button
                    IconButton(onClick = { 
                        importLauncher.launch(arrayOf("application/epub+zip", "audio/*"))
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Import Book")
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
            }
            
            // Service tabs
            ScrollableTabRow(
                selectedTabIndex = uiState.selectedTabIndex,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 16.dp
            ) {
                uiState.serverTabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = uiState.selectedTabIndex == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { 
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val icon = when {
                                    tab.id == -1L -> "📚"
                                    tab.serviceType == ServiceType.AUDIOBOOKSHELF -> "🎧"
                                    tab.serviceType == ServiceType.BOOKLORE -> "📖"
                                    tab.serviceType == ServiceType.STORYTELLER -> "🗣️"
                                    else -> "🔗"
                                }
                                if (tab.id != -1L) {
                                    val color = if (tab.isConnected) {
                                        androidx.compose.ui.graphics.Color.Green
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(color)
                                    )
                                }

                                Text(text = "$icon ${tab.name}")
                                
                                if (tab.bookCount > 0) {
                                    Text(
                                        text = "(${tab.bookCount})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    )
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
                 when {
                        // Error State
                        uiState.error != null && !uiState.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uiState.error!!,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        
                        // Empty States
                        uiState.books.isEmpty() && !uiState.isLoading -> {
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
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(onClick = { viewModel.refresh() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Refresh Library")
                            }
                        }
                        
                        // Content Logic
                        else -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Header for Author/Series view with Download All
                                if (uiState.selectedFilter != null && (uiState.viewMode == ViewMode.Authors || uiState.viewMode == ViewMode.Series)) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = uiState.selectedFilter!!,
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                                Text(
                                                    text = "${uiState.books.size} books",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            
                                            Button(
                                                onClick = {
                                                    if (uiState.viewMode == ViewMode.Series) {
                                                        viewModel.downloadSeries(uiState.selectedFilter!!)
                                                    } else {
                                                        viewModel.downloadAuthor(uiState.selectedFilter!!)
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Download All")
                                            }
                                            
                                            IconButton(onClick = { viewModel.clearFilter() }) {
                                                Icon(Icons.Default.Close, "Clear Selection")
                                            }
                                        }
                                    }
                                }
                                
                                // Main Content based on Mode
                                if (uiState.viewMode == ViewMode.Series && uiState.selectedFilter == null) {
                                    // Series List
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(16.dp)
                                    ) {
                                        items(uiState.uniqueSeries.size) { index ->
                                            val series = uiState.uniqueSeries[index]
                                            ListItem(
                                                headlineContent = { Text(series) },
                                                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                                                modifier = Modifier.clickable { viewModel.selectFilter(series) }
                                            )
                                            HorizontalDivider()
                                        }
                                    }
                                } else if (uiState.viewMode == ViewMode.Authors && uiState.selectedFilter == null) {
                                    // Authors List
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(16.dp)
                                    ) {
                                        items(uiState.uniqueAuthors.size) { index ->
                                            val author = uiState.uniqueAuthors[index]
                                            ListItem(
                                                headlineContent = { Text(author) },
                                                leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                                                modifier = Modifier.clickable { viewModel.selectFilter(author) }
                                            )
                                            HorizontalDivider()
                                        }
                                    }
                                } else if (uiState.viewMode != ViewMode.Home) {
                                    // Grid View (Standard or Keyed Filter)
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        LazyVerticalGrid(
                                            state = gridState,
                                            columns = GridCells.Fixed(2), // Force 2-column for BookCamp feel
                                            contentPadding = PaddingValues(20.dp),
                                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                                            verticalArrangement = Arrangement.spacedBy(24.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(
                                                items = uiState.books,
                                                key = { it.id }
                                            ) { book ->
                                                BookCard(
                                                    book = book,
                                                    modifier = Modifier.animateItem(),
                                                    onClick = { onBookClick("u_${book.id}") }
                                                )
                                            }
                                        }

                                        // Fast Scroller Overlay
                                        if (uiState.viewMode == ViewMode.Grid && uiState.sortOption == SortOption.TitleAsc && uiState.books.size > 5) {
                                            FastScroller(
                                                onLetterClick = { letter ->
                                                    val index = uiState.books.indexOfFirst { 
                                                        val title = it.title.trim().lowercase()
                                                        val normalized = when {
                                                            title.startsWith("the ") -> title.removePrefix("the ")
                                                            title.startsWith("a ") -> title.removePrefix("a ")
                                                            title.startsWith("an ") -> title.removePrefix("an ")
                                                            else -> title
                                                        }
                                                        if (letter == '#') {
                                                            normalized.isNotEmpty() && normalized[0].isDigit()
                                                        } else {
                                                            normalized.isNotEmpty() && normalized[0].uppercaseChar() == letter
                                                        }
                                                    }
                                                    if (index != -1) {
                                                        scope.launch {
                                                            gridState.animateScrollToItem(index)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .padding(end = 4.dp)
                                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
                                            )
                                        }
                                    }
                                } else {
                                    // Home Dashboard
                                    HomeView(
                                        selectedTabId = uiState.serverTabs.getOrNull(uiState.selectedTabIndex)?.id ?: -1L,
                                        recentBooks = uiState.recentBooks,
                                        homeAuthors = uiState.homeAuthors,
                                        serviceMap = uiState.booksByService,
                                        onBookClick = onBookClick,
                                        onSettingsClick = onSettingsClick
                                    )
                                }
                            }
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
fun HomeView(
    selectedTabId: Long,
    recentBooks: List<UnifiedBookDisplay>,
    homeAuthors: List<AuthorDisplay>,
    serviceMap: Map<String, List<UnifiedBookDisplay>>,
    onBookClick: (String) -> Unit,
    onSettingsClick: () -> Unit
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
        Text(
            text = "Continue Reading",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        
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
        Text(
            text = "Authors",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
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
                style = MaterialTheme.typography.titleLarge
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
