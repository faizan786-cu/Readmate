package com.example.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.components.AppNavigationDrawer
import com.example.ui.screens.about.AboutScreen
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.book.BookDetailScreen
import com.example.ui.screens.book.BookFormScreen
import com.example.ui.screens.chapter.ChapterFormScreen
import com.example.ui.screens.chat.ChapterChatScreen
import com.example.ui.screens.chat.ChapterResponseScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.progress.ProgressScreen
import com.example.ui.screens.quiz.ChapterMasteryQuizScreen
import com.example.ui.screens.quiz.DailyRecallScreen
import com.example.ui.screens.quiz.WordVaultQuizScreen
import com.example.ui.screens.reels.WisdomReelsScreen
import com.example.ui.screens.settings.ApiKeyManagementScreen
import com.example.ui.screens.settings.GeminiConfigScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.vault.WordVaultScreen
import kotlinx.coroutines.launch

@Composable
fun ReadMateNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Dashboard.route
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route

    AppNavigationDrawer(
        drawerState = drawerState,
        currentRoute = currentRoute,
        onNavigateToDashboard = {
            coroutineScope.launch { drawerState.close() }
            if (currentRoute != Screen.Dashboard.route) {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Dashboard.route) { inclusive = true }
                }
            }
        },
        onNavigateToLibrary = {
            coroutineScope.launch { drawerState.close() }
            if (currentRoute != Screen.Library.route) {
                navController.navigate(Screen.Library.route) {
                    launchSingleTop = true
                }
            }
        },
        onNavigateToWordVault = {
            coroutineScope.launch { drawerState.close() }
            if (!currentRoute.startsWith("wordvault")) {
                navController.navigate(Screen.WordVault.createRoute()) {
                    launchSingleTop = true
                }
            }
        },
        onNavigateToProgress = {
            coroutineScope.launch { drawerState.close() }
            if (currentRoute != Screen.Progress.route) {
                navController.navigate(Screen.Progress.route) {
                    launchSingleTop = true
                }
            }
        },
        onNavigateToSettings = {
            coroutineScope.launch { drawerState.close() }
            if (currentRoute != Screen.Settings.route) {
                navController.navigate(Screen.Settings.route) {
                    launchSingleTop = true
                }
            }
        },
        onNavigateToAbout = {
            coroutineScope.launch { drawerState.close() }
            if (currentRoute != Screen.About.route) {
                navController.navigate(Screen.About.route) {
                    launchSingleTop = true
                }
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = modifier,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(280))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(240))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(280))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(240))
            }
        ) {
            // Dashboard Screen
            composable(route = Screen.Dashboard.route) {
                DashboardScreen(
                    onOpenDrawer = {
                        coroutineScope.launch { drawerState.open() }
                    },
                    onNavigateToLibrary = {
                        navController.navigate(Screen.Library.route)
                    },
                    onNavigateToWordVault = {
                        navController.navigate(Screen.WordVault.createRoute())
                    },
                    onNavigateToWisdomReels = { quoteId ->
                        navController.navigate(Screen.WisdomReels.createRoute(quoteId))
                    },
                    onNavigateToDailyRecall = {
                        navController.navigate(Screen.DailyRecall.route)
                    },
                    onNavigateToWordQuiz = {
                        navController.navigate(Screen.WordVaultQuiz.route)
                    },
                    onNavigateToChapterMasteryQuiz = { chapterId ->
                        navController.navigate(Screen.ChapterMasteryQuiz.createRoute(chapterId))
                    },
                    onNavigateToProgress = {
                        navController.navigate(Screen.Progress.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToCreateBook = {
                        navController.navigate(Screen.BookCreate.route)
                    },
                    onNavigateToChapterChat = { chapterId ->
                        navController.navigate(Screen.ChapterChat.createRoute(chapterId))
                    }
                )
            }

            // Library Screen
            composable(route = Screen.Library.route) {
                LibraryScreen(
                    onOpenDrawer = {
                        coroutineScope.launch { drawerState.open() }
                    },
                    onNavigateToCreateBook = {
                        navController.navigate(Screen.BookCreate.route)
                    },
                    onNavigateToBookDetail = { bookId ->
                        navController.navigate(Screen.BookDetail.createRoute(bookId))
                    },
                    onNavigateToWisdomReels = {
                        navController.navigate(Screen.WisdomReels.createRoute())
                    }
                )
            }

            // Auth Screen
            composable(route = Screen.Auth.route) {
                AuthScreen(
                    onAuthSuccess = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                        }
                    }
                )
            }

            // Settings Screen
            composable(route = Screen.Settings.route) {
                SettingsScreen(
                    onOpenDrawer = {
                        coroutineScope.launch { drawerState.open() }
                    },
                    onNavigateToApiManagement = {
                        navController.navigate(Screen.ApiKeyManagement.route)
                    },
                    onNavigateToDashboard = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = true }
                        }
                    },
                    onNavigateToAuth = {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToAbout = {
                        navController.navigate(Screen.About.route)
                    }
                )
            }

            // Dedicated API Key Management Screen
            composable(route = Screen.ApiKeyManagement.route) {
                ApiKeyManagementScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Gemini API Config Screen (legacy route alias)
            composable(route = Screen.GeminiConfig.route) {
                ApiKeyManagementScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Create Book Screen
            composable(route = Screen.BookCreate.route) {
                BookFormScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onBookSaved = { newBookId ->
                        navController.navigate(Screen.BookDetail.createRoute(newBookId)) {
                            popUpTo(Screen.BookCreate.route) { inclusive = true }
                        }
                    }
                )
            }

            // Book Detail Screen
            composable(
                route = Screen.BookDetail.route,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.LongType }
                )
            ) {
                BookDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEditBook = { bookId ->
                        navController.navigate(Screen.BookEdit.createRoute(bookId))
                    },
                    onNavigateToAddChapter = { bookId ->
                        navController.navigate(Screen.ChapterCreate.createRoute(bookId))
                    },
                    onNavigateToEditChapter = { chapterId ->
                        navController.navigate(Screen.ChapterEdit.createRoute(chapterId))
                    },
                    onNavigateToChapterChat = { chapterId ->
                        navController.navigate(Screen.ChapterChat.createRoute(chapterId))
                    },
                    onNavigateToDailyRecall = {
                        navController.navigate(Screen.DailyRecall.route)
                    },
                    onNavigateToChapterMastery = { chapterId ->
                        navController.navigate(Screen.ChapterMasteryQuiz.createRoute(chapterId))
                    },
                    onNavigateToProgress = {
                        navController.navigate(Screen.Progress.route)
                    }
                )
            }

            // Edit Book Screen
            composable(
                route = Screen.BookEdit.route,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.LongType }
                )
            ) {
                BookFormScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onBookSaved = {
                        navController.popBackStack()
                    }
                )
            }

            // Create Chapter Screen
            composable(
                route = Screen.ChapterCreate.route,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.LongType }
                )
            ) {
                ChapterFormScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onChapterSaved = { navController.popBackStack() }
                )
            }

            // Edit Chapter Screen
            composable(
                route = Screen.ChapterEdit.route,
                arguments = listOf(
                    navArgument("chapterId") { type = NavType.LongType }
                )
            ) {
                ChapterFormScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onChapterSaved = { navController.popBackStack() }
                )
            }

            // Chapter Chat Screen
            composable(
                route = Screen.ChapterChat.route,
                arguments = listOf(
                    navArgument("chapterId") { type = NavType.LongType },
                    navArgument("targetMessageId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val chapterId = backStackEntry.arguments?.getLong("chapterId") ?: -1L
                ChapterChatScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToWordVault = {
                        navController.navigate(Screen.WordVault.createRoute(chapterId = chapterId))
                    },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToChapter = { targetChapterId ->
                        navController.navigate(Screen.ChapterChat.createRoute(chapterId = targetChapterId))
                    },
                    onNavigateToResponse = { entryIndex ->
                        navController.navigate(Screen.ChapterResponse.createRoute(chapterId, entryIndex))
                    },
                    onNavigateToChapterMasteryQuiz = { chId ->
                        navController.navigate(Screen.ChapterMasteryQuiz.createRoute(chId))
                    }
                )
            }

            // Chapter Response Screen (Full-Screen Editorial Document)
            composable(
                route = Screen.ChapterResponse.route,
                arguments = listOf(
                    navArgument("chapterId") { type = NavType.LongType },
                    navArgument("initialIndex") {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                ),
                enterTransition = {
                    fadeIn(animationSpec = tween(250)) + scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(250, easing = FastOutSlowInEasing)
                    )
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(200)) + scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(200, easing = FastOutSlowInEasing)
                    )
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(250)) + scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(250, easing = FastOutSlowInEasing)
                    )
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(200)) + scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(200, easing = FastOutSlowInEasing)
                    )
                }
            ) { backStackEntry ->
                val chapterId = backStackEntry.arguments?.getLong("chapterId") ?: -1L
                val initialIndex = backStackEntry.arguments?.getInt("initialIndex") ?: 0
                ChapterResponseScreen(
                    onNavigateBack = { navController.popBackStack() },
                    initialIndex = initialIndex,
                    onNavigateToWordVault = {
                        navController.navigate(Screen.WordVault.createRoute(chapterId = chapterId))
                    }
                )
            }

            // Word Vault Screen
            composable(
                route = Screen.WordVault.route,
                arguments = listOf(
                    navArgument("chapterId") {
                        type = NavType.StringType
                        defaultValue = "-1"
                        nullable = true
                    },
                    navArgument("bookId") {
                        type = NavType.StringType
                        defaultValue = "-1"
                        nullable = true
                    }
                )
            ) {
                WordVaultScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToWordQuiz = {
                        navController.navigate(Screen.WordVaultQuiz.route)
                    }
                )
            }

            // Wisdom Reels Screen
            composable(
                route = Screen.WisdomReels.route,
                arguments = listOf(
                    navArgument("quoteId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val quoteIdStr = backStackEntry.arguments?.getString("quoteId")
                val initialQuoteId = quoteIdStr?.toLongOrNull()
                WisdomReelsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToChat = { chapterId, messageId ->
                        navController.navigate(Screen.ChapterChat.createRoute(chapterId, messageId))
                    },
                    initialQuoteId = initialQuoteId
                )
            }

            // Progress & Analytics Screen
            composable(route = Screen.Progress.route) {
                ProgressScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDailyRecall = {
                        navController.navigate(Screen.DailyRecall.route)
                    },
                    onNavigateToWordQuiz = {
                        navController.navigate(Screen.WordVaultQuiz.route)
                    },
                    onNavigateToChapterMastery = { chapterId ->
                        navController.navigate(Screen.ChapterMasteryQuiz.createRoute(chapterId))
                    },
                    onNavigateToLibrary = {
                        navController.navigate(Screen.Library.route)
                    },
                    onOpenDrawer = {
                        coroutineScope.launch { drawerState.open() }
                    }
                )
            }

            // Daily Active Recall Quiz Screen
            composable(route = Screen.DailyRecall.route) {
                DailyRecallScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToProgress = {
                        navController.navigate(Screen.Progress.route) {
                            popUpTo(Screen.DailyRecall.route) { inclusive = true }
                        }
                    },
                    onNavigateToWordQuiz = {
                        navController.navigate(Screen.WordVaultQuiz.route) {
                            popUpTo(Screen.DailyRecall.route) { inclusive = true }
                        }
                    }
                )
            }

            // Word Vault 100% Offline Quiz Screen
            composable(route = Screen.WordVaultQuiz.route) {
                WordVaultQuizScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToProgress = {
                        navController.navigate(Screen.Progress.route) {
                            popUpTo(Screen.WordVaultQuiz.route) { inclusive = true }
                        }
                    },
                    onNavigateToDailyRecall = {
                        navController.navigate(Screen.DailyRecall.route) {
                            popUpTo(Screen.WordVaultQuiz.route) { inclusive = true }
                        }
                    }
                )
            }

            // Chapter Grand Mastery Quiz Screen (50-MCQ Engine)
            composable(
                route = Screen.ChapterMasteryQuiz.route,
                arguments = listOf(
                    navArgument("chapterId") { type = NavType.LongType }
                )
            ) {
                ChapterMasteryQuizScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToProgress = {
                        navController.navigate(Screen.Progress.route) {
                            popUpTo(Screen.ChapterMasteryQuiz.route) { inclusive = true }
                        }
                    },
                    onNavigateToNextChapter = { nextChapterId ->
                        navController.navigate(Screen.ChapterChat.createRoute(nextChapterId)) {
                            popUpTo(Screen.ChapterMasteryQuiz.route) { inclusive = true }
                        }
                    }
                )
            }

            // Dedicated Full-Page About Screen & Developer Attribution Architecture
            composable(
                route = Screen.About.route,
                enterTransition = {
                    fadeIn(animationSpec = tween(250)) + scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(250, easing = FastOutSlowInEasing)
                    )
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(200)) + scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(200, easing = FastOutSlowInEasing)
                    )
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(250)) + scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(250, easing = FastOutSlowInEasing)
                    )
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(200)) + scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(200, easing = FastOutSlowInEasing)
                    )
                }
            ) {
                AboutScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}

