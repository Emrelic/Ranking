package com.example.ranking.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ranking.ui.screens.*

@Composable
fun RankingNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToCreateList = { navController.navigate("create_list") },
                onNavigateToSongList = { listId -> navController.navigate("song_list/$listId") },
                onNavigateToArchive = { navController.navigate("archive") },
                onNavigateToTest = { navController.navigate("test") }
            )
        }
        
        composable("create_list") {
            CreateListScreen(
                onNavigateBack = { navController.popBackStack() },
                onListCreated = { listId -> 
                    navController.navigate("song_list/$listId") {
                        popUpTo("home")
                    }
                }
            )
        }
        
        composable("song_list/{listId}") { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            SongListScreen(
                listId = listId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRanking = { id, method -> 
                    navController.navigate("ranking/$id/$method")
                },
                onNavigateToLeagueSettings = { id, method ->
                    navController.navigate("league_settings/$id/$method")
                }
            )
        }
        
        composable("league_settings/{listId}/{method}") { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            val method = backStackEntry.arguments?.getString("method") ?: ""
            LeagueSettingsScreen(
                listId = listId,
                method = method,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRanking = { id, m -> 
                    navController.navigate("ranking/$id/$m")
                }
            )
        }

        composable("fixture/{listId}/{method}") { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            val method = backStackEntry.arguments?.getString("method") ?: ""
            FixtureScreen(
                listId = listId,
                method = method,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRanking = { id, m -> 
                    navController.navigate("ranking/$id/$m")
                }
            )
        }

        composable("ranking/{listId}/{method}") { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            val method = backStackEntry.arguments?.getString("method") ?: ""
            RankingScreen(
                listId = listId,
                method = method,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToResults = { id, m -> 
                    navController.navigate("results/$id/$m")
                },
                onNavigateToFixture = { id, m ->
                    navController.navigate("fixture/$id/$m")
                }
            )
        }
        
        composable("results/{listId}/{method}") { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            val method = backStackEntry.arguments?.getString("method") ?: ""
            ResultsScreen(
                listId = listId,
                method = method,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToFixture = { id, m ->
                    navController.navigate("fixture/$id/$m")
                }
            )
        }
        
        composable("archive") {
            ArchiveScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("test") {
            TestScreen()
        }
    }
}