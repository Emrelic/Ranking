package com.example.ranking.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
        
        // 🆕 KRİTER PUANLAMA TAM EKRAN ENTEGRASYONU
        if (uiState.showCriteriaScoring) {
            val currentMatch = uiState.currentMatch
            val song1 = uiState.song1
            val song2 = uiState.song2
            
            android.util.Log.d("RankingScreen", "🎯 CRITERIA DIALOG CONDITIONS:")
            android.util.Log.d("RankingScreen", "showCriteriaScoring: ${uiState.showCriteriaScoring}")
            android.util.Log.d("RankingScreen", "criteriaNames: ${uiState.criteriaNames}")
            android.util.Log.d("RankingScreen", "currentMatch: $currentMatch")
            android.util.Log.d("RankingScreen", "song1: ${song1?.name}")
            android.util.Log.d("RankingScreen", "song2: ${song2?.name}")
            
            if (currentMatch != null && song1 != null && song2 != null && uiState.criteriaNames.isNotEmpty()) {
                CriteriaScoringScreen(
                    criteriaNames = uiState.criteriaNames,
                    team1Name = song1.name,
                    team2Name = song2.name,
                    onSave = { criteriaScores ->
                        viewModel.saveCriteriaScores(criteriaScores)
                    },
                    onDismiss = {
                        viewModel.closeCriteriaScoring()
                    }
                )
            } else {
                // Fallback - hata durumunda dialog'u kapat
                android.util.Log.e("RankingScreen", "❌ CRITERIA DIALOG CONDITIONS NOT MET - CLOSING")
                viewModel.closeCriteriaScoring()
            }
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
                        Box(
                            modifier = Modifier.height(140.dp) // Sabit yükseklik - simetrik çerçeve
                        ) {
                            Button(
                                onClick = { onMatchResult(match.id, song1.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp, vertical = 8.dp), // Eşit padding
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center // Ortada hizalama
                                ) {
                                    // Padişah ismi - BÜYÜK FONT - TAM GÖRÜNÜR
                                    Text(
                                        text = song1.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        softWrap = true
                                    )
                                    if (song1.artist.isNotBlank()) {
                                        Text(
                                            text = song1.artist,
                                            style = MaterialTheme.typography.titleMedium,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            softWrap = true
                                        )
                                    }
                                    if (song1.album.isNotBlank()) {
                                        Text(
                                            text = song1.album,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            softWrap = true
                                        )
                                    }
                                }
                            }
                            
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
                                        .offset(x = 4.dp, y = 4.dp) // Tam köşeye yerleştir
                                        .background(
                                            Color(0xFFFF9800), // Turuncu/Amber
                                            RoundedCornerShape(8.dp) // Küçük border radius
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp) // Kompakt padding
                                ) {
                                    Text(
                                        text = if (currentPoints % 1.0 == 0.0) "${currentPoints.toInt()}p" else "${currentPoints}p",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
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
                        Box(
                            modifier = Modifier.height(140.dp) // Aynı sabit yükseklik - simetrik çerçeve
                        ) {
                            Button(
                                onClick = { onMatchResult(match.id, song2.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp, vertical = 8.dp), // Aynı eşit padding
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center // Aynı ortada hizalama
                                ) {
                                    // Padişah ismi - BÜYÜK FONT - TAM GÖRÜNÜR
                                    Text(
                                        text = song2.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        softWrap = true
                                    )
                                    if (song2.artist.isNotBlank()) {
                                        Text(
                                            text = song2.artist,
                                            style = MaterialTheme.typography.titleMedium,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            softWrap = true
                                        )
                                    }
                                    if (song2.album.isNotBlank()) {
                                        Text(
                                            text = song2.album,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            softWrap = true
                                        )
                                    }
                                }
                            }
                            
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
                                        .offset(x = 4.dp, y = 4.dp) // Tam köşeye yerleştir
                                        .background(
                                            Color(0xFFFF9800), // Turuncu/Amber
                                            RoundedCornerShape(8.dp) // Küçük border radius
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp) // Kompakt padding
                                ) {
                                    Text(
                                        text = if (currentPoints % 1.0 == 0.0) "${currentPoints.toInt()}p" else "${currentPoints}p",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
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
                    
                    // 🆕 KRİTER PUANLAMA BUTONU
                    if (method == "EMRE_CORRECT" && uiState.criteriaNames.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.openCriteriaScoring() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text("📊 Kriter Puanlaması")
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
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = song1?.name ?: "Bilinmiyor",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        softWrap = true
                                    )
                                    if (song1?.artist?.isNotBlank() == true) {
                                        Text(
                                            text = song1.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            softWrap = true
                                        )
                                    }
                                    if (song1?.album?.isNotBlank() == true) {
                                        Text(
                                            text = song1.album,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            textAlign = TextAlign.Center,
                                            softWrap = true
                                        )
                                    }
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
                        
                        // VS
                        Text(
                            text = "VS",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        
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
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = song2?.name ?: "Bilinmiyor",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        softWrap = true
                                    )
                                    if (song2?.artist?.isNotBlank() == true) {
                                        Text(
                                            text = song2.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            softWrap = true
                                        )
                                    }
                                    if (song2?.album?.isNotBlank() == true) {
                                        Text(
                                            text = song2.album,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            textAlign = TextAlign.Center,
                                            softWrap = true
                                        )
                                    }
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

// ===================================================================================
// 🆕 KRİTER PUANLAMA UI BİLEŞENLERİ
// ===================================================================================

/**
 * Kriter puanlama tam ekran sayfası - Kıyaslamalı ve Ayrı Ayrı puanlama desteği ile
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CriteriaScoringScreen(
    criteriaNames: List<String>,
    team1Name: String,
    team2Name: String,
    onSave: (Map<String, Pair<Double?, Double?>>) -> Unit,
    onDismiss: () -> Unit
) {
    var criteriaScores by remember { 
        mutableStateOf(criteriaNames.associateWith { Pair(null as Double?, null as Double?) }) 
    }
    var isComparative by remember { mutableStateOf(false) }
    var maxPoints by remember { mutableStateOf(100) }
    var customPoints by remember { mutableStateOf("") }
    
    // Tam ekran Surface ile wrap etelim
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header bar
            TopAppBar(
                title = { 
                    Text("📊 Kriter Puanlaması") 
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onSave(criteriaScores) },
                        enabled = criteriaScores.all { (_, scores) -> 
                            scores.first != null && scores.second != null 
                        }
                    ) {
                        Text("Kaydet", fontWeight = FontWeight.Bold)
                    }
                }
            )
            
            // Settings bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Puanlama Modu",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isComparative) "Kıyaslamalı (Puan bölüştürme)" else "Ayrı Ayrı (Bağımsız puanlama)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        
                        Switch(
                            checked = isComparative,
                            onCheckedChange = { 
                                isComparative = it
                                if (it) {
                                    // Kıyaslamalı moda geçerken skorları temizle
                                    criteriaScores = criteriaNames.associateWith { Pair(null as Double?, null as Double?) }
                                }
                            }
                        )
                    }
                    
                    if (isComparative) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Puan Cetveli Seçenekleri
                        Text("Toplam Puan Seçimi:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Preset puan butonları
                        val presetPoints = listOf(1, 2, 3, 5, 6, 7, 10, 20, 50, 100)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(presetPoints) { points ->
                                FilterChip(
                                    onClick = {
                                        maxPoints = points
                                        customPoints = ""
                                        // Skorları temizle
                                        criteriaScores = criteriaNames.associateWith { Pair(null as Double?, null as Double?) }
                                    },
                                    label = { Text(points.toString()) },
                                    selected = maxPoints == points && customPoints.isEmpty(),
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Custom puan textbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Özel:", style = MaterialTheme.typography.bodyMedium)
                            
                            OutlinedTextField(
                                value = customPoints,
                                onValueChange = { 
                                    customPoints = it
                                    val newPoints = it.toIntOrNull()
                                    if (newPoints != null && newPoints > 0 && newPoints <= 1000) {
                                        maxPoints = newPoints
                                        // Skorları temizle
                                        criteriaScores = criteriaNames.associateWith { Pair(null as Double?, null as Double?) }
                                    }
                                },
                                label = { Text("Puan") },
                                placeholder = { Text("Örn: 15") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(100.dp),
                                singleLine = true
                            )
                        }
                    }
                }
            }
            
            // Criteria list - Scroll olmadan daha fazla görünsün
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), // Daha kompakt spacing
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(criteriaNames) { criterionName ->
                    if (isComparative) {
                        ComparativeCriterionScoringItem(
                            criterionName = criterionName,
                            team1Name = team1Name,
                            team2Name = team2Name,
                            maxPoints = maxPoints,
                            team1Score = criteriaScores[criterionName]?.first,
                            team2Score = criteriaScores[criterionName]?.second,
                            onScoresChange = { score1, score2 ->
                                criteriaScores = criteriaScores + (criterionName to Pair(score1, score2))
                            }
                        )
                    } else {
                        IndependentCriterionScoringItem(
                            criterionName = criterionName,
                            team1Name = team1Name,
                            team2Name = team2Name,
                            team1Score = criteriaScores[criterionName]?.first,
                            team2Score = criteriaScores[criterionName]?.second,
                            onTeam1ScoreChange = { score ->
                                val currentPair = criteriaScores[criterionName] ?: Pair(null, null)
                                criteriaScores = criteriaScores + (criterionName to currentPair.copy(first = score))
                            },
                            onTeam2ScoreChange = { score ->
                                val currentPair = criteriaScores[criterionName] ?: Pair(null, null)
                                criteriaScores = criteriaScores + (criterionName to currentPair.copy(second = score))
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bağımsız kriter puanlama bileşeni - Ayrı ayrı puanlama modu için
 */
@Composable
fun IndependentCriterionScoringItem(
    criterionName: String,
    team1Name: String,
    team2Name: String,
    team1Score: Double?,
    team2Score: Double?,
    onTeam1ScoreChange: (Double?) -> Unit,
    onTeam2ScoreChange: (Double?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = criterionName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team 1 Score
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = team1Name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    var team1Input by remember { mutableStateOf(team1Score?.toString() ?: "") }
                    
                    OutlinedTextField(
                        value = team1Input,
                        onValueChange = { input ->
                            team1Input = input
                            val score = input.toDoubleOrNull()
                            if (score == null && input.isNotEmpty()) {
                                // Invalid input, don't update
                            } else if (score != null && score in 1.0..100.0) {
                                onTeam1ScoreChange(score)
                            } else if (input.isEmpty()) {
                                onTeam1ScoreChange(null)
                            }
                        },
                        label = { Text("Puan") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        isError = team1Input.isNotEmpty() && (team1Input.toDoubleOrNull()?.let { it !in 1.0..100.0 } == true)
                    )
                }
                
                Text(
                    "VS",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Team 2 Score
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = team2Name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    var team2Input by remember { mutableStateOf(team2Score?.toString() ?: "") }
                    
                    OutlinedTextField(
                        value = team2Input,
                        onValueChange = { input ->
                            team2Input = input
                            val score = input.toDoubleOrNull()
                            if (score == null && input.isNotEmpty()) {
                                // Invalid input, don't update
                            } else if (score != null && score in 1.0..100.0) {
                                onTeam2ScoreChange(score)
                            } else if (input.isEmpty()) {
                                onTeam2ScoreChange(null)
                            }
                        },
                        label = { Text("Puan") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        isError = team2Input.isNotEmpty() && (team2Input.toDoubleOrNull()?.let { it !in 1.0..100.0 } == true)
                    )
                }
            }
            
            // Validation message kaldırıldı
        }
    }
}

/**
 * Kıyaslamalı kriter puanlama bileşeni - Puan bölüştürme modu için
 */
@Composable
fun ComparativeCriterionScoringItem(
    criterionName: String,
    team1Name: String,
    team2Name: String,
    maxPoints: Int,
    team1Score: Double?,
    team2Score: Double?,
    onScoresChange: (Double?, Double?) -> Unit
) {
    var sliderValue by remember { mutableStateOf(maxPoints * 0.53f) } // 53-47 varsayılan oranı
    
    // Slider değeri değiştiğinde skorları güncelle
    LaunchedEffect(sliderValue) {
        val score1 = sliderValue.toDouble()
        val score2 = (maxPoints - sliderValue).toDouble()
        onScoresChange(if (score1 > 0) score1 else null, if (score2 > 0) score2 else null)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Kriter adı
            Text(
                text = criterionName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Takım isimleri ve skorları
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team 1 info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = team1Name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${sliderValue.toInt()} puan",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Text(
                    "VS",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                
                // Team 2 info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = team2Name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${(maxPoints - sliderValue).toInt()} puan",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            
            // Kayan slider
            Column {
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 0f..maxPoints.toFloat(),
                    steps = maxPoints - 1,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                    )
                )
                
                // Slider açıklaması
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${team1Name} favors",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Toplam: $maxPoints puan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${team2Name} favors",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            
            // Hızlı preset butonları
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(
                    "Eşit" to (maxPoints / 2f),
                    "Hafif" to (maxPoints * 0.6f),
                    "Güçlü" to (maxPoints * 0.8f),
                    "Dominant" to (maxPoints * 0.9f)
                ).forEach { (label, value) ->
                    OutlinedButton(
                        onClick = { sliderValue = value },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (label != "Dominant") {
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
        }
    }
}