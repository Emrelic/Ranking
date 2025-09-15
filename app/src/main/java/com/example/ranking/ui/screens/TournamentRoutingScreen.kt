package com.example.ranking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.ui.viewmodel.TournamentRoutingViewModel

@Composable
fun TournamentRoutingScreen(
    tournamentId: Long,
    onNavigateToTournamentRanking: (Long) -> Unit,
    onNavigateToClassicRanking: (Long, String) -> Unit,
    viewModel: TournamentRoutingViewModel = viewModel()
) {
    LaunchedEffect(tournamentId) {
        android.util.Log.d("TournamentRouting", "Loading tournament with ID: $tournamentId")
        viewModel.loadTournament(tournamentId) { tournament ->
            if (tournament != null) {
                android.util.Log.d("TournamentRouting", "Tournament loaded: ${tournament.name}, System: ${tournament.systemType}")
                // Navigate directly to classic RankingScreen for all system types
                android.util.Log.d("TournamentRouting", "Navigating to ClassicRanking: listId=${tournament.songListId}, system=${tournament.systemType}")
                onNavigateToClassicRanking(tournament.songListId, tournament.systemType)
            } else {
                android.util.Log.e("TournamentRouting", "Failed to load tournament with ID: $tournamentId")
            }
        }
    }
    
    // Show loading while routing
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Turnuva yükleniyor...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}