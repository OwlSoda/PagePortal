package com.owlsoda.pageportal.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.owlsoda.pageportal.features.auth.LoginScreen
import com.owlsoda.pageportal.features.book.BookDetailScreen
import com.owlsoda.pageportal.features.comic.ComicReaderScreen
import com.owlsoda.pageportal.features.library.LibraryScreen
import com.owlsoda.pageportal.features.player.AudiobookPlayerScreen
import java.net.URLDecoder
import java.net.URLEncoder

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
    object ComicReader : Screen("comic/{bookId}?filePath={filePath}") {
        fun createRoute(bookId: String, filePath: String): String {
            val encodedPath = URLEncoder.encode(filePath, "UTF-8")
            return "comic/$bookId?filePath=$encodedPath"
        }
    }
    object Settings : Screen("settings")
    object ServerManagement : Screen("servers")
    object MatchReview : Screen("matches")
}

@Composable
fun PagePortalNavHost() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = Screen.Library.route
    ) {
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
        
        composable(Screen.Library.route) {
            LibraryScreen(
                onBookClick = { bookId ->
                    navController.navigate(Screen.BookDetail.createRoute(bookId))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        composable(Screen.Settings.route) {
            com.owlsoda.pageportal.features.settings.SettingsScreen(
                onBack = { navController.popBackStack() },
                onServersClick = { navController.navigate(Screen.ServerManagement.route) },
                onMatchReviewClick = { navController.navigate(Screen.MatchReview.route) }
            )
        }

        composable(Screen.ServerManagement.route) {
            com.owlsoda.pageportal.features.settings.ServerManagementScreen(
                onBack = { navController.popBackStack() },
                onAddServer = {
                    navController.navigate(Screen.Login.createRoute(addAccount = true))
                }
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
                    navController.navigate(Screen.AudiobookPlayer.createRoute(id))
                },
                onReadEbook = { id ->
                    navController.navigate(Screen.Reader.createRoute(id))
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
}
