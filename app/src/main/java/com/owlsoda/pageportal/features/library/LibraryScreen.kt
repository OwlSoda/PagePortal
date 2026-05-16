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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Person
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.ExperimentalSharedTransitionApi
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
import com.owlsoda.pageportal.ui.theme.PagePortalPlayerBackground
import com.owlsoda.pageportal.ui.theme.PagePortalPlayerText
import com.owlsoda.pageportal.ui.theme.PagePortalPurple
import coil.compose.AsyncImage
import com.owlsoda.pageportal.core.extensions.parseAuthors
import com.owlsoda.pageportal.features.auth.LoginScreen
import com.owlsoda.pageportal.ui.components.EmptyState
import com.owlsoda.pageportal.features.library.components.FastScroller
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager

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
    onAuthorClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onGlobalSearchClick: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
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
    
    // Importing Overlay
    if (uiState.isImporting) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.padding(32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Adding to Library...",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Optimizing for ReadAloud playback",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
    
    val topBarState = com.owlsoda.pageportal.navigation.LocalTopBarState.current
    LaunchedEffect(uiState.isOfflineFilterActive) {
        topBarState.title = "PagePortal"
        topBarState.actions = {
            IconButton(onClick = { 
                importLauncher.launch(arrayOf("application/epub+zip", "audio/*", "video/mp4"))
            }) {
                Icon(Icons.Default.Add, contentDescription = "Import Content")
            }

            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
            
            Box {
                IconButton(onClick = { showOverflowMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Sort Books") },
                        onClick = { 
                            showSortMenu = true
                            showOverflowMenu = false
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) }
                    )
                    
                    DropdownMenuItem(
                        text = { Text("Refresh Library") },
                        onClick = { 
                            viewModel.refresh()
                            showOverflowMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Refresh, null) }
                    )
                    
                    DropdownMenuItem(
                        text = { Text("Browse Services") },
                        onClick = { 
                            onBrowseClick()
                            showOverflowMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Category, null) }
                    )
                    
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
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )

            AnimatedVisibility(
                visible = uiState.searchQuery.length >= 2,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { onGlobalSearchClick() },
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Search all servers for \"${uiState.searchQuery}\"",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
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
                        label = { Text("Audio") },
                        leadingIcon = { Icon(Icons.Filled.Headphones, contentDescription = null) }
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.filterHasEbook,
                        onClick = { viewModel.toggleEbookFilter() },
                        label = { Text("Ebook") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) }
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.filterHasReadAloud,
                        onClick = { viewModel.toggleReadAloudFilter() },
                        label = { Text("ReadAloud") },
                        leadingIcon = { Icon(Icons.Filled.RecordVoiceOver, contentDescription = null) }
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.filterDownloaded,
                        onClick = { viewModel.toggleDownloadedFilter() },
                        label = { Text("Downloaded") },
                        leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) }
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
                        0 -> HomeTabContent(uiState, viewModel, pagerState, scope, onBookClick, onAuthorClick, onSettingsClick)
                        1 -> AuthorsTabContent(uiState, viewModel)
                        2 -> SeriesTabContent(uiState, viewModel)
                        3 -> BooksTabContent(uiState, gridState, viewModel, onBookClick, importLauncher)
                    }
                }
                
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = uiState.isLoading,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        } // End of main Column
        
        // Local FAB
        val selectedTab = uiState.serverTabs.getOrNull(uiState.selectedTabIndex)
        val isLocalTab = selectedTab?.serviceType == com.owlsoda.pageportal.services.ServiceType.LOCAL
        val isBooksTab = pagerState.currentPage == 3
        
        if (isBooksTab && isLocalTab) {
            ExtendedFloatingActionButton(
                onClick = { 
                    importLauncher.launch(arrayOf("application/epub+zip", "audio/*", "video/mp4")) 
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Import Book") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            )
        }
    } // End of main Box
}

@Composable
fun HomeTabContent(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    pagerState: androidx.compose.foundation.pager.PagerState,
    scope: kotlinx.coroutines.CoroutineScope,
    onBookClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    if (uiState.books.isEmpty() && !uiState.isLoading) {
        EmptyLibraryState(uiState, onSettingsClick)
    } else {
        HomeView(
            selectedTabId = uiState.serverTabs.getOrNull(uiState.selectedTabIndex)?.id ?: -1L,
            recentBooks = uiState.continueReading,
            homeAuthors = uiState.homeAuthors,
            serviceMap = uiState.booksByService,
            onBookClick = onBookClick,
            onAuthorClick = onAuthorClick,
            onSeeAllClick = { tab ->
                val index = uiState.serverTabs.indexOf(tab)
                if (index != -1) {
                    viewModel.selectTab(index)
                    // If we need to scroll the pager:
                    scope.launch { pagerState.animateScrollToPage(3) } // Books tab
                }
            }
        )
    }
}

@Composable
fun AuthorsTabContent(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel
) {
    if (uiState.uniqueAuthors.isEmpty() && !uiState.isLoading) {
        EmptyState(icon = Icons.Filled.Person, title = "No Authors", message = "Authors will appear here once you have books.")
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
        EmptyState(icon = Icons.AutoMirrored.Filled.LibraryBooks, title = "No Series", message = "Series information will appear here once you have books.")
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
    onBookClick: (String) -> Unit,
    importLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    val selectedTab = uiState.serverTabs.getOrNull(uiState.selectedTabIndex)
    val isLocalTab = selectedTab?.serviceType == ServiceType.LOCAL

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
                        val iconVector = when {
                            tab.id == -1L -> Icons.AutoMirrored.Filled.LibraryBooks
                            tab.serviceType == ServiceType.AUDIOBOOKSHELF -> Icons.Filled.Headphones
                            tab.serviceType == ServiceType.STORYTELLER -> Icons.Filled.RecordVoiceOver
                            tab.serviceType == ServiceType.LOCAL -> Icons.Filled.Folder
                            else -> Icons.Filled.Link
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            // Hide text on narrow screens or many tabs? Let's keep it for now.
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = tab.name, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                )
            }
        }

        // Section Title for Local Tab (Visual Push)
        if (isLocalTab && uiState.books.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Personal Collection",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
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
            val selectedTab = uiState.serverTabs.getOrNull(uiState.selectedTabIndex)
            val isLocalTab = selectedTab?.serviceType == ServiceType.LOCAL

            if (uiState.books.isEmpty() && !uiState.isLoading) {
                LocalEmptyState(isLocalTab, viewModel, importLauncher)
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 20.dp, 
                        end = 20.dp, 
                        top = 20.dp, 
                        bottom = 80.dp // Extra padding for FAB
                    ),
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
}

@Composable
fun LocalEmptyState(
    isLocalTab: Boolean,
    viewModel: LibraryViewModel,
    importLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    if (isLocalTab) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.size(120.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Your Local Library is Empty",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Toss in your .epub or ReadAloud files to start building your personal collection.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { importLauncher.launch(arrayOf("application/epub+zip", "audio/*")) },
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Files to Import")
            }
        }
    } else {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.LibraryBooks, 
            title = "No Books Found", 
            message = "This server currently has no books matching your filters."
        )
    }
}

@Composable
fun EmptyLibraryState(uiState: LibraryUiState, onSettingsClick: () -> Unit) {
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
}

@Composable
fun HomeView(
    selectedTabId: Long,
    recentBooks: List<UnifiedBookDisplay>,
    homeAuthors: List<AuthorDisplay>,
    serviceMap: Map<ServerTab, List<UnifiedBookDisplay>>,
    onBookClick: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onSeeAllClick: (ServerTab) -> Unit
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
                    onAuthorClick = onAuthorClick,
                    isCompact = !isAllTab
                )
            }
        }
        
        // Authors Section (Scalable Grid if specific tab, Carousel if All)
        if (homeAuthors.isNotEmpty()) {
            item {
                HomeAuthorsSection(
                    authors = homeAuthors,
                    onAuthorClick = onAuthorClick,
                    isGrid = !isAllTab
                )
            }
        }
        
        // Service Rows (Only for "All" tab)
        if (isAllTab) {
            serviceMap.forEach { (tab, books) ->
                item {
                    ServiceCarousel(
                        title = tab.name,
                        books = books,
                        onBookClick = onBookClick,
                        onSeeAllClick = { onSeeAllClick(tab) }
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
    onAuthorClick: (String) -> Unit,
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
    onAuthorClick: (String) -> Unit,
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
                        onClick = { onAuthorClick(author.name) },
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
                        onClick = { onAuthorClick(author.name) },
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
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
    onBookClick: (String) -> Unit,
    onSeeAllClick: () -> Unit
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
            TextButton(onClick = onSeeAllClick) {
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

@OptIn(ExperimentalSharedTransitionApi::class)
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
        
        val sharedScope = com.owlsoda.pageportal.ui.theme.LocalSharedTransitionScope.current
        val animatedScope = com.owlsoda.pageportal.ui.theme.LocalNavAnimatedVisibilityScope.current
        
        var surfaceModifier: Modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.66f)
            
        if (sharedScope != null && animatedScope != null) {
            with(sharedScope) {
                surfaceModifier = surfaceModifier.sharedElement(
                    state = rememberSharedContentState(key = "cover_${book.id}"),
                    animatedVisibilityScope = animatedScope
                )
            }
        }
        
        Surface(
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 4.dp,
            modifier = surfaceModifier
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
                
                // Local/Device Badge (bottom left)
                if (book.isLocal) {
                    Box(modifier = Modifier.align(Alignment.BottomStart)) {
                        BadgeIcon(
                            Icons.Default.PhoneAndroid,
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        )
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
                val authorsList = remember(book.authors) {
                    book.authors.parseAuthors()
                }
                Text(
                    text = authorsList.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )
            }
            
            if (book.isDownloading || book.listeningProgress > 0) {
                val progressValue = if (book.isDownloading) book.downloadProgress else book.listeningProgress
                val progressColor = if (book.isDownloading) MaterialTheme.colorScheme.primary else PagePortalPurple
                
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
private fun BadgeIcon(
    icon: ImageVector,
    containerColor: Color = Color.Black.copy(alpha = 0.6f)
) {
    Surface(
        color = containerColor,
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
