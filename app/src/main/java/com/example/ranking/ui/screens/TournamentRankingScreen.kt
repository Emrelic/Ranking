package com.example.ranking.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.ui.viewmodel.TournamentRankingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentRankingScreen(
    tournamentId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToResults: (Long) -> Unit,
    onNavigateToRanking: (Long, String) -> Unit = { _, _ -> },
    viewModel: TournamentRankingViewModel = viewModel()
) {
    LaunchedEffect(tournamentId) {
        android.util.Log.d("TournamentRankingScreen", "LaunchedEffect triggered for tournamentId: $tournamentId")
        android.util.Log.d("TournamentRankingScreen", "ViewModel class: ${viewModel::class.java.simpleName}")
        viewModel.initializeTournament(tournamentId)
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val tournament = uiState.tournament
    val currentMatch = uiState.currentMatch
    val standings = uiState.standings
    val criteriaSettings = uiState.criteriaSettings
    val showCriteriaDialog by viewModel.showCriteriaDialog.collectAsState()
    
    // Handle redirect to working RankingScreen
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            if (error.startsWith("REDIRECT_TO_RANKING_SCREEN:")) {
                val parts = error.split(":")
                if (parts.size >= 3) {
                    val listId = parts[1].toLongOrNull() ?: 0L
                    val systemType = parts[2]
                    android.util.Log.d("TournamentRankingScreen", "Redirecting to RankingScreen: listId=$listId, systemType=$systemType")
                    onNavigateToRanking(listId, systemType)
                }
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { 
                Column {
                    Text(tournament?.name ?: "Turnuva")
                    if (tournament != null) {
                        Text(
                            text = "${tournament.systemType} - ${if (tournament.criterionListId != null) "Kriterli" else "Kritersiz"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                }
            },
            actions = {
                if (uiState.isCompleted) {
                    TextButton(onClick = { tournament?.id?.let(onNavigateToResults) }) {
                        Text("Sonuçlar")
                    }
                }
            }
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.errorMessage != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Hata",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = uiState.errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.initializeTournament(tournamentId) }) {
                        Text("Tekrar Dene")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Current match section
                if (currentMatch != null && !uiState.isCompleted) {
                    item {
                        CurrentMatchCard(
                            match = currentMatch,
                            hasCriteria = tournament?.criterionListId != null,
                            onTeam1Win = { viewModel.recordMatchResult(currentMatch.id, 1) },
                            onTeam2Win = { viewModel.recordMatchResult(currentMatch.id, 2) },
                            onDraw = { viewModel.recordMatchResult(currentMatch.id, 0) },
                            onOpenCriteria = { viewModel.openCriteriaDialog() }
                        )
                    }
                } else if (uiState.isCompleted) {
                    item {
                        CompletionCard(
                            onViewResults = { tournament?.id?.let(onNavigateToResults) }
                        )
                    }
                }

                // Standings section
                if (standings.isNotEmpty()) {
                    item {
                        Text(
                            text = "Sıralama",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    items(standings.take(10)) { standing ->
                        StandingCard(standing = standing)
                    }
                }
            }
        }

        // Criteria evaluation dialog
        if (showCriteriaDialog && currentMatch != null && criteriaSettings != null) {
            CriteriaEvaluationDialog(
                match = currentMatch,
                criteriaSettings = criteriaSettings,
                criterionScores = uiState.currentMatchCriteriaScores,
                onDismiss = { viewModel.closeCriteriaDialog() },
                onSaveScores = { scores ->
                    viewModel.saveCriteriaScores(currentMatch.id, scores)
                },
                onMatchResult = { result ->
                    viewModel.recordMatchResultFromCriteria(currentMatch.id, result)
                    viewModel.closeCriteriaDialog()
                }
            )
        }
    }
}

@Composable
private fun CurrentMatchCard(
    match: Any, // Will be properly typed
    hasCriteria: Boolean,
    onTeam1Win: () -> Unit,
    onTeam2Win: () -> Unit,
    onDraw: () -> Unit,
    onOpenCriteria: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Şu Anki Maç",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Match teams display would go here
            Text(
                text = "Takım A vs Takım B", // Placeholder
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Voting buttons
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onTeam1Win,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Takım A")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = onDraw,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Berabere")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = onTeam2Win,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Takım B")
                }
            }
            
            // Criteria button
            if (hasCriteria) {
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = onOpenCriteria,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kriterler ile Değerlendir")
                }
            }
        }
    }
}

@Composable
private fun CompletionCard(
    onViewResults: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "🎉 Turnuva Tamamlandı!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(onClick = onViewResults) {
                Text("Sonuçları Görüntüle")
            }
        }
    }
}

@Composable
private fun StandingCard(
    standing: Any // Will be properly typed
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "1. Placeholder Team", // Will show actual data
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "0 puan", // Will show actual points
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CriteriaEvaluationDialog(
    match: Any,
    criteriaSettings: Any,
    criterionScores: List<Any>,
    onDismiss: () -> Unit,
    onSaveScores: (Map<String, Pair<Double?, Double?>>) -> Unit,
    onMatchResult: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Kriter Değerlendirmesi",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Criteria scoring will be implemented here
                Text(
                    text = "Kriter skorlaması burada olacak",
                    modifier = Modifier.weight(1f)
                )
                
                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("İptal")
                    }
                    
                    Button(onClick = { onSaveScores(emptyMap()) }) {
                        Text("Kaydet")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { onMatchResult(1) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Takım A Galip")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = { onMatchResult(0) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Berabere")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = { onMatchResult(2) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Takım B Galip")
                    }
                }
            }
        }
    }
}