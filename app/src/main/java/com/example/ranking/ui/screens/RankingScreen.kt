package com.example.ranking.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.data.Song
import com.example.ranking.ui.viewmodel.RankingViewModel
import org.json.JSONObject

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
                
                if (method in listOf("LEAGUE", "SWISS", "EMRE_CORRECT", "ELIMINATION", "FULL_ELIMINATION")) {
                    TextButton(
                        onClick = { onNavigateToFixture(listId, method) }
                    ) {
                        Text("Fikstür")
                    }
                }
                if (method == "LEAGUE" || method == "EMRE_CORRECT") {
                    var showStandings by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = { showStandings = !showStandings }
                    ) {
                        Text(if (showStandings) "Maçlar" else "Puan Durumu")
                    }
                    
                    if (showStandings) {
                        StandingsDialog(
                            uiState = uiState,
                            method = method,
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
            "LEAGUE", "SWISS", "EMRE_CORRECT" -> MatchBasedContent(
                uiState = uiState,
                method = method,
                viewModel = viewModel,
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
                    TeamCardContent(
                        song = song,
                        modifier = Modifier.fillMaxWidth()
                    )
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
            TeamCardContent(
                song = song,
                modifier = Modifier
                    .width(600.dp) // Fixed large width for table display
            )
            
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
    viewModel: RankingViewModel = viewModel(),
    onMatchResult: (Long, Long?) -> Unit,
    onMatchResultWithScore: (Long, Long?, Int?, Int?) -> Unit = { id, winner, _, _ -> onMatchResult(id, winner) },
    onComplete: () -> Unit
) {
    // İlk sıralama tablosunu göster (EMRE_CORRECT için)
    if (method == "EMRE_CORRECT" && uiState.showInitialRanking) {
        InitialRankingContent(
            uiState = uiState,
            method = method,
            viewModel = viewModel
        )
        return
    }
    
    // Eşleştirmeler listesini göster (EMRE_CORRECT için)
    if (method == "EMRE_CORRECT" && uiState.showMatchingsList) {
        MatchingsListContent(
            uiState = uiState,
            viewModel = viewModel
        )
        return
    }
    
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
            
            if (method == "SWISS" || method == "EMRE_CORRECT") {
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
                                TeamCardContent(
                                    song = song1,
                                    modifier = Modifier
                                        .width(600.dp) // Fixed large width for table display
                                )
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
                    
                    // Vertical VS text (V above S)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "V",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "S",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
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
                                TeamCardContent(
                                    song = song2,
                                    modifier = Modifier
                                        .width(600.dp) // Fixed large width for table display
                                )
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
                        Box(
                            modifier = Modifier.height(140.dp) // Sabit yükseklik - simetrik çerçeve
                        ) {
                            TeamCardContent(
                                song = song1,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                onClick = { onMatchResult(match.id, song1.id) }
                            )
                            
                            // Puan göstergesi TAM KÖŞEDE - yazıları kapatmayacak şekilde
                            if (method == "EMRE_CORRECT") {
                                val currentPoints = if (uiState.emreState?.teams?.isNotEmpty() == true) {
                                    uiState.emreState.teams.find { it.song.id == song1.id }?.points ?: 0.0
                                } else {
                                    0.0
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 4.dp, y = 4.dp)
                                        .background(
                                            Color(0xFF4CAF50), // Green badge
                                            CircleShape
                                        )
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = if (currentPoints % 1.0 == 0.0) "${currentPoints.toInt()}p" else "${currentPoints}p",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }
                        }
                    }
                
                    // Vertical VS text (V above S)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "V",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "S",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    uiState.song2?.let { song2 ->
                        Box(
                            modifier = Modifier.height(140.dp) // Aynı sabit yükseklik - simetrik çerçeve
                        ) {
                            TeamCardContent(
                                song = song2,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                onClick = { onMatchResult(match.id, song2.id) }
                            )
                            
                            // Puan göstergesi TAM KÖŞEDE - yazıları kapatmayacak şekilde
                            if (method == "EMRE_CORRECT") {
                                val currentPoints = if (uiState.emreState?.teams?.isNotEmpty() == true) {
                                    uiState.emreState.teams.find { it.song.id == song2.id }?.points ?: 0.0
                                } else {
                                    0.0
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 4.dp, y = 4.dp)
                                        .background(
                                            Color(0xFF4CAF50), // Green badge
                                            CircleShape
                                        )
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = if (currentPoints % 1.0 == 0.0) "${currentPoints.toInt()}p" else "${currentPoints}p",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }
                        }
                    }
                    
                    if (method == "LEAGUE" || method == "SWISS" || method == "EMRE_CORRECT") {
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
    method: String,
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
                        if (method == "EMRE_CORRECT") {
                            Text(
                                text = "İP", // İkincil Puan
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(0.5f),
                                fontSize = 12.sp
                            )
                        }
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
                            overflow = TextOverflow.Ellipsis
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
                        if (method == "EMRE_CORRECT") {
                            // İkincil puan gösterimi - EmreState'den H2H puanını al
                            val secondaryPoints = uiState.emreState?.teams?.find { 
                                it.song.id == standing.song.id 
                            }?.secondaryPoints ?: 0.0
                            
                            Text(
                                text = String.format("%.1f", secondaryPoints),
                                modifier = Modifier.weight(0.5f),
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
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

@Composable
private fun InitialRankingContent(
    uiState: RankingViewModel.RankingUiState,
    method: String,
    viewModel: RankingViewModel = viewModel()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "İlk Sıralama Tablosu",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // İlk eşleştirmeleri yap butonu - YUKARI TAŞINDI
        Button(
            onClick = { 
                android.util.Log.d("InitialRankingContent", "🔥 BUTON BASILDI!")
                viewModel.createFirstRoundMatches() 
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("1. Tur Eşleştirmelerini Yap")
        }
        
        // EmreState'den takımları al
        uiState.emreState?.teams?.let { teams ->
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sıra",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(40.dp)
                        )
                        Text(
                            text = "ID",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(40.dp)
                        )
                        Text(
                            text = "Takım",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Puan",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(60.dp),
                            textAlign = TextAlign.End
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
                
                items(teams.sortedBy { it.currentPosition }) { team ->
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
                            // Anlık sıra
                            Text(
                                text = "${team.currentPosition}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(40.dp)
                            )
                            
                            // ID (sabit sıra numarası)
                            Text(
                                text = "${team.teamId}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(40.dp)
                            )
                            
                            // Takım bilgisi
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = team.song.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                if (team.song.artist.isNotBlank()) {
                                    Text(
                                        text = team.song.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            // Puan
                            Text(
                                text = "${team.points.toInt()}p",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(60.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchingsListContent(
    uiState: RankingViewModel.RankingUiState,
    viewModel: RankingViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Başlık - Dinamik tur numarası
        val currentRound = uiState.matchingsList.firstOrNull()?.round ?: 1
        Text(
            text = "${currentRound}. Tur Eşleştirmeleri",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Toplam eşleştirme sayısı - Debug log ekle
        Text(
            text = "${uiState.matchingsList.size} Eşleştirme Oluşturuldu",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // DEBUG: UI'da kaç eşleştirme var log'la
        android.util.Log.d("MatchingsListContent", "🔢 UI'da görünen eşleştirme sayısı: ${uiState.matchingsList.size}")
        uiState.matchingsList.forEachIndexed { index, match ->
            android.util.Log.d("MatchingsListContent", "UI Eşleştirme $index: ${match.songId1} vs ${match.songId2} (Round: ${match.round})")
        }
        
        // Eşleştirmeler listesi
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(uiState.matchingsList.sortedBy { match -> 
                if (match.matchNumber > 0) match.matchNumber else 999
            }.reversed()) { index, match ->
                val song1 = uiState.allSongs.find { it.id == match.songId1 }
                val song2 = uiState.allSongs.find { it.id == match.songId2 }
                
                Column {
                    // Eşleşme numarası - alternating match numbering kullan
                    val matchNumber = if (match.matchNumber > 0) match.matchNumber else index + 1
                    Text(
                        text = "${matchNumber}. Eşleşme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        
                        // İlk takım Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                song1?.let { song ->
                                    TeamCardContent(
                                        song = song,
                                        modifier = Modifier
                                            .padding(0.dp)
                                            .width(600.dp) // Fixed large width for table display
                                    )
                                }
                                
                                // Puan rozeti sol alt köşe (sadece EMRE_CORRECT için)
                                val team1Points = if (uiState.emreState?.teams?.isNotEmpty() == true) {
                                    uiState.emreState.teams.find { it.song.id == song1?.id }?.points ?: 0.0
                                } else {
                                    0.0
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                        .background(Color(0xFF4CAF50), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${team1Points.toInt()}p",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        
                        // Vertical VS text (V above S)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "V",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "S",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // İkinci takım Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                song2?.let { song ->
                                    TeamCardContent(
                                        song = song,
                                        modifier = Modifier
                                            .padding(0.dp)
                                            .width(600.dp) // Fixed large width for table display
                                    )
                                }
                                
                                // Puan rozeti sağ alt köşe (sadece EMRE_CORRECT için)
                                val team2Points = if (uiState.emreState?.teams?.isNotEmpty() == true) {
                                    uiState.emreState.teams.find { it.song.id == song2?.id }?.points ?: 0.0
                                } else {
                                    0.0
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .background(Color(0xFF4CAF50), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${team2Points.toInt()}p",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Puanlama ekranına geç butonu
        Button(
            onClick = { 
                android.util.Log.d("MatchingsListContent", "🎯 Puanlama ekranına geç butonu basıldı!")
                viewModel.startScoring() 
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "Puanlama Ekranına Geç",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
internal fun TeamCardContent(
    song: Song,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    // Debug CSV data
    android.util.Log.d("TeamCardContent", "Song: ${song.name}, CSV data exists: ${song.csvData != null}, CSV data: '${song.csvData}'")
    
    Column(
        modifier = modifier
    ) {
        // Ana başlık (song name)
        Text(
            text = song.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        
        // Eğer artist varsa göster
        if (song.artist.isNotBlank()) {
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // CSV tabular data varsa display mode'a göre göster
        song.csvData?.let { csvData ->
            if (csvData.isNotBlank()) {
                android.util.Log.d("TeamCardContent", "Showing CSV table for song: ${song.name}")
                Spacer(modifier = Modifier.height(8.dp))
                CsvDataTable(
                    csvData = csvData, 
                    teamPoints = extractPointsFromCsv(csvData),
                    onClick = onClick
                )
            }
        }
    }
}

@Composable 
private fun CsvDataTable(
    csvData: String, 
    teamPoints: Double = 0.0,
    onClick: (() -> Unit)? = null
) {
    val parsedData = remember(csvData) {
        parseCsvDataToMap(csvData)
    }
    
    android.util.Log.d("CsvDataTable", "📊 CSV data: $csvData")
    android.util.Log.d("CsvDataTable", "📊 Parsed data size: ${parsedData.size}")
    android.util.Log.d("CsvDataTable", "📊 Parsed data: $parsedData")
    
    if (parsedData.isNotEmpty()) {
        // Green table format with header and scrollable rows - CLICKABLE
        Box(
            modifier = onClick?.let { 
                Modifier.clickable { it() } 
            } ?: Modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 700.dp) // Larger table width
            ) {
                // Header with team name (first value from CSV)
                val teamName = parsedData.values.firstOrNull() ?: "Team"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1B5E20)) // Very dark green header (button-like)
                        .padding(horizontal = 12.dp, vertical = 10.dp), // Slightly larger padding
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = teamName.uppercase(),
                        style = MaterialTheme.typography.titleMedium, // Larger header text
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
                
                // Scrollable data rows with alternating green colors
                val dataRows = parsedData.entries.drop(1)
                    .filterNot { (key, _) -> key.startsWith("_") } // Skip metadata like _displayMode
                
                // Use LazyColumn only if many rows, otherwise regular Column
                val shouldScroll = dataRows.size > 8 // Scroll only if more than 8 rows
                
                if (shouldScroll) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 0.dp, max = 500.dp), // Larger max height
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        itemsIndexed(dataRows.toList()) { index, (key, value) ->
                            TableRow(index, key, value)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        dataRows.forEachIndexed { index, (key, value) ->
                            TableRow(index, key, value)
                        }
                    }
                }
            }
            
            // Orange circular point badge at top-right corner
            if (teamPoints > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-8).dp)
                        .size(24.dp)
                        .background(
                            color = Color(0xFFFF9800), // Orange
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${teamPoints.toInt()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun parseCsvDataToMap(csvData: String): Map<String, String> {
    return try {
        val jsonObject = JSONObject(csvData)
        val keys = jsonObject.keys().asSequence().toList()
        val result = mutableMapOf<String, String>()
        
        // Her sütun başlığını satır etiketi yap, değerini karşısına yazdır
        keys.forEach { key ->
            val value = jsonObject.optString(key, "")
            if (value.isNotBlank()) {
                result[key] = value
            }
        }
        
        result
    } catch (e: Exception) {
        emptyMap()
    }
}

private fun extractPointsFromCsv(csvData: String): Double {
    return try {
        val jsonObject = JSONObject(csvData)
        // Possible point field names (in order of preference)
        val pointFields = listOf("Puan", "puan", "Point", "point", "Points", "points", "Score", "score", "Skor", "skor")
        
        for (field in pointFields) {
            val value = jsonObject.optString(field, "")
            if (value.isNotBlank()) {
                return value.toDoubleOrNull() ?: 0.0
            }
        }
        0.0
    } catch (e: Exception) {
        0.0
    }
}

private fun extractDisplayModeFromCsv(csvData: String): String {
    return try {
        val jsonObject = JSONObject(csvData)
        jsonObject.optString("_displayMode", "cards")
    } catch (e: Exception) {
        "cards"
    }
}

@Composable
private fun TableRow(index: Int, key: String, value: String) {
    val backgroundColor = when {
        index % 2 == 0 -> Color(0xFF388E3C) // Darker green for even rows (button-like)
        else -> Color(0xFF4CAF50) // Medium-dark green for odd rows
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 8.dp), // Slightly larger padding
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodyMedium, // Larger text
            fontWeight = FontWeight.Medium,
            color = Color.White, // White text for better contrast
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium, // Larger text
            color = Color.White, // White text for better contrast
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FullTableDisplay(csvData: String) {
    val parsedData = remember(csvData) {
        parseCsvDataToMap(csvData).filterKeys { !it.startsWith("_") } // Remove metadata
    }
    
    if (parsedData.isNotEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(min = 700.dp), // Larger width consistent with CsvDataTable
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFE8F5E8) // Light green background
            )
        ) {
            Column {
                // Green header with team name
                val teamName = parsedData.values.firstOrNull() ?: "Team"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1B5E20)) // Very dark green header - consistent
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = teamName.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                // Table rows with alternating colors
                parsedData.entries.drop(1).forEachIndexed { index, (key, value) ->
                    val backgroundColor = when {
                        index % 2 == 0 -> Color(0xFF388E3C) // Darker green for even rows - consistent
                        else -> Color(0xFF4CAF50) // Medium-dark green for odd rows - consistent
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(backgroundColor)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.White, // White text for better contrast
                            modifier = Modifier.weight(1.5f)
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White, // White text for better contrast
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private fun getMethodTitle(method: String): String {
    return when (method) {
        "DIRECT_SCORING" -> "Direkt Puanlama"
        "LEAGUE" -> "Lig Sistemi"
        "ELIMINATION" -> "Eleme Sistemi"
        "FULL_ELIMINATION" -> "Tam Eleme Sistemi"
        "SWISS" -> "İsviçre Sistemi"
        "EMRE_CORRECT" -> "Geliştirilmiş İsviçre Sistemi"
        else -> "Sıralama"
    }
}