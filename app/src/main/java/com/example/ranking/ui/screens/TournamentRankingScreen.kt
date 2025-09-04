package com.example.ranking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.ui.viewmodel.RankingViewModel

@Composable
fun TournamentRankingScreen(
    tournamentId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToResults: (Long) -> Unit,
    viewModel: RankingViewModel = viewModel()
) {
    android.util.Log.d("TournamentRankingScreen", "Screen loaded with tournament ID: $tournamentId")
    
    LaunchedEffect(tournamentId) {
        android.util.Log.d("TournamentRankingScreen", "Initializing tournament with ID: $tournamentId")
        viewModel.initializeTournament(tournamentId)
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val tournament = viewModel.getCurrentTournament()
    
    // Show loading or error states
    if (uiState.isLoading) {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }
    
    if (uiState.error != null) {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.Text(
                    text = "Hata",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error
                )
                androidx.compose.material3.Text(
                    text = uiState.error!!,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))
                androidx.compose.material3.Button(
                    onClick = { viewModel.initializeTournament(tournamentId) }
                ) {
                    androidx.compose.material3.Text("Tekrar Dene")
                }
            }
        }
        return
    }
    
    // Use the full-featured RankingScreen but adapt for tournament
    if (tournament != null) {
        RankingScreen(
            listId = tournament.songListId,
            method = tournament.systemType,
            pairingMethodName = "SEQUENTIAL",
            onNavigateBack = onNavigateBack,
            onNavigateToResults = { _, _ -> 
                onNavigateToResults(tournamentId)
            },
            onNavigateToFixture = { _, _ -> 
                // Could implement tournament fixture view
            },
            viewModel = viewModel
        )
    }
}