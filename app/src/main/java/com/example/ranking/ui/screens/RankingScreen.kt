package com.example.ranking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.data.Song
import com.example.ranking.ui.viewmodel.RankingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    listId: Long,
    method: String,
    pairingMethodName: String = "SEQUENTIAL",
    onNavigateBack: () -> Unit,
    onNavigateToResults: (Long, String) -> Unit,
    onNavigateToFixture: (Long, String) -> Unit = { _, _ -> },
    viewModel: RankingViewModel = viewModel()
) {
    LaunchedEffect(listId, method, pairingMethodName) {
        viewModel.initializeRanking(listId, method, pairingMethodName)
    }
    
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TopAppBar(
            title = { 
                Text(getMethodTitle(method))
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                }
            },
            actions = {
                // Session management buttons
                if (uiState.hasActiveSession) {
                    TextButton(
                        onClick = { viewModel.pauseSession() }
                    ) {
                        Text("Duraklat")
                    }
                    
                    TextButton(
                        onClick = { viewModel.deleteCurrentSession() }
                    ) {
                        Text("Sıfırla")
                    }
                }
                
                if (method in listOf("LEAGUE", "SWISS", "EMRE", "ELIMINATION", "FULL_ELIMINATION")) {
                    TextButton(
                        onClick = { onNavigateToFixture(listId, method) }
                    ) {
                        Text("Fikstür")
                    }
                }
                if (method == "LEAGUE" || method == "EMRE") {
                    var showStandings by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = { showStandings = !showStandings }
                    ) {
                        Text(if (showStandings) "Maçlar" else "Puan Durumu")
                    }
                    
                    if (showStandings) {
                        StandingsDialog(
                            uiState = uiState,
                            onDismiss = { showStandings = false }
                        )
                    }
                }
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Error handling
        uiState.error?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = "Hata: $error",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        when (method) {
            "DIRECT_SCORING" -> DirectScoringContent(
                uiState = uiState,
                allSongs = uiState.allSongs,
                onScoreSubmit = viewModel::submitDirectScore,
                onScoreUpdate = viewModel::updateScoreInSession,
                onComplete = { onNavigateToResults(listId, method) }
            )
            "LEAGUE", "SWISS", "EMRE" -> MatchBasedContent(
                uiState = uiState,
                method = method,
                onMatchResult = viewModel::submitMatchResult,
                onMatchResultWithScore = viewModel::submitMatchResultWithScore,
                onComplete = { onNavigateToResults(listId, method) }
            )
            "ELIMINATION" -> EliminationContent(
                uiState = uiState,
                onMatchResult = viewModel::submitMatchResult,
                onComplete = { onNavigateToResults(listId, method) }
            )
            "FULL_ELIMINATION" -> EliminationContent(
                uiState = uiState,
                onMatchResult = viewModel::submitMatchResult,
                onComplete = { onNavigateToResults(listId, method) }
            )
        }
    }
}

@Composable
private fun DirectScoringContent(
    uiState: RankingViewModel.RankingUiState,
    allSongs: List<Song> = emptyList(),
    onScoreSubmit: (Long, Double) -> Unit,
    onScoreUpdate: (Long, Double) -> Unit = { _, _ -> },
    onComplete: () -> Unit
) {
    if (uiState.isComplete) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Puanlama Tamamlandı!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onComplete) {
                Text("Sonuçları Görüntüle")
            }
        }
        return
    }
    
    uiState.currentSong?.let { song ->
        var scoreText by remember { mutableStateOf("") }
        
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = { uiState.progress },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "${uiState.currentIndex + 1} / ${uiState.totalCount}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = song.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    if (song.artist.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    if (song.album.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = song.album,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Bu öğeye 0-100 arası kaç puan veriyorsunuz?",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = scoreText,
                onValueChange = { 
                    if (it.all { char -> char.isDigit() || char == '.' }) {
                        scoreText = it
                    }
                },
                label = { Text("Puan (0-100)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(0.5f)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    val score = scoreText.toDoubleOrNull()
                    if (score != null && score in 0.0..100.0) {
                        onScoreSubmit(song.id, score)
                        scoreText = ""
                    }
                },
                enabled = scoreText.toDoubleOrNull()?.let { it in 0.0..100.0 } == true,
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text("Puanı Kaydet")
            }
            
            // Show completed scores if there are any
            if (uiState.hasActiveSession && uiState.currentIndex > 0) {
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "Verilen Puanlar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(uiState.completedScores.toList()) { (songId, score) ->
                        val song = allSongs.find { it.id == songId }
                        song?.let {
                            CompletedScoreItem(
                                song = it,
                                score = score,
                                onScoreUpdate = onScoreUpdate
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedScoreItem(
    song: Song,
    score: Double,
    onScoreUpdate: (Long, Double) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editScore by remember { mutableStateOf(score.toString()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                if (song.artist.isNotBlank()) {
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (isEditing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = editScore,
                        onValueChange = { 
                            if (it.all { char -> char.isDigit() || char == '.' }) {
                                editScore = it
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp),
                        singleLine = true
                    )
                    
                    IconButton(
                        onClick = {
                            val newScore = editScore.toDoubleOrNull()
                            if (newScore != null && newScore in 0.0..100.0) {
                                onScoreUpdate(song.id, newScore)
                                isEditing = false
                            }
                        }
                    ) {
                        Text("✓", style = MaterialTheme.typography.bodyLarge)
                    }
                    
                    IconButton(
                        onClick = { 
                            editScore = score.toString()
                            isEditing = false 
                        }
                    ) {
                        Text("✗", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = score.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(
                        onClick = { isEditing = true }
                    ) {
                        Text("✏️")
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchBasedContent(
    uiState: RankingViewModel.RankingUiState,
    method: String,
    onMatchResult: (Long, Long?) -> Unit,
    onMatchResultWithScore: (Long, Long?, Int?, Int?) -> Unit = { id, winner, _, _ -> onMatchResult(id, winner) },
    onComplete: () -> Unit
) {
    if (uiState.isComplete) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Eşleşmeler Tamamlandı!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onComplete) {
                Text("Sonuçları Görüntüle")
            }
        }
        return
    }
    
    uiState.currentMatch?.let { match ->
        var score1Text by remember { mutableStateOf("") }
        var score2Text by remember { mutableStateOf("") }
        val useScores = uiState.leagueSettings?.useScores ?: false
        
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = { uiState.progress },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "${uiState.completedMatches + 1} / ${uiState.totalMatches}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            if (method == "SWISS" || method == "EMRE") {
                Text(
                    text = "Tur: ${match.round}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = if (useScores) "Maç Skoru Girin" else "Hangisi daha iyi?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (useScores) {
                // Score input mode
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    uiState.song1?.let { song1 ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = song1.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (song1.artist.isNotBlank()) {
                                        Text(
                                            text = song1.artist,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = score1Text,
                                    onValueChange = { 
                                        if (it.all { char -> char.isDigit() }) {
                                            score1Text = it
                                        }
                                    },
                                    label = { Text("Skor") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(80.dp),
                                    singleLine = true
                                )
                            }
                        }
                    }
                    
                    Text(
                        text = "VS",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    
                    uiState.song2?.let { song2 ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = song2.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (song2.artist.isNotBlank()) {
                                        Text(
                                            text = song2.artist,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = score2Text,
                                    onValueChange = { 
                                        if (it.all { char -> char.isDigit() }) {
                                            score2Text = it
                                        }
                                    },
                                    label = { Text("Skor") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(80.dp),
                                    singleLine = true
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            val score1 = score1Text.toIntOrNull()
                            val score2 = score2Text.toIntOrNull()
                            
                            if (score1 != null && score2 != null) {
                                val winner = when {
                                    score1 > score2 -> uiState.song1?.id
                                    score2 > score1 -> uiState.song2?.id
                                    else -> null // Draw
                                }
                                onMatchResultWithScore(match.id, winner, score1, score2)
                                score1Text = ""
                                score2Text = ""
                            }
                        },
                        enabled = score1Text.toIntOrNull() != null && score2Text.toIntOrNull() != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Skoru Kaydet")
                    }
                }
            } else {
                // Traditional winner selection mode  
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    uiState.song1?.let { song1 ->
                        Button(
                            onClick = { onMatchResult(match.id, song1.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = song1.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (song1.artist.isNotBlank()) {
                                    Text(
                                        text = song1.artist,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                if (song1.album.isNotBlank()) {
                                    Text(
                                        text = song1.album,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                
                    Text(
                        text = "VS",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    
                    uiState.song2?.let { song2 ->
                        Button(
                            onClick = { onMatchResult(match.id, song2.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = song2.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (song2.artist.isNotBlank()) {
                                    Text(
                                        text = song2.artist,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                if (song2.album.isNotBlank()) {
                                    Text(
                                        text = song2.album,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                    
                    if (method == "LEAGUE" || method == "SWISS" || method == "EMRE") {
                        val allowDraws = uiState.leagueSettings?.allowDraws ?: true
                        if (allowDraws) {
                            Button(
                                onClick = { onMatchResult(match.id, null) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("Berabere")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EliminationContent(
    uiState: RankingViewModel.RankingUiState,
    onMatchResult: (Long, Long?) -> Unit,
    onComplete: () -> Unit
) {
    // Similar to MatchBasedContent but with elimination-specific UI
    MatchBasedContent(
        uiState = uiState,
        method = "ELIMINATION",
        onMatchResult = onMatchResult,
        onComplete = onComplete
    )
}

@Composable
private fun StandingsDialog(
    uiState: RankingViewModel.RankingUiState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anlık Puan Durumu") },
        text = {
            LazyColumn {
                item {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Takım",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(2f)
                        )
                        Text(
                            text = "O",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.5f)
                        )
                        Text(
                            text = "G",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.5f)
                        )
                        Text(
                            text = "B",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.5f)
                        )
                        Text(
                            text = "M",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.5f)
                        )
                        Text(
                            text = "A",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.5f)
                        )
                        Text(
                            text = "Y",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.5f)
                        )
                        Text(
                            text = "P",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.5f)
                        )
                    }
                }
                
                // Real standings data
                items(uiState.currentStandings) { standing ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${standing.position}. ${standing.song.name}",
                            modifier = Modifier.weight(2f),
                            maxLines = 1
                        )
                        Text(
                            text = standing.played.toString(),
                            modifier = Modifier.weight(0.5f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = standing.won.toString(),
                            modifier = Modifier.weight(0.5f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = standing.drawn.toString(),
                            modifier = Modifier.weight(0.5f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = standing.lost.toString(),
                            modifier = Modifier.weight(0.5f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "-",
                            modifier = Modifier.weight(0.5f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "-",
                            modifier = Modifier.weight(0.5f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = String.format("%.1f", standing.points),
                            modifier = Modifier.weight(0.5f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat")
            }
        }
    )
}

private fun getMethodTitle(method: String): String {
    return when (method) {
        "DIRECT_SCORING" -> "Direkt Puanlama"
        "LEAGUE" -> "Lig Sistemi"
        "ELIMINATION" -> "Eleme Sistemi"
        "FULL_ELIMINATION" -> "Tam Eleme Sistemi"
        "SWISS" -> "İsviçre Sistemi"
        "EMRE" -> "Geliştirilmiş İsviçre Sistemi"
        else -> "Sıralama"
    }
}