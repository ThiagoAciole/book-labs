package com.example.booklabs

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.booklabs.ui.home.HomeScreen
import com.example.booklabs.ui.reader.ReaderScreen
import com.example.booklabs.ui.theme.BookLabsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BookLabsTheme {
                val navController = rememberNavController()
                
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onComicClick = { comic ->
                                val encodedUri = Uri.encode(comic.path)
                                val encodedFileName = Uri.encode(comic.fileName)
                                val encodedTitle = Uri.encode(comic.title)
                                navController.navigate("reader/$encodedUri/$encodedFileName/$encodedTitle")
                            },
                            onSettingsClick = { /* Handle settings */ },
                            onDownloadClick = { /* Handle download */ }
                        )
                    }
                    
                    composable(
                        route = "reader/{uri}/{fileName}/{title}",
                        arguments = listOf(
                            navArgument("uri") { type = NavType.StringType },
                            navArgument("fileName") { type = NavType.StringType },
                            navArgument("title") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val uri = backStackEntry.arguments?.getString("uri") ?: ""
                        val fileName = backStackEntry.arguments?.getString("fileName") ?: ""
                        val title = backStackEntry.arguments?.getString("title") ?: ""
                        
                        ReaderScreen(
                            comicUri = uri,
                            comicFileName = fileName,
                            comicTitle = title, // Use title for display
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}