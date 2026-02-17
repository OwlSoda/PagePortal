package com.owlsoda.pageportal.features.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.owlsoda.pageportal.services.ServiceType
import androidx.compose.foundation.background
import coil.compose.AsyncImage
import com.owlsoda.pageportal.features.auth.LoginScreen
import com.owlsoda.pageportal.ui.components.EmptyState

private data class EmptyStateInfo(
    val icon: ImageVector,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PagePortal")
                    }
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
            Box(
                modifier = Modifier.fillMaxSize()
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
                                    EmptyStateInfo(Icons.Filled.LinkOff, "No Services Connected", "Go to Settings to add Storyteller or other services.", "Open Settings", onSettingsClick)
                                }
                                
                                uiState.isOfflineFilterActive -> {
                                    EmptyStateInfo(Icons.Filled.CloudOff, "No Downloaded Books", "Download books while online to read them here.", null, null)
                                }
                                
                                else -> {
                                    EmptyStateInfo(Icons.AutoMirrored.Filled.LibraryBooks, "No Books Found", "Pull down to refresh or check your server connection.", null, null)
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
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = uiState.gridMinWidth.dp),
                                        contentPadding = PaddingValues(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                } else {
                                    // Home Dashboard
                                    HomeView(
                                        recentBooks = uiState.recentBooks,
                                        serviceMap = uiState.booksByService,
                                        onBookClick = onBookClick
                                    )
                                }
                            }
                        }
                    }
                    
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
            }
        }
    }
}

@Composable
fun HomeView(
    recentBooks: List<UnifiedBookDisplay>,
    serviceMap: Map<String, List<UnifiedBookDisplay>>,
    onBookClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Recent Activity
        if (recentBooks.isNotEmpty()) {
            item {
                RecentActivitySection(books = recentBooks, onBookClick = onBookClick)
            }
        }
        
        // Service Rows
        serviceMap.forEach { (serviceName, books) ->
            item {
                ServiceCarousel(
                    title = serviceName,
                    books = books,
                    onBookClick = onBookClick
                )
            }
        }
    }
}

@Composable
fun RecentActivitySection(
    books: List<UnifiedBookDisplay>,
    onBookClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = "Recent Activity",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(books) { book ->
                BookCard(
                    book = book,
                    modifier = Modifier.width(130.dp),
                    onClick = { onBookClick("u_${book.id}") }
                )
            }
        }
    }
}

@Composable
fun ServiceCarousel(
    title: String,
    books: List<UnifiedBookDisplay>,
    onBookClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(books) { book ->
                BookCard(
                    book = book,
                    modifier = Modifier.width(120.dp),
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Cover image
            AsyncImage(
                model = book.coverUrl,
                contentDescription = book.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.66f)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
            
            // Book info
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (book.authors.isNotEmpty()) {
                    Text(
                        text = book.authors,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Format badges
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (book.hasEbook) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.height(24.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = "Ebook available",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    if (book.hasAudiobook) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.height(24.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Headphones,
                                    contentDescription = "Audiobook available",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
                
                if (book.isDownloading) {
                    LinearProgressIndicator(
                        progress = { book.downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(4.dp)
                    )
                }
            }
        }
    }
}
