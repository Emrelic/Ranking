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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.PaddingValues
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

// YENİ 6 KATMANLI TASARIM TAMAMLANDI - Eksik fonksiyonlar ekleniyor

private fun getMethodTitle(method: String): String {
    return when (method) {
        "DIRECT_SCORING" -> "Direkt Puanlama"
        "LEAGUE" -> "Lig Sistemi"
        "SWISS" -> "İsviçre Sistemi"
        "EMRE_CORRECT" -> "Geliştirilmiş İsviçre Sistemi"
        "ELIMINATION" -> "Eleme Sistemi"
        "FULL_ELIMINATION" -> "Tam Eleme"
        else -> "Ranking Sistemi"
    }
}

@Composable
internal fun TeamCardContent(
    song: Song,
    modifier: Modifier = Modifier
) {
    val csvData = song.csvData

    // Parse JSON outside composable scope
    val jsonData = remember(csvData) {
        if (csvData != null && csvData.isNotEmpty()) {
            try {
                val data = org.json.JSONObject(csvData)
                val keys = data.keys().asSequence().toList().filter { it != "name" }
                keys.map { key -> key to data.optString(key, "") }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    if (jsonData != null) {
        // CSV data display
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(jsonData) { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0D47A1),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1976D2),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    } else {
        // Simple song info
        Column {
            Text(
                text = song.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            song.artist?.let { artist ->
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Placeholder functions - These should exist in the original file
@Composable
private fun StandingsDialog(
    uiState: RankingViewModel.RankingUiState,
    method: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Puan Durumu") },
        text = { Text("Puan durumu burada gösterilecek") },
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
    viewModel: RankingViewModel
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("İlk sıralama tablosu")
        Button(onClick = {
            android.util.Log.d("InitialRankingContent", "🔥 Turnuvayı Başlat butonuna basıldı!")
            viewModel.startScoring()
        }) {
            Text("Turnuvayı Başlat")
        }
    }
}

@Composable
private fun MatchingsListContent(
    uiState: RankingViewModel.RankingUiState,
    viewModel: RankingViewModel
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Eşleştirmeler listesi")
    }
}

@Composable
private fun EliminationContent(
    uiState: RankingViewModel.RankingUiState,
    onMatchResult: (Long, Long?) -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Eleme sistemi")
    }
}

@Composable
private fun CriteriaEvaluationDialog(
    match: Match,
    song1: Song?,
    song2: Song?,
    tournamentId: Long,
    onDismiss: () -> Unit,
    onSave: (Map<String, Pair<Double?, Double?>>, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kriter Değerlendirmesi") },
        text = { Text("Kriter değerlendirmesi burada yapılacak") },
        confirmButton = {
            TextButton(onClick = { onSave(emptyMap(), "save_only") }) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
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
    android.util.Log.d("MatchBasedContent", "🔍 EMRE_CORRECT check: method=$method, showInitialRanking=${uiState.showInitialRanking}")
    if (method == "EMRE_CORRECT" && uiState.showInitialRanking) {
        android.util.Log.d("MatchBasedContent", "✅ InitialRankingContent gösteriliyor!")
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
        var showVSPopup by remember { mutableStateOf(false) }

        // 6 KATMANLI SABİT LAYOUT SİSTEMİ
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. TUR İLERLEME ÇUBUĞU (En üst, minimal padding)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp)
            ) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(2.dp))

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


            // 2. TAKIM 1 BAŞLIĞI (Sabit)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1976D2))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.song1?.name?.uppercase() ?: "TAKIM 1",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // 3. TAKIM 1 SCROLL PENCERESİ
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFFE3F2FD))
                    .padding(8.dp)
            ) {
                uiState.song1?.let { song1 ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            TeamCardContent(
                                song = song1,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // 4. ORTA BUTON ÇUBUĞU (Sabit - Tam ekran ortasında)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Sol: BERABERLIK butonu (büyük)
                Button(
                    onClick = {
                        onMatchResult(match.id, null)
                    },
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    shape = RectangleShape
                ) {
                    Text("BERABERLİK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Orta: VS butonu (popup trigger)
                Button(
                    onClick = { showVSPopup = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFC107)
                    ),
                    shape = RectangleShape
                ) {
                    Text("VS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }

                // Sağ: İki buton (SKOR GİR + KRİTER)
                Column(
                    modifier = Modifier.weight(2f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Button(
                        onClick = {
                            // Skor gir dialog açılacak
                        },
                        modifier = Modifier.fillMaxWidth().height(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        ),
                        shape = RectangleShape,
                        contentPadding = PaddingValues(2.dp)
                    ) {
                        Text("SKOR GİR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            onShowCriteriaDialog(true)
                        },
                        modifier = Modifier.fillMaxWidth().height(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        ),
                        shape = RectangleShape,
                        contentPadding = PaddingValues(2.dp)
                    ) {
                        Text("KRİTER", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 5. TAKIM 2 BAŞLIĞI (Sabit)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1976D2))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.song2?.name?.uppercase() ?: "TAKIM 2",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // 6. TAKIM 2 SCROLL PENCERESİ (Ekran dibine kadar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFFE3F2FD))
                    .padding(8.dp)
            ) {
                uiState.song2?.let { song2 ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            TeamCardContent(
                                song = song2,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // VS POPUP DIALOG
            if (showVSPopup) {
                AlertDialog(
                    onDismissRequest = { showVSPopup = false },
                    title = { Text("Menü") },
                    text = {
                        Column {
                            TextButton(onClick = { showVSPopup = false }) {
                                Text("Durakla")
                            }
                            TextButton(onClick = { showVSPopup = false }) {
                                Text("Sıfırla")
                            }
                            TextButton(onClick = { showVSPopup = false }) {
                                Text("Fikstür")
                            }
                            TextButton(onClick = { showVSPopup = false }) {
                                Text("Puan")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showVSPopup = false }) {
                            Text("Kapat")
                        }
                    }
                )
            }
        }
    }
}
