package com.example.ranking.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    android.util.Log.d("RankingNavigation", "🗺️ RANKING NAVIGATION COMPOSABLE CALLED!")
    NavHost(
        navController = navController,
        startDestination = "main_menu"
    ) {
        composable("main_menu") {
            android.util.Log.d("RankingNavigation", "🏠 MAIN_MENU COMPOSABLE REACHED!")
            MainMenuScreen(
                onNavigateToLists = { navController.navigate("lists") },
                onNavigateToCriteria = { 
                    android.util.Log.d("RankingNavigation", "🌟 NAVIGATE TO CRITERIA CLICKED!")
                    android.util.Log.d("RankingNavigation", "🌟 Current backstack: ${navController.currentBackStackEntry?.destination?.route}")
                    try {
                        navController.navigate("criteria")
                        android.util.Log.d("RankingNavigation", "🌟 Navigate criteria SUCCESSFUL!")
                    } catch (e: Exception) {
                        android.util.Log.e("RankingNavigation", "❌ Navigate criteria FAILED: ${e.message}", e)
                    }
                },
                onNavigateToActiveTournaments = { navController.navigate("active_tournaments") },
                onNavigateToArchive = { navController.navigate("archive") },
                onNavigateToNewTournament = { navController.navigate("new_tournament_direct") }
            )
        }
        
        composable("lists") {
            ListsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCreateList = { navController.navigate("create_list") },
                onNavigateToSongList = { listId -> navController.navigate("song_list/$listId") }
            )
        }
        
        composable("criteria") {
            android.util.Log.d("RankingNavigation", "🌟 CRİTERİA COMPOSABLE REACHED!")
            
            // SIFIRDAN BASIT KRITERLER SAYFASI
            SimpleCriteriaScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCreateCriteria = { navController.navigate("create_criteria") }
            )
            
            android.util.Log.d("RankingNavigation", "🌟 SIMPLE CRİTERİA SCREEN SUCCESS!")
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
        
        // Keep old home route for backward compatibility during development
        composable("home") {
            HomeScreen(
                onNavigateToCreateList = { navController.navigate("create_list") },
                onNavigateToSongList = { listId -> navController.navigate("song_list/$listId") },
                onNavigateToArchive = { navController.navigate("archive") },
                onNavigateToTest = { navController.navigate("test") },
                onNavigateToRanking = { listId, method -> navController.navigate("ranking/$listId/$method") }
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
                },
                onNavigateToEmrePairingSettings = { id ->
                    navController.navigate("emre_pairing_settings/$id")
                }
            )
        }
        
        composable("new_tournament/{listId}") { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            NewTournamentScreen(
                onNavigateBack = { navController.popBackStack() },
                onTournamentCreated = { tournamentId ->
                    // Navigate to tournament-based ranking
                    navController.navigate("tournament_ranking/$tournamentId") {
                        popUpTo("main_menu")
                    }
                },
                onClassicSystemSelected = { listId, systemType ->
                    // Navigate to classic ranking screen
                    when (systemType) {
                        "LEAGUE" -> navController.navigate("league_settings/$listId/$systemType") {
                            popUpTo("main_menu")
                        }
                        "EMRE_CORRECT" -> navController.navigate("emre_pairing_settings/$listId") {
                            popUpTo("main_menu")
                        }
                        else -> navController.navigate("ranking/$listId/$systemType") {
                            popUpTo("main_menu")
                        }
                    }
                }
            )
        }
        
        // Direct tournament creation from main menu
        composable("new_tournament_direct") {
            NewTournamentScreen(
                onNavigateBack = { navController.popBackStack() },
                onTournamentCreated = { tournamentId ->
                    // Navigate to tournament-based ranking
                    android.util.Log.d("RankingNavigation", "Direct - onTournamentCreated called with ID: $tournamentId")
                    android.util.Log.d("RankingNavigation", "Direct - Navigating to tournament_ranking/$tournamentId")
                    navController.navigate("tournament_ranking/$tournamentId") {
                        popUpTo("main_menu")
                    }
                },
                onClassicSystemSelected = { listId, systemType ->
                    // Navigate to classic ranking screen
                    when (systemType) {
                        "LEAGUE" -> navController.navigate("league_settings/$listId/$systemType") {
                            popUpTo("main_menu")
                        }
                        "EMRE_CORRECT" -> navController.navigate("emre_pairing_settings/$listId") {
                            popUpTo("main_menu")
                        }
                        else -> navController.navigate("ranking/$listId/$systemType") {
                            popUpTo("main_menu")
                        }
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

        composable("emre_pairing_settings/{listId}") { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            EmrePairingSettingsScreen(
                listId = listId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRanking = { id, method, pairingMethod -> 
                    navController.navigate("ranking/$id/$method?pairingMethod=${pairingMethod.name}")
                }
            )
        }

        composable(
            "ranking/{listId}/{method}?pairingMethod={pairingMethod}",
            arguments = listOf(
                navArgument("pairingMethod") { 
                    type = NavType.StringType
                    defaultValue = "SEQUENTIAL"
                }
            )
        ) { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId")?.toLongOrNull() ?: 0L
            val method = backStackEntry.arguments?.getString("method") ?: ""
            val pairingMethodName = backStackEntry.arguments?.getString("pairingMethod") ?: "SEQUENTIAL"
            RankingScreen(
                listId = listId,
                method = method,
                pairingMethodName = pairingMethodName,
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

// SIFIRDAN BASIT KRITERLER SAYFASI - ViewModel yok, sadece UI
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleCriteriaScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCreateCriteria: () -> Unit
) {
    android.util.Log.d("SimpleCriteriaScreen", "🎯 SIMPLE CRITERIA SCREEN BAŞLADI!")
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kriterler") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToCreateCriteria) {
                        Icon(Icons.Default.Add, contentDescription = "Yeni Kriter")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Kriterler Sayfası",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sıfırdan yazılan basit version",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onNavigateToCreateCriteria) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Yeni Kriter Listesi Oluştur")
            }
        }
    }
}