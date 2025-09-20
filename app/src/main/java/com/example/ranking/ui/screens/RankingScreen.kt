package com.example.ranking.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.data.Song
import com.example.ranking.data.Match
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
    var showCriteriaDialog by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Ana içerik - Dialog açıkken gizle
        if (!showCriteriaDialog) {
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
                // Köşeleri yuvarlatılmış dikdörtgen butonlar - sayfanın en tepesinde
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Session management buttons
                    if (uiState.hasActiveSession) {
                        Button(
                            onClick = { viewModel.pauseSession() },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text("Duraklat", fontSize = 10.sp)
                        }

                        Button(
                            onClick = { viewModel.deleteCurrentSession() },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text("Sıfırla", fontSize = 10.sp)
                        }
                    }

                    if (method in listOf("LEAGUE", "SWISS", "EMRE_CORRECT", "ELIMINATION", "FULL_ELIMINATION")) {
                        Button(
                            onClick = { onNavigateToFixture(listId, method) },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text("Fikstür", fontSize = 10.sp)
                        }
                    }
                    if (method == "LEAGUE" || method == "EMRE_CORRECT") {
                        var showStandings by remember { mutableStateOf(false) }
                        Button(
                            onClick = { showStandings = !showStandings },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Text(if (showStandings) "Maçlar" else "Puan", fontSize = 10.sp)
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
                onComplete = { onNavigateToResults(listId, method) },
                showCriteriaDialog = showCriteriaDialog,
                onShowCriteriaDialog = { showCriteriaDialog = it }
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
        
        // Kriter Değerlendirme Dialogu - Ana içeriğin üstünde
        if (showCriteriaDialog) {
            val currentMatch = when (method) {
                "LEAGUE", "SWISS", "EMRE_CORRECT" -> uiState.currentMatch
                else -> null
            }
            currentMatch?.let { match ->
                CriteriaEvaluationDialog(
                    match = match,
                    song1 = uiState.song1,
                    song2 = uiState.song2,
                    tournamentId = match.tournamentId ?: match.listId, // Fallback: listId'yi tournament olarak kullan
                    onDismiss = { showCriteriaDialog = false },
                    onSave = { criteriaScores, winner ->
                        // Kriter skorlarını kaydet ve maç sonucunu belirle
                        showCriteriaDialog = false
                        // Kazanan bilgisine göre arka sayfadaki butona basılmış gibi işle
                        when (winner) {
                            "team1" -> {
                                // İlk takım kazandı - arka sayfadaki sol butonu işle
                                android.util.Log.d("KriterDebug", "Takım 1 kazandı - ${uiState.song1?.name}")
                            }
                            "team2" -> {
                                // İkinci takım kazandı - arka sayfadaki sağ butonu işle
                                android.util.Log.d("KriterDebug", "Takım 2 kazandı - ${uiState.song2?.name}")
                            }
                            "draw" -> {
                                // Beraberlik - arka sayfadaki beraberlik butonu işle
                                android.util.Log.d("KriterDebug", "Beraberlik")
                            }
                            "save_only" -> {
                                // Sadece kaydet - kazanan belirlenmedi
                                android.util.Log.d("KriterDebug", "Sadece kaydet")
                            }
                        }
                    }
                )
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
                    .weight(1f) // Take available width for table display
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
    onComplete: () -> Unit,
    showCriteriaDialog: Boolean = false,
    onShowCriteriaDialog: (Boolean) -> Unit = {}
) {
    android.util.Log.d("MatchBasedContent", "🎯 Method: $method, showInitialRanking: ${uiState.showInitialRanking}, showMatchingsList: ${uiState.showMatchingsList}, isComplete: ${uiState.isComplete}, currentMatch: ${uiState.currentMatch?.id}")

    // İlk sıralama tablosunu göster (EMRE_CORRECT için)
    if (method == "EMRE_CORRECT" && uiState.showInitialRanking) {
        InitialRankingContent(
            uiState = uiState,
            method = method,
            viewModel = viewModel
        )
        return
    }

    // Eşleştirmeler listesini göster (EMRE_CORRECT için) - currentMatch yoksa
    if (method == "EMRE_CORRECT" && uiState.showMatchingsList && uiState.currentMatch == null) {
        android.util.Log.d("MatchBasedContent", "🎯 Showing MatchingsList for EMRE_CORRECT")
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
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. ÜST BÖLÜM: Sıkıştırılmış ilerleme ve tur bilgisi
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${uiState.completedMatches + 1} / ${uiState.totalMatches}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (method == "SWISS" || method == "EMRE_CORRECT") {
                        Text(
                            text = "Tur: ${match.round}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Duplicate menü butonları kaldırıldı - üstteki TopAppBar'daki butonlar kullanılıyor

            // 3. ANA İÇERİK: Genişletilmiş takım kartları
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (useScores) "Maç Skoru Girin" else "Hangisi daha iyi?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))
            
                // TAKIM KARTLARI: 3x büyütülmüş, scrollable container
                if (useScores) {
                    // Score input mode
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)  // Ana alanda tüm boş alanı kapla
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            uiState.song1?.let { song1 ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(420.dp) // 3x büyütülmüş yükseklik (140dp * 3)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // BÜYÜK TABLO ALANI: 3x genişlik
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        ) {
                                            TeamCardContent(
                                                song = song1,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(8.dp)
                                            )
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
                        }

                        item {
                            // Vertical VS text (V above S)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
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
                        }

                        item {
                            uiState.song2?.let { song2 ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(420.dp) // 3x büyütülmüş yükseklik (140dp * 3)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // BÜYÜK TABLO ALANI: 3x genişlik
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                        ) {
                                            TeamCardContent(
                                                song = song2,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(8.dp)
                                            )
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
                        }

                        item {
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
                    }
                } else {
                    // Traditional winner selection mode - scrollable
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)  // Ana alanda tüm boş alanı kapla
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            uiState.song1?.let { song1 ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(420.dp) // 3x büyütülmüş yükseklik (140dp * 3)
                                ) {
                                    TeamCardContent(
                                        song = song1,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp),
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
                                                    MaterialTheme.colorScheme.primary,
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
                        }

                        item {
                            // Vertical VS text (V above S)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
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
                        }

                        item {
                            uiState.song2?.let { song2 ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(420.dp) // 3x büyütülmüş yükseklik (140dp * 3)
                                ) {
                                    TeamCardContent(
                                        song = song2,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp),
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
                                                    MaterialTheme.colorScheme.primary,
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
                        }
                    }
                }
            }

            // 4. ALT BÖLÜM: Berabere ve kriter butonları - aşağıya taşındı
            if (method == "LEAGUE" || method == "SWISS" || method == "EMRE_CORRECT") {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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

                    // Kriter Değerlendirme Butonu
                    Button(
                        onClick = { onShowCriteriaDialog(true) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("Kriterler ile Değerlendir")
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
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            android.util.Log.d("MatchingsListContent", "🎯 Maç tıklandı: ${match.id}")
                            // Bu maçı currentMatch olarak ayarla ve oylama ekranına geç
                            viewModel.selectMatch(match)
                        },
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
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
                        
                        // İlk takım - direkt içerik (gri kutu yok)
                        Box(
                            modifier = Modifier.weight(1f)
                        ) {
                            song1?.let { song ->
                                TeamCardContent(
                                    song = song,
                                    modifier = Modifier.fillMaxWidth() // Ekran kenarlarına kadar uzansın
                                )
                            }
                            
                            // Puan rozeti sol alt köşe (sadece EMRE_CORRECT için)
                            val team1Points = if (uiState.emreState?.teams?.isNotEmpty() == true) {
                                uiState.emreState.teams.find { it.song.id == song1?.id }?.points ?: 0.0
                            } else {
                                0.0
                            }
                            
                            if (team1Points > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
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
                        
                        // Vertical VS text (V above S) - daraltılmış
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 2.dp) // Daha dar VS alanı
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
                        
                        // İkinci takım - direkt içerik (gri kutu yok)
                        Box(
                            modifier = Modifier.weight(1f)
                        ) {
                            song2?.let { song ->
                                TeamCardContent(
                                    song = song,
                                    modifier = Modifier.fillMaxWidth() // Ekran kenarlarına kadar uzansın
                                )
                            }
                            
                            // Puan rozeti sağ alt köşe (sadece EMRE_CORRECT için)
                            val team2Points = if (uiState.emreState?.teams?.isNotEmpty() == true) {
                                uiState.emreState.teams.find { it.song.id == song2?.id }?.points ?: 0.0
                            } else {
                                0.0
                            }
                            
                            if (team2Points > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
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
        android.util.Log.d("TeamCardContent", "🔍 Song: ${song.name}, csvData: '${song.csvData}'")
        song.csvData?.let { csvData ->
            android.util.Log.d("TeamCardContent", "🔍 CSV data is not null: '$csvData'")
            if (csvData.isNotBlank()) {
                android.util.Log.d("TeamCardContent", "✅ Showing CSV table for song: ${song.name}")
                Spacer(modifier = Modifier.height(8.dp))
                CsvDataTable(
                    csvData = csvData, 
                    teamPoints = extractPointsFromCsv(csvData),
                    onClick = onClick
                )
            } else {
                android.util.Log.d("TeamCardContent", "❌ CSV data is blank for song: ${song.name}")
            }
        } ?: run {
            android.util.Log.d("TeamCardContent", "❌ CSV data is null for song: ${song.name}")
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
        // Image #1 mavi table format with header and scrollable rows - CLICKABLE + border
        Box {
            Card(
                modifier = onClick?.let {
                    Modifier.clickable { it() }
                } ?: Modifier,
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline) // Material Theme çerçeve
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp) // No padding for full width
                ) {
                    // Header with team name (first value from CSV)
                    val firstEntry = parsedData.entries.firstOrNull()
                    val teamName = firstEntry?.value ?: "Team"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1976D2)) // Koyu mavi başlık (Blue 700)
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

                    // Scrollable data rows excluding header and metadata
                    val dataRows = parsedData.entries.drop(1) // Skip first entry (used as header)
                        .filterNot { (key, _) -> key.startsWith("_") } // Skip metadata like _displayMode

                    // Always show all rows without height limit
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
                            color = MaterialTheme.colorScheme.secondary, // Material Theme secondary
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
    // Image #1 format - açık mavi tonları ve koyu text
    val backgroundColor = when {
        index % 2 == 0 -> Color(0xFFE3F2FD) // Çok açık mavi - çift satırlar (Blue 50)
        else -> Color(0xFFBBDEFB) // Açık mavi - tek satırlar (Blue 100)
    }
    val textColor = when {
        index % 2 == 0 -> Color(0xFF0D47A1) // Koyu mavi text - çift satırlar
        else -> Color(0xFF1565C0) // Orta mavi text - tek satırlar
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.weight(1f)
        )

        // Multi-line value support - like ListViewScreen implementation
        val valueLines = value.split(Regex("\\n|;|,")).filter { it.trim().isNotBlank() }
        if (valueLines.size > 1) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                valueLines.take(3).forEach { line -> // Show max 3 lines
                    Text(
                        text = line.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        textAlign = TextAlign.End,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                if (valueLines.size > 3) {
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        textAlign = TextAlign.End
                    )
                }
            }
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
        }
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
                .fillMaxWidth().padding(4.dp), // Mobile friendly width
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer // Material Theme primary container
            )
        ) {
            Column {
                // Green header with team name
                val teamName = parsedData.values.firstOrNull() ?: "Team"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary) // Material Theme primary header
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
                        index % 2 == 0 -> MaterialTheme.colorScheme.primaryContainer // Material Theme primary container for even rows
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) // Material Theme primary with transparency for odd rows
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

@Composable
private fun CriteriaEvaluationDialog(
    match: Match,
    song1: Song?,
    song2: Song?,
    tournamentId: Long?,
    onDismiss: () -> Unit,
    onSave: (Map<String, Pair<Double?, Double?>>, String) -> Unit, // Kazanan bilgisi eklendi
    viewModel: RankingViewModel = viewModel()
) {
    // TAM EKRAN OVERLAY - Temizlenmiş tasarım
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. EN TEPEDE KRİTER DEĞERLENDİRMESİ YAZISI
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Kriter Değerlendirmesi",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 2. TAKIM BAŞLIKLARI - Ekran kenarından kenara
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(2.dp, Color.Black),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(55.dp)
                            .padding(2.dp)
                    ) {
                        // Sol yarı - Açık mavi (Takım 1)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(end = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFBBDEFB)),
                            border = BorderStroke(2.dp, Color.Black),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = song1?.name ?: "TAKIM 1",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1976D2)
                                )
                            }
                        }

                        // Sağ yarı - Açık yeşil (Takım 2)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(start = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCEDC8)),
                            border = BorderStroke(2.dp, Color.Black),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = song2?.name ?: "TAKIM 2",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF388E3C)
                                )
                            }
                        }
                    }
                }

                // 3. KRİTERLER VE PUANLAMA
                val criteriaScores = remember { mutableStateMapOf<String, Pair<Double?, Double?>>() }
                var criteria by remember { mutableStateOf<List<String>>(emptyList()) }
                var criteriaSettings by remember { mutableStateOf<Map<String, Any>?>(null) }

                LaunchedEffect(tournamentId) {
                    android.util.Log.d("CriteriaDialog", "🎯 LaunchedEffect tournamentId: $tournamentId")
                    if (tournamentId != null) {
                        android.util.Log.d("CriteriaDialog", "🎯 Calling getCriteriaForTournament...")
                        criteria = viewModel.getCriteriaForTournament(tournamentId)
                        android.util.Log.d("CriteriaDialog", "🎯 Criteria received: $criteria (size: ${criteria.size})")
                        criteriaSettings = viewModel.getCriteriaSettingsForTournament(tournamentId)
                        android.util.Log.d("CriteriaDialog", "🎯 CriteriaSettings received: $criteriaSettings")
                    }
                }

                val finalCriteria = criteria // Sadece gerçek kriter listesi

                // Scrollable kriterler listesi
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(0.dp) // Kenarları ekranla birleştir
                ) {
                    items(finalCriteria) { criterion ->
                        NewCriterionEvaluationBox(
                            criterionName = criterion,
                            team1Name = song1?.name ?: "Takım 1",
                            team2Name = song2?.name ?: "Takım 2",
                            currentScores = criteriaScores[criterion] ?: Pair(null, null),
                            criteriaSettings = criteriaSettings,
                            onScoresChanged = { team1Score, team2Score ->
                                criteriaScores[criterion] = Pair(team1Score, team2Score)
                            }
                        )
                    }
                }

                // 4. TOPLAM PUANLAR VE GALİBİYET BUTONLARI
                FinalScoreAndResultSection(
                    criteriaScores = criteriaScores,
                    team1Name = song1?.name ?: "Takım 1",
                    team2Name = song2?.name ?: "Takım 2",
                    onDismiss = onDismiss,
                    onSave = onSave
                )
            }
        }
    }
}

@Composable
private fun NewCriterionEvaluationBox(
    criterionName: String,
    team1Name: String,
    team2Name: String,
    currentScores: Pair<Double?, Double?>,
    criteriaSettings: Map<String, Any>?,
    onScoresChanged: (Double?, Double?) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val scoringType = criteriaSettings?.get("scoringType") as? String ?: "separate"
    val scoreScale = (criteriaSettings?.get("scoreScale") as? Double)?.toInt() ?: 10

    // DEBUG: Scoring type kontrolü
    android.util.Log.d("KriterDebug", "scoringType: $scoringType, scoreScale: $scoreScale")

    // Checkbox açıldığında varsayılan puanları ayarla
    LaunchedEffect(isExpanded) {
        if (isExpanded && currentScores.first == null && currentScores.second == null) {
            // Varsayılan olarak eşit puan dağıtımı (5-5 gibi)
            val defaultScore = scoreScale / 2.0
            onScoresChanged(defaultScore, defaultScore)
        }
    }

    // XML MOCKUP TASARIMI - Ekran kenarından kenara siyah çerçeveli kutu (Card kaldırıldı)
    // Kriter metni uzunluğuna göre dinamik yükseklik
    val textHeight = remember(criterionName) {
        // Uzun metinler için yükseklik artırımı
        when {
            criterionName.length > 50 -> if (isExpanded) 110.dp else 60.dp
            criterionName.length > 30 -> if (isExpanded) 100.dp else 50.dp
            else -> if (isExpanded) 90.dp else 40.dp
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 0.dp) // TÜM boşlukları elimine et
            .border(2.dp, Color.Black) // Siyah çerçeve
            .clickable { isExpanded = !isExpanded }
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(textHeight)
            ) {
            // SOL YARI - AÇIK MAVİ BÖLGE (Takım 1)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFFBBDEFB)) // Biraz koyu mavi
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (isExpanded) {
                    // Açık durum - Sol kısımda puanlama UI (kriter metni üstte overlay'da)
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Puanlama UI - SADECE AYRI AYRI MODDA dropdown
                        if (scoringType == "separate") {
                            android.util.Log.d("KriterDebug", "Sol dropdown gösteriliyor - scoringType: $scoringType")
                            ScoreDropdown(
                                score = currentScores.first,
                                maxScore = scoreScale,
                                onScoreSelected = { score ->
                                    onScoresChanged(score, currentScores.second)
                                }
                            )
                        } else {
                            android.util.Log.d("KriterDebug", "Sol dropdown GÖSTERİLMİYOR - scoringType: $scoringType")
                        }
                    }
                }
            }

            // SAĞ YARI - AÇIK YEŞİL BÖLGE (Takım 2)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFFDCEDC8)) // Biraz koyu yeşil
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (isExpanded) {
                    // Açık durum - Sağ kısımda puanlama UI (kriter metni üstte overlay'da)
                    if (scoringType == "separate") {
                        // Ayrı ayrı - Dropdown (alt kısımda)
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            ScoreDropdown(
                                score = currentScores.second,
                                maxScore = scoreScale,
                                onScoreSelected = { score ->
                                    onScoresChanged(currentScores.first, score)
                                }
                            )
                        }
                    }
                } else {
                    // Kapalı durum - Checkbox yeşil bölgenin en sağında
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Checkbox(
                            checked = isExpanded,
                            onCheckedChange = { isExpanded = !isExpanded },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            }

            // KRİTER METNİ OVERLAY - Tüm genişliği kaplar, her durumda görünür
            if (isExpanded) {
                // Açık durum - Kriter metni üstte, arka plan transparan
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(textHeight)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = criterionName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2),
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(Alignment.Top),
                        maxLines = 4, // Uzun metinler için 4 satıra kadar
                        overflow = TextOverflow.Visible
                    )
                }
            } else {
                // Kapalı durum - Kriter metni tüm genişlikte, checkbox hariç
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(textHeight)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = criterionName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2),
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 30.dp, top = 4.dp, bottom = 4.dp) // Checkbox için sağdan yer bırak
                            .wrapContentHeight(Alignment.Top),
                        maxLines = when {
                            criterionName.length > 50 -> 3
                            criterionName.length > 30 -> 2
                            else -> 2
                        },
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // SLIDER OVERLAY - Kıyaslamalı modda tüm genişliği kaplar
            if (isExpanded && scoringType == "comparative") {
                // Alt kısım arka planı - Sol yarı mavi, sağ yarı yeşil
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(textHeight)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFFBBDEFB)) // Mavi arka plan
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFFDCEDC8)) // Yeşil arka plan
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(textHeight)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Slider - ortadan küçültülmüş, iki ucunda puanlar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Sol puan - slider'ın sol ucunda (2x büyük font)
                            Text(
                                text = "${currentScores.first?.toInt() ?: (scoreScale / 2)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 32.sp), // 16sp -> 32sp (2 katı)
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1976D2),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(end = 8.dp)
                            )

                            // Slider - ortada, küçültülmüş
                            Slider(
                                value = currentScores.first?.toFloat() ?: (scoreScale / 2f),
                                onValueChange = { newValue ->
                                    val team1Score = newValue.toDouble()
                                    val team2Score = scoreScale - team1Score
                                    onScoresChanged(team1Score, team2Score)
                                },
                                valueRange = 0f..scoreScale.toFloat(),
                                steps = scoreScale - 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 16.dp) // Soldan ve sağdan küçültme
                            )

                            // Sağ puan - slider'ın sağ ucunda (2x büyük font)
                            Text(
                                text = "${currentScores.second?.toInt() ?: (scoreScale / 2)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 32.sp), // 16sp -> 32sp (2 katı)
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF388E3C),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun FinalScoreAndResultSection(
    criteriaScores: Map<String, Pair<Double?, Double?>>,
    team1Name: String,
    team2Name: String,
    onDismiss: () -> Unit,
    onSave: (Map<String, Pair<Double?, Double?>>, String) -> Unit // Kazanan bilgisi eklendi
) {
    // Toplam puanları hesapla
    val team1Total = criteriaScores.values.mapNotNull { it.first }.sum()
    val team2Total = criteriaScores.values.mapNotNull { it.second }.sum()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 0.dp) // Hiç padding yok - beyaz şeridi kaldır
    ) {
        // Toplam puanlar - XML tasarımına uygun siyah çerçeveli, boşluksuz
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp) // Puan kutuları arasında boşluk yok
        ) {
            // Takım 1 toplam puanı - Siyah çerçeveli mavi kutu
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(2.dp, Color.Black),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFBBDEFB))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = team1Name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Toplam: ${team1Total.toInt()}",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 32.sp), // 2 katı puntoda
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Takım 2 toplam puanı - Siyah çerçeveli yeşil kutu
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(2.dp, Color.Black),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFDCEDC8))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = team2Name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF388E3C),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Toplam: ${team2Total.toInt()}",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 32.sp), // 2 katı puntoda
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF388E3C),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Sonuç butonları - Dikdörtgen, boşluksuz, açık mavi/yeşil
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp) // Butonlar arası boşluk yok
        ) {
            Button(
                onClick = { onSave(criteriaScores, "team1") }, // Takım 1 kazandı
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp), // Büyütüldü - metinler için daha fazla yer
                shape = RoundedCornerShape(0.dp), // Dikdörtgen
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFBBDEFB) // Biraz koyu mavi
                )
            ) {
                Text("$team1Name Kazandı", color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { onSave(criteriaScores, "draw") }, // Beraberlik
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp), // Büyütüldü - metinler için daha fazla yer
                shape = RoundedCornerShape(0.dp), // Dikdörtgen
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Gray
                )
            ) {
                Text("Beraberlik", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { onSave(criteriaScores, "team2") }, // Takım 2 kazandı
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp), // Büyütüldü - metinler için daha fazla yer
                shape = RoundedCornerShape(0.dp), // Dikdörtgen
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFDCEDC8) // Biraz koyu yeşil
                )
            ) {
                Text("$team2Name Kazandı", color = Color(0xFF388E3C), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp)) // Minimal beyaz boşluk

        // İptal ve Kaydet butonları - Bant şeklinde, dikdörtgen
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp) // Butonlar bitişik
        ) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(0.dp), // Dikdörtgen şekil
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red
                )
            ) {
                Text("İptal", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { onSave(criteriaScores, "save_only") }, // Sadece kaydet, kazanan yok
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(0.dp), // Dikdörtgen şekil
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32)
                )
            ) {
                Text("Kaydet", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CriterionEvaluationRow(
    criterionName: String,
    team1Name: String,
    team2Name: String,
    currentScores: Pair<Double?, Double?>,
    criteriaSettings: Map<String, Any>?,
    onScoresChanged: (Double?, Double?) -> Unit
) {
    var isActive by remember { mutableStateOf(true) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
                else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Kriter başlığı ve aktif/pasif toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = criterionName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) MaterialTheme.colorScheme.primary 
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                
                Switch(
                    checked = isActive,
                    onCheckedChange = { 
                        isActive = it
                        if (!it) {
                            onScoresChanged(null, null)
                        }
                    }
                )
            }
            
            if (isActive) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Puanlama ayarları - Settings'ten al
                val scoringType = criteriaSettings?.get("scoringType") as? String ?: "separate"
                val scoreScale = (criteriaSettings?.get("scoreScale") as? Double)?.toInt() ?: 10
                
                // Takım sütunları - RENK FARKI
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Takım 1 sütunu - MAVİ RENK
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                Color(0xFFE3F2FD), // Açık mavi
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = team1Name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1976D2) // Koyu mavi
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Puanlama UI - Settings'e göre tip belirlenir
                        if (scoringType == "comparative") {
                            // Kıyaslamalı puanlama - Slider
                            ComparativeScoring(
                                score = currentScores.first,
                                maxScore = scoreScale,
                                onScoreSelected = { score ->
                                    onScoresChanged(score, currentScores.second)
                                }
                            )
                        } else {
                            // Ayrı ayrı puanlama - Dropdown
                            ScoreDropdown(
                                score = currentScores.first,
                                maxScore = scoreScale,
                                onScoreSelected = { score ->
                                    onScoresChanged(score, currentScores.second)
                                }
                            )
                        }
                    }
                    
                    // Takım 2 sütunu - YEŞİL RENK
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                Color(0xFFE8F5E8), // Açık yeşil
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = team2Name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF388E3C) // Koyu yeşil
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Puanlama UI - Settings'e göre tip belirlenir
                        if (scoringType == "comparative") {
                            // Kıyaslamalı puanlama - Slider
                            ComparativeScoring(
                                score = currentScores.second,
                                maxScore = scoreScale,
                                onScoreSelected = { score ->
                                    onScoresChanged(currentScores.first, score)
                                }
                            )
                        } else {
                            // Ayrı ayrı puanlama - Dropdown
                            ScoreDropdown(
                                score = currentScores.second,
                                maxScore = scoreScale,
                                onScoreSelected = { score ->
                                    onScoresChanged(currentScores.first, score)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScoreDropdown(
    score: Double?,
    maxScore: Int = 10,
    onScoreSelected: (Double?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val scores = (1..maxScore).map { it.toDouble() }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = score?.toInt()?.toString() ?: "Puan seç",
            onValueChange = { },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Puan yok", fontSize = 11.sp) },
                onClick = {
                    onScoreSelected(null)
                    expanded = false
                }
            )
            scores.forEach { scoreValue ->
                DropdownMenuItem(
                    text = { Text(scoreValue.toInt().toString(), fontSize = 11.sp) },
                    onClick = {
                        onScoreSelected(scoreValue)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ComparativeScoring(
    score: Double?,
    maxScore: Int = 10,
    onScoreSelected: (Double?) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (score != null) "${score.toInt()}/$maxScore" else "Puan seç",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Slider(
            value = score?.toFloat() ?: 0f,
            onValueChange = { newValue ->
                onScoreSelected(if (newValue > 0) newValue.toDouble() else null)
            },
            valueRange = 0f..maxScore.toFloat(),
            steps = maxScore - 1,
            modifier = Modifier.fillMaxWidth()
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(maxScore.toString(), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
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