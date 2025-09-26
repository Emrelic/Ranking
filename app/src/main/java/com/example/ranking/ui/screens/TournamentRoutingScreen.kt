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
        viewModel.loadTournament(tournamentId) { tournament ->
            if (tournament != null) {
                // Navigate directly to classic RankingScreen for all system types
                onNavigateToClassicRanking(tournament.songListId, tournament.systemType)
            } else {
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