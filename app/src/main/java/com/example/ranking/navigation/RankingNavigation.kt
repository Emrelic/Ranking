package com.example.ranking.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.ranking.ui.screens.*

@Composable
fun RankingNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "main_menu"
    ) {
        composable("main_menu") {
            MainMenuScreen(
                onNavigateToLists = { navController.navigate("lists") },
                onNavigateToCriteria = { navController.navigate("criteria") },
                onNavigateToActiveTournaments = { navController.navigate("active_tournaments") },
                onNavigateToArchive = { navController.navigate("archive") },
                onNavigateToNewTournament = { navController.navigate("new_tournament_direct") },
                onNavigateToAbout = { navController.navigate("about") },
                onNavigateToYouTubeAnalysis = { navController.navigate("youtube_analysis") },
                onNavigateToHazirListeler = { navController.navigate("hazir_listeler") }
            )
        }

        composable("hazir_listeler") {
            HazirListelerScreen(
                onNavigateBack = { navController.popBackStack() },
                onListImported = { listId ->
                    navController.navigate("list_view/$listId")
                }
            )
        }
        
        composable("lists") {
            ListsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCreateList = { navController.navigate("create_list") },
                onNavigateToSongList = { listId -> navController.navigate("list_view/$listId") }
            )
        }
        
        composable("criteria") {
            CriteriaScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCreateCriteria = { navController.navigate("create_criteria") },
                onNavigateToEditCriteria = { criteriaId -> navController.navigate("edit_criteria/$criteriaId") }
            )
        }
        
        composable("create_criteria") {
            CreateCriteriaScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("edit_criteria/{criteriaId}") { backStackEntry ->
            val criteriaId = backStackEntry.arguments?.getString("criteriaId")?.toLongOrNull() ?: 0L
            CreateCriteriaScreen(
                onNavigateBack = { navController.popBackStack() },
                criteriaListId = criteriaId
            )
        }
        
        composable("youtube_analysis") {
            YouTubeAnalysisScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRanking = { listId, method ->
                    navController.navigate("ranking/$listId/$method") {
                        popUpTo("main_menu")
                    }
                }
            )
        }
        
        composable("create_list") {
            CreateListScreen(
                onNavigateBack = { navController.popBackStack() },
                onListCreated = { listId -> 
                    navController.navigate("list_view/$listId") {
                        popUpTo("lists")
                    }
                }
            )
        }
        
        composable("new_tournament/{listId}") { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            NewTournamentScreen(
                onNavigateBack = { navController.popBackStack() },
                onTournamentCreated = { tournamentId ->
                    // Navigate based on system type
                    navController.navigate("tournament_routing/$tournamentId") {
                        popUpTo("main_menu")
                    }
                }
            )
        }
        
        // Direct tournament creation from main menu
        composable("new_tournament_direct") {
            NewTournamentScreen(
                onNavigateBack = { navController.popBackStack() },
                onTournamentCreated = { tournamentId ->
                    // Directly navigate to classic ranking screen to bypass tournament routing issues
                    navController.navigate("tournament_routing/$tournamentId") {
                        popUpTo("main_menu")
                    }
                }
            )
        }
        
        // Tournament routing - redirect to appropriate screen based on system type
        composable("tournament_routing/{tournamentId}") { backStackEntry ->
            val tournamentId = backStackEntry.arguments?.getString("tournamentId")?.toLongOrNull() ?: 0L
            TournamentRoutingScreen(
                tournamentId = tournamentId,
                onNavigateToTournamentRanking = { tId ->
                    navController.navigate("tournament_ranking/$tId") {
                        popUpTo("main_menu")
                    }
                },
                onNavigateToClassicRanking = { listId, systemType ->
                    navController.navigate("ranking/$listId/$systemType") {
                        popUpTo("main_menu")
                    }
                }
            )
        }
        
        composable("tournament_ranking/{tournamentId}") { backStackEntry ->
            val tournamentId = backStackEntry.arguments?.getString("tournamentId")?.toLongOrNull() ?: 0L
            TournamentRankingScreen(
                tournamentId = tournamentId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToResults = { tId ->
                    navController.navigate("tournament_results/$tId")
                },
                onNavigateToRanking = { listId, systemType ->
                    // Redirect to working RankingScreen system
                    navController.navigate("ranking/$listId/$systemType") {
                        popUpTo("main_menu")
                    }
                }
            )
        }
        
        composable("active_tournaments") {
            ActiveTournamentsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTournament = { tournamentId ->
                    navController.navigate("tournament_ranking/$tournamentId")
                }
            )
        }
        


        composable(
            "ranking/{listId}/{method}?pairingMethod={pairingMethod}&isResuming={isResuming}",
            arguments = listOf(
                navArgument("pairingMethod") { 
                    type = NavType.StringType
                    defaultValue = "SEQUENTIAL"
                },
                navArgument("isResuming") { 
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            val method = backStackEntry.arguments?.getString("method") ?: ""
            val pairingMethodName = backStackEntry.arguments?.getString("pairingMethod") ?: "SEQUENTIAL"
            val isResuming = backStackEntry.arguments?.getBoolean("isResuming") ?: false
            RankingScreen(
                listId = listId,
                method = method,
                pairingMethodName = pairingMethodName,
                isResuming = isResuming,
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
        
        composable("about") {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("archive") {
            ArchiveScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        
        // List view screen - only shows content, no tournament creation
        composable("list_view/{listId}") { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            ListViewScreen(
                listId = listId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { navController.navigate("list_edit/$listId") }
            )
        }
        
        // List edit screen - table editing functionality
        composable("list_edit/{listId}") { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            ListEditScreen(
                listId = listId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}