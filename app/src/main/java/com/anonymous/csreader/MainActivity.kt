package com.anonymous.csreader

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.anonymous.csreader.data.AppDatabase
import com.anonymous.csreader.data.BookEntity
import com.anonymous.csreader.data.SettingsManager
import com.anonymous.csreader.ui.screens.LibraryScreen
import com.anonymous.csreader.ui.screens.NotesScreen
import com.anonymous.csreader.ui.screens.ReaderScreen
import com.anonymous.csreader.ui.screens.SettingsScreen
import com.anonymous.csreader.ui.theme.CsReaderTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);

        // Enable edge-to-edge drawing
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val database = AppDatabase.getDatabase(this)
        val settingsManager = SettingsManager(this)

        setContent {
            var themeName by remember { mutableStateOf(settingsManager.theme) }

            CsReaderTheme(themeName = themeName) {
                // Update status bar icons dynamically
                val view = LocalView.current
                val context = LocalContext.current
                LaunchedEffect(themeName) {
                    val activityWindow = (context as? Activity)?.window
                    if (activityWindow != null) {
                        val controller = WindowCompat.getInsetsController(activityWindow, view)
                        controller.isAppearanceLightStatusBars = themeName != "dark"
                    }
                }

                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "library") {
                    composable("library") {
                        LibraryScreen(
                            bookDao = database.bookDao(),
                            onSelectBook = { book ->
                                navController.navigate("reader/${book.id}")
                            },
                            onNavigateToNotes = {
                                navController.navigate("notes")
                            },
                            onNavigateToSettings = {
                                navController.navigate("settings")
                            }
                        )
                    }

                    composable(
                        route = "reader/{bookId}?cfi={cfi}&page={page}",
                        arguments = listOf(
                            navArgument("bookId") { type = NavType.StringType },
                            navArgument("cfi") { type = NavType.StringType; nullable = true; defaultValue = null },
                            navArgument("page") { type = NavType.IntType; defaultValue = -1 }
                        )
                    ) { backStackEntry ->
                        val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
                        val cfi = backStackEntry.arguments?.getString("cfi")
                        val pageVal = backStackEntry.arguments?.getInt("page") ?: -1
                        val page = if (pageVal == -1) null else pageVal

                        val bookState = produceState<BookEntity?>(initialValue = null, key1 = bookId) {
                            value = database.bookDao().getBookById(bookId)
                        }

                        val book = bookState.value
                        if (book != null) {
                            val resolvedBook = remember(book, cfi, page) {
                                book.copy(
                                    lastCfi = cfi ?: book.lastCfi,
                                    lastPage = page ?: book.lastPage
                                )
                            }

                            ReaderScreen(
                                bookDao = database.bookDao(),
                                highlightDao = database.highlightDao(),
                                settingsManager = settingsManager,
                                book = resolvedBook,
                                onBack = { navController.popBackStack() }
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    composable("notes") {
                        NotesScreen(
                            highlightDao = database.highlightDao(),
                            bookDao = database.bookDao(),
                            onBack = { navController.popBackStack() },
                            onNavigateToBookAtLocation = { book, cfi, page ->
                                val cfiQuery = if (cfi != null) "&cfi=$cfi" else ""
                                val pageQuery = if (page != null) "&page=$page" else ""
                                navController.navigate("reader/${book.id}?$cfiQuery$pageQuery")
                            }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            settingsManager = settingsManager,
                            bookDao = database.bookDao(),
                            highlightDao = database.highlightDao(),
                            db = database,
                            onBack = { navController.popBackStack() },
                            onDatabaseCleared = {
                                themeName = "light"
                            },
                            onThemeChanged = { newTheme ->
                                themeName = newTheme
                            }
                        )
                    }
                }
            }
        }
    }
}
