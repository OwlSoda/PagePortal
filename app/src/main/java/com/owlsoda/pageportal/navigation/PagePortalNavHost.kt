package com.owlsoda.pageportal.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.owlsoda.pageportal.features.auth.LoginScreen
import com.owlsoda.pageportal.features.book.BookDetailScreen
import com.owlsoda.pageportal.features.comic.ComicReaderScreen
import com.owlsoda.pageportal.features.library.LibraryScreen
import com.owlsoda.pageportal.features.player.AudiobookPlayerScreen
import com.owlsoda.pageportal.features.player.ReadAloudPlayerScreen
import com.owlsoda.pageportal.features.reader.ReaderScreen
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Settings
import java.net.URLDecoder
import java.net.URLEncoder
import androidx.compose.ui.res.painterResource
import com.owlsoda.pageportal.R

sealed class Screen(val route: String) {
    object Login : Screen("login?addAccount={addAccount}") {
        fun createRoute(addAccount: Boolean = false) = "login?addAccount=$addAccount"
    }
    object Library : Screen("library")
    object BookDetail : Screen("book/{bookId}") {
        fun createRoute(bookId: String) = "book/$bookId"
    }
    object AudiobookPlayer : Screen("player/{bookId}") {
        fun createRoute(bookId: String) = "player/$bookId"
    }
    object Reader : Screen("reader/{bookId}?isReadAloud={isReadAloud}") {
        fun createRoute(bookId: String, isReadAloud: Boolean = false) = 
            "reader/$bookId?isReadAloud=$isReadAloud"
    }
    object ReadAloudPlayer : Screen("readaloud_player/{bookId}") {
        fun createRoute(bookId: String) = "readaloud_player/$bookId"
    }
    object ComicReader : Screen("comic/{bookId}?filePath={filePath}") {
        fun createRoute(bookId: String, filePath: String): String {
            val encodedPath = URLEncoder.encode(filePath, "UTF-8")
            return "comic/$bookId?filePath=$encodedPath"
        }
    }
    object Browse : Screen("browse")
    object FilteredBooks : Screen("filtered_books?type={type}&value={value}") {
        fun createRoute(type: String, value: String): String {
             val encodedValue = URLEncoder.encode(value, "UTF-8")
             return "filtered_books?type=$type&value=$encodedValue"
        }
    }
    object Settings : Screen("settings")
    object ServerManagement : Screen("servers")
    object StorageManagement : Screen("storage")
    object ServiceTest : Screen("test_service/{serverId}") {
        fun createRoute(serverId: Long) = "test_service/$serverId"
    }
    object MatchReview : Screen("matches")
}

@Composable
fun PagePortalNavHost(
    audiobookPlayerViewModel: com.owlsoda.pageportal.features.player.AudiobookPlayerViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    mainViewModel: com.owlsoda.pageportal.MainViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentService = navBackStackEntry?.arguments?.getString("serviceName")
    
    // Main State
    val mainState by mainViewModel.uiState.collectAsState()
    
    // MiniPlayer State
    val playerState by audiobookPlayerViewModel.state.collectAsState()
    val isPlayerVisible = playerState.isPlaying || playerState.currentPosition > 0 || playerState.title.isNotEmpty()
    
    // Determine screen type for showing bars
    // Check if the current route matches generic service pattern or is a specific main tab
    val isMainTab = currentRoute in listOf(Screen.Library.route) || 
                    currentRoute == "service/{serviceName}"
                    

    val showBottomBar = isMainTab
    val showMiniPlayer = isPlayerVisible && 
        currentRoute?.startsWith("player/") != true && 
        currentRoute?.startsWith("reader/") != true &&
        currentRoute?.startsWith("comic/") != true &&
        currentRoute?.startsWith("login") != true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    // Unified Home
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Home") },
                        selected = currentRoute == Screen.Library.route,
                        onClick = { 
                            navController.navigate(Screen.Library.route) { 
                                popUpTo(Screen.Library.route) { inclusive = true }
                                launchSingleTop = true 
                            } 
                        }
                    )
                    
// ... imports

                    // Dynamic Service Tabs
                    if (mainState.connectedServiceTypes.contains(com.owlsoda.pageportal.services.ServiceType.STORYTELLER)) {
                        NavigationBarItem(
                            icon = { Icon(painterResource(id = R.drawable.ic_storyteller), contentDescription = null) },
                            label = { Text("Storyteller") },
                            selected = currentService == "Storyteller",
                            onClick = { 
                                navController.navigate("service/Storyteller") { 
                                    popUpTo(Screen.Library.route) 
                                    launchSingleTop = true 
                                } 
                            }
                        )
                    }

                    if (mainState.connectedServiceTypes.contains(com.owlsoda.pageportal.services.ServiceType.AUDIOBOOKSHELF)) {
                        NavigationBarItem(
                            icon = { Icon(painterResource(id = R.drawable.ic_audiobookshelf), contentDescription = null) },
                            label = { Text("ABS") },
                            selected = currentService == "Audiobookshelf",
                            onClick = { 
                                navController.navigate("service/Audiobookshelf") { 
                                    popUpTo(Screen.Library.route) 
                                    launchSingleTop = true 
                                } 
                            }
                        )
                    }

                    if (mainState.connectedServiceTypes.contains(com.owlsoda.pageportal.services.ServiceType.BOOKLORE)) {
                        NavigationBarItem(
                            icon = { Icon(painterResource(id = R.drawable.ic_booklore), contentDescription = null) },
                            label = { Text("Booklore") },
                            selected = currentService == "Booklore",
                            onClick = { 
                                navController.navigate("service/Booklore") { 
                                    popUpTo(Screen.Library.route) 
                                    launchSingleTop = true 
                                } 
                            }
                        )
                    }
                    
                     NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("Settings") },
                        selected = currentRoute == Screen.Settings.route,
                        onClick = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Library.route,
                modifier = Modifier.fillMaxSize().padding(bottom = if (showMiniPlayer) 88.dp else 0.dp)
            ) {
                // Unified Home
                composable(Screen.Library.route) {
                    com.owlsoda.pageportal.features.home.UnifiedHomeScreen(
                        onBookClick = { bookId ->
                             navController.navigate(Screen.BookDetail.createRoute(bookId))
                        },
                        onNavigateToService = { service ->
                             navController.navigate("service/$service")
                        }
                    )
                }
                
                // Generic Service Route
                composable(
                    route = "service/{serviceName}",
                    arguments = listOf(navArgument("serviceName") { type = NavType.StringType })
                ) { backStackEntry ->
                    val serviceName = backStackEntry.arguments?.getString("serviceName") ?: ""
                    com.owlsoda.pageportal.features.service.ServiceScreen(
                        serviceType = serviceName,
                        onBookClick = { bookId ->
                             navController.navigate(Screen.BookDetail.createRoute(bookId))
                        }
                    )
                }

                composable(
                    route = Screen.Login.route,
                    arguments = listOf(navArgument("addAccount") { defaultValue = false })
                ) {
                    com.owlsoda.pageportal.features.auth.LoginScreen(
                        onLoginSuccess = {
                            val wasAddingAccount = navController.previousBackStackEntry?.destination?.route == Screen.ServerManagement.route
                            if (wasAddingAccount) {
                                 navController.popBackStack()
                            } else {
                                navController.navigate(Screen.Library.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            }
                        }
                    )
                }
                
                composable(Screen.Browse.route) {
                    com.owlsoda.pageportal.features.browse.BrowseScreen(
                        onBack = { navController.popBackStack() },
                        onEntityClick = { type, id ->
                            navController.navigate(Screen.FilteredBooks.createRoute(type, id))
                        }
                    )
                }
                
                composable(
                    route = Screen.FilteredBooks.route,
                    arguments = listOf(
                        navArgument("type") { type = NavType.StringType },
                        navArgument("value") { type = NavType.StringType }
                    )
                ) {
                     com.owlsoda.pageportal.features.book.BookListScreen(
                        onBack = { navController.popBackStack() },
                        onBookClick = { bookId ->
                             navController.navigate(Screen.BookDetail.createRoute(bookId))
                        }
                     )
                }
                
                composable(Screen.Settings.route) {
                    com.owlsoda.pageportal.features.settings.SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onServersClick = { navController.navigate(Screen.ServerManagement.route) },
                        onMatchReviewClick = { navController.navigate(Screen.MatchReview.route) },
                        onStorageClick = { navController.navigate(Screen.StorageManagement.route) }
                    )
                }
        
                composable(Screen.ServerManagement.route) {
                    com.owlsoda.pageportal.features.settings.ServerManagementScreen(
                        onBack = { navController.popBackStack() },
                        onAddServer = {
                            navController.navigate(Screen.Login.createRoute(addAccount = true))
                        },
                        onTestServer = { serverId ->
                            navController.navigate(Screen.ServiceTest.createRoute(serverId))
                        }
                    )
                }
                
                composable(Screen.StorageManagement.route) {
                    com.owlsoda.pageportal.features.settings.storage.StorageManagementScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                
                composable(
                    route = Screen.ServiceTest.route,
                    arguments = listOf(navArgument("serverId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val serverId = backStackEntry.arguments?.getLong("serverId") ?: return@composable
                    com.owlsoda.pageportal.features.testing.ServiceTestScreen(
                        serverId = serverId,
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable(
                    route = Screen.BookDetail.route,
                    arguments = listOf(navArgument("bookId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                    BookDetailScreen(
                        bookId = bookId,
                        onBack = { navController.popBackStack() },
                        onPlayAudiobook = { id ->
                            audiobookPlayerViewModel.loadBook(id) // Preload
                            navController.navigate(Screen.AudiobookPlayer.createRoute(id))
                        },
                        onReadEbook = { id ->
                            navController.navigate(Screen.Reader.createRoute(id))
                        },
                        onPlayReadAloud = { id ->
                            // Use Reader Screen for True ReadAloud (Text+Audio)
                            navController.navigate(Screen.Reader.createRoute(id, true))
                        }
                    )
                }
                
                composable(
                    route = Screen.AudiobookPlayer.route,
                    arguments = listOf(navArgument("bookId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                    AudiobookPlayerScreen(
                        bookId = bookId,
                        onBack = { navController.popBackStack() },
                        viewModel = audiobookPlayerViewModel
                    )
                }
                
                composable(
                    route = Screen.Reader.route,
                    arguments = listOf(
                        navArgument("bookId") { type = NavType.StringType },
                        navArgument("isReadAloud") { 
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    )
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                    val isReadAloud = backStackEntry.arguments?.getBoolean("isReadAloud") ?: false
                    
                    ReaderScreen(
                        bookId = bookId,
                        isReadAloud = isReadAloud,
                        onBack = { navController.popBackStack() }
                    )
                }
                
                // Keep the old player route just in case, but BookDetail will redirect to Reader now
                composable(
                    route = Screen.ReadAloudPlayer.route,
                    arguments = listOf(navArgument("bookId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                    ReadAloudPlayerScreen(
                        bookId = bookId,
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable(
                    route = Screen.ComicReader.route,
                    arguments = listOf(
                        navArgument("bookId") { type = NavType.StringType },
                        navArgument("filePath") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                    val filePath = backStackEntry.arguments?.getString("filePath")?.let {
                        URLDecoder.decode(it, "UTF-8")
                    } ?: return@composable
                    
                    ComicReaderScreen(
                        bookId = bookId,
                        filePath = filePath,
                        onBack = { navController.popBackStack() }
                    )
                }
                
                // Placeholders
                composable(Screen.MatchReview.route) {
                     com.owlsoda.pageportal.features.settings.MatchReviewScreen(
                         onBack = { navController.popBackStack() }
                     )
                }
            }
            
            // Persistent Mini Player
            com.owlsoda.pageportal.features.player.MiniPlayerBar(
                isVisible = showMiniPlayer,
                bookTitle = playerState.title,
                bookAuthor = playerState.author,
                chapterTitle = playerState.chapters.getOrNull(playerState.currentChapterIndex)?.title,
                coverUrl = playerState.coverUrl,
                isPlaying = playerState.isPlaying,
                progress = if (playerState.duration > 0) playerState.currentPosition.toFloat() / playerState.duration else 0f,
                onPlayPauseClick = { audiobookPlayerViewModel.togglePlayPause() },
                onStopClick = { audiobookPlayerViewModel.pause() }, // Simple stop for now
                onBarClick = { 
                    if (playerState.bookId.isNotEmpty()) {
                        navController.navigate(Screen.AudiobookPlayer.createRoute(playerState.bookId))
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
