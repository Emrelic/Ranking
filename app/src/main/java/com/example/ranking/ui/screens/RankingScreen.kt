package com.example.ranking.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
    var showStandingsDialog by remember { mutableStateOf(false) }
    
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
                onShowCriteriaDialog = { showCriteriaDialog = it },
                onShowStandingsDialog = { showStandingsDialog = it }
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
                            }
                            "team2" -> {
                                // İkinci takım kazandı - arka sayfadaki sağ butonu işle
                            }
                            "draw" -> {
                                // Beraberlik - arka sayfadaki beraberlik butonu işle
                            }
                            "save_only" -> {
                                // Sadece kaydet - kazanan belirlenmedi
                            }
                        }
                    }
                )
            }
        }
        
        // Standings Dialog with TMB buttons
        if (showStandingsDialog) {
            StandingsDialog(
                uiState = uiState,
                method = method,
                onDismiss = { showStandingsDialog = false },
                onMatchResult = viewModel::submitMatchResult,
                onShowCriteriaDialog = { showCriteriaDialog = it }
            )
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
        // CSV data display - restore original LazyColumn with proper constraint handling
        LazyColumn(
            modifier = modifier.heightIn(max = 300.dp), // Add max height to prevent infinite constraint
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
    onDismiss: () -> Unit,
    onMatchResult: (Long, Long?) -> Unit = { _, _ -> },
    onShowCriteriaDialog: (Boolean) -> Unit = { }
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Puan Durumu") },
        text = { 
            Column {
                Text("Puan durumu burada gösterilecek") 
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // TMB BUTONLARI - Bitişik Layout (3 Mavi + 3 Kırmızı)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start // Boşluk yok
                ) {
                    // 3 MAVİ BUTON
                    Button(
                        onClick = { onMatchResult(0, null) }, // Beraberlik
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                        shape = RectangleShape,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("BERABERLİK", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = { /* VS menü */ },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                        shape = RectangleShape,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("VS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = { /* Skor gir */ },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                        shape = RectangleShape,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("SKOR GİR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    // 3 KIRMIZI BUTON
                    Button(
                        onClick = { onShowCriteriaDialog(true) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        shape = RectangleShape,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("KRİTER", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = { /* TAM EKRAN */ },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        shape = RectangleShape,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("TAM EKRAN", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = { /* MENÜ */ },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        shape = RectangleShape,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("MENÜ", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
    viewModel: RankingViewModel
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🎯 EMRE_CORRECT İlk Sıralama",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Blue,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "Debug: showInitialRanking = ${uiState.showInitialRanking}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Red,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Debug: emreState = ${uiState.emreState != null}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Red,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Button(
            onClick = {
                viewModel.startScoring()
            },
            modifier = Modifier.padding(16.dp)
        ) {
            Text("🚀 Turnuvayı Başlat", fontSize = 18.sp)
        }
    }
}

@Composable
private fun MatchingsListContent(
    uiState: RankingViewModel.RankingUiState,
    viewModel: RankingViewModel
) {
    var isAdvancedView by remember { mutableStateOf(false) } // Basit görünüm varsayılan

    // Debug logging
    android.util.Log.d("RANKING_DEBUG", "MatchingsListContent render - matchingsList.size: ${uiState.matchingsList.size}")
    android.util.Log.d("RANKING_DEBUG", "MatchingsList içeriği: ${uiState.matchingsList.map { "${it.id}:${it.matchNumber}" }}")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Eşleştirmeler - ${uiState.currentRound}. Tur",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Toggle Butonları - Basit/Gelişmiş
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { isAdvancedView = false },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isAdvancedView) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Basit",
                    color = if (!isAdvancedView) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { isAdvancedView = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAdvancedView) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Gelişmiş",
                    color = if (isAdvancedView) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Sayaç bilgisi
        Text(
            text = "${uiState.matchingsList.size} Eşleştirme",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Eşleştirmeler listesi
        if (uiState.matchingsList.isEmpty()) {
            Text(
                text = "Henüz eşleştirme yok - startScoring() çağırılmadı mı?",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Red,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp), // Fixed height to prevent constraint issues
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.matchingsList) { match ->
                    android.util.Log.d("RANKING_DEBUG", "Rendering match card: ${match.id}:${match.matchNumber}")
                    if (isAdvancedView) {
                        // Gelişmiş görünüm - Büyük tablo kartları
                        AdvancedMatchCard(
                            match = match,
                            songs = uiState.allSongs,
                            onClick = { 
                                try {
                                    android.util.Log.d("RANKING_DEBUG", "AdvancedMatchCard onClick - Match: ${match.id}")
                                    viewModel.selectMatch(match)
                                } catch (e: Exception) {
                                    android.util.Log.e("RANKING_DEBUG", "AdvancedMatchCard onClick CRASH: ${e.message}", e)
                                }
                            }
                        )
                    } else {
                        // Basit görünüm - Küçük kartlar
                        SimpleMatchCard(
                            match = match,
                            songs = uiState.allSongs,
                            onClick = { 
                                try {
                                    android.util.Log.d("RANKING_DEBUG", "SimpleMatchCard onClick - Match: ${match.id}")
                                    viewModel.selectMatch(match)
                                } catch (e: Exception) {
                                    android.util.Log.e("RANKING_DEBUG", "SimpleMatchCard onClick CRASH: ${e.message}", e)
                                }
                            }
                        )
                    }
                }
            }
            
            // Puanlama Ekranına Geç butonu - LazyColumn dışında
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    try {
                        android.util.Log.d("RANKING_DEBUG", "Puanlama Ekranına Geç butonu tıklandı")
                        // İlk maçı seç ve puanlama ekranına geç
                        val firstMatch = uiState.matchingsList.firstOrNull()
                        if (firstMatch != null) {
                            android.util.Log.d("RANKING_DEBUG", "İlk maç seçiliyor: ${firstMatch.id}")
                            viewModel.selectMatch(firstMatch)
                        } else {
                            android.util.Log.e("RANKING_DEBUG", "Hiçbir maç bulunamadı!")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("RANKING_DEBUG", "Puanlama butonu CRASH: ${e.message}", e)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "▶ Puanlama Ekranına Geç",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SimpleMatchCard(
    match: Match,
    songs: List<Song>,
    onClick: () -> Unit
) {
    val song1 = songs.find { it.id == match.songId1 }
    val song2 = songs.find { it.id == match.songId2 }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Maç numarası
            Text(
                text = "${match.matchNumber}. Eşleşme",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Basit VS Layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Takım 1
                Text(
                    text = song1?.name ?: "Takım 1",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1976D2),
                    modifier = Modifier.weight(1f)
                )

                // VS
                Text(
                    text = "VS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Takım 2
                Text(
                    text = song2?.name ?: "Takım 2",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF388E3C),
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AdvancedMatchCard(
    match: Match,
    songs: List<Song>,
    onClick: () -> Unit
) {
    val song1 = songs.find { it.id == match.songId1 }
    val song2 = songs.find { it.id == match.songId2 }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp) // Minimal padding için 16dp → 4dp
        ) {
            // Maç numarası başlığı
            Text(
                text = "${match.matchNumber}. Eşleşme",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Yan yana format - EKRAN_GORUNTULERI.md Image #1 formatı
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Sol takım kartı
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Header - Mavi başlık
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2196F3))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = song1?.name?.uppercase() ?: "AFGANİSTAN",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Restored original team card content
                    if (song1 != null) {
                        TeamCardContent(
                            song = song1,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Safe fallback
                        Text(
                            text = "Takım 1",
                            modifier = Modifier.padding(8.dp),
                            color = Color(0xFF1976D2)
                        )
                    }
                }

                // VS ortada - Minimal
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(20.dp))
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

                // Sağ takım kartı
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Header - Mavi başlık
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2196F3))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = song2?.name?.uppercase() ?: "ARNAVUTLUK",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Restored original team card content
                    if (song2 != null) {
                        TeamCardContent(
                            song = song2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Safe fallback
                        Text(
                            text = "Takım 2",
                            modifier = Modifier.padding(8.dp),
                            color = Color(0xFF1976D2)
                        )
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
    onSave: (Map<String, Pair<Double?, Double?>>, String) -> Unit,
    viewModel: RankingViewModel = viewModel()
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize(), // TAM EKRAN
            shape = RoundedCornerShape(0.dp), // KÖŞE YUVARLAĞI YOK
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Başlık
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kriter Değerlendirmesi",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Kapat")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Takım isimleri
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = song1?.name ?: "Takım 1",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = song2?.name ?: "Takım 2",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Gerçek kriterler - Tournament'taki criterionListId'den alınıyor
                val criteriaScores = remember { mutableStateMapOf<String, Pair<Double?, Double?>>() }
                var criteria by remember { mutableStateOf<List<String>>(emptyList()) }

                // Turnuva ID'sinden criteria'ları ve settings'leri al
                var criteriaSettings by remember { mutableStateOf<Map<String, Any>?>(null) }
                LaunchedEffect(tournamentId) {
                    criteria = viewModel.getCriteriaForTournament(tournamentId)
                    criteriaSettings = viewModel.getCriteriaSettingsForTournament(tournamentId)
                }

                // Eğer criteria bulunamazsa demo data kullan
                val finalCriteria = if (criteria.isNotEmpty()) criteria else listOf(
                    "Teknik Yetenek",
                    "Yaratıcılık",
                    "Performans",
                    "Orijinallik",
                    "Sahne Hâkimiyeti"
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(finalCriteria) { criterion ->
                        CriterionEvaluationRow(
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

                Spacer(modifier = Modifier.height(16.dp))

                // Alt butonlar - ESKİ DİKDÖRTGEN TASARIM
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp), // Daha küçük yükseklik
                        shape = RoundedCornerShape(4.dp), // Dikdörtgen
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Text(
                            "İptal",
                            fontSize = 12.sp // Küçük punto
                        )
                    }

                    Button(
                        onClick = { onSave(criteriaScores.toMap(), "save_only") },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp), // Daha küçük yükseklik
                        shape = RoundedCornerShape(4.dp), // Dikdörtgen
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            "Kaydet",
                            fontSize = 12.sp // Küçük punto
                        )
                    }
                }
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
                    fontWeight = FontWeight.Bold
                )

                Switch(
                    checked = isActive,
                    onCheckedChange = { isActive = it }
                )
            }

            if (isActive) {
                Spacer(modifier = Modifier.height(16.dp))

                // Puanlama sistemi belirleme - settings'e göre
                val scoringType = criteriaSettings?.get("scoringType") as? String ?: "separate"
                val scoreScale = (criteriaSettings?.get("scoreScale") as? Number)?.toInt() ?: 10

                when (scoringType) {
                    "comparative" -> {
                        // Kıyaslamalı puanlama - Slider ile
                        ComparativeScoring(
                            team1Name = team1Name,
                            team2Name = team2Name,
                            scoreScale = scoreScale,
                            currentScores = currentScores,
                            onScoresChanged = onScoresChanged
                        )
                    }
                    else -> {
                        // Ayrı ayrı puanlama - Dropdown ile (varsayılan)
                        SeparateScoring(
                            team1Name = team1Name,
                            team2Name = team2Name,
                            scoreScale = scoreScale,
                            currentScores = currentScores,
                            onScoresChanged = onScoresChanged
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeparateScoring(
    team1Name: String,
    team2Name: String,
    scoreScale: Int,
    currentScores: Pair<Double?, Double?>,
    onScoresChanged: (Double?, Double?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Takım 1 puanlama
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = team1Name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1976D2), // Mavi
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            ScoreDropdown(
                score = currentScores.first,
                scoreScale = scoreScale,
                onScoreChange = { newScore ->
                    onScoresChanged(newScore, currentScores.second)
                }
            )
        }

        // Takım 2 puanlama
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = team2Name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF388E3C), // Yeşil
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            ScoreDropdown(
                score = currentScores.second,
                scoreScale = scoreScale,
                onScoreChange = { newScore ->
                    onScoresChanged(currentScores.first, newScore)
                }
            )
        }
    }
}

@Composable
private fun ComparativeScoring(
    team1Name: String,
    team2Name: String,
    scoreScale: Int,
    currentScores: Pair<Double?, Double?>,
    onScoresChanged: (Double?, Double?) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(scoreScale / 2f) }

    Column {
        // Takım isimleri
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = team1Name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1976D2) // Mavi
            )
            Text(
                text = team2Name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF388E3C) // Yeşil
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Slider
        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                val team1Score = scoreScale - it.toDouble()
                val team2Score = it.toDouble()
                onScoresChanged(team1Score, team2Score)
            },
            valueRange = 0f..scoreScale.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )

        // Skorlar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${(scoreScale - sliderValue).toInt()}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
            Text(
                text = "${sliderValue.toInt()}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF388E3C)
            )
        }
    }
}

@Composable
private fun ScoreDropdown(
    score: Double?,
    scoreScale: Int = 10,
    onScoreChange: (Double?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val scoreOptions = (1..scoreScale).map { it.toDouble() } + listOf(null)

    Box {
        Button(
            onClick = { expanded = true },
            modifier = Modifier
                .width(80.dp)
                .height(40.dp), // Dikdörtgen buton
            shape = RoundedCornerShape(4.dp), // Minimal corner radius
            contentPadding = PaddingValues(4.dp)
        ) {
            Text(
                text = score?.toInt()?.toString() ?: "—",
                fontSize = 12.sp, // Küçük punto
                fontWeight = FontWeight.Bold
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            scoreOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option?.toInt()?.toString() ?: "Seçim Yap",
                            fontSize = 12.sp
                        )
                    },
                    onClick = {
                        onScoreChange(option)
                        expanded = false
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
    onShowCriteriaDialog: (Boolean) -> Unit = {},
    onShowStandingsDialog: (Boolean) -> Unit = {}
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

        // YENİ TAM EKRAN 6 KATMANLI SCROLL PENCERELİ LAYOUT
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. SABİT: TUR İLERLEME ÇUBUĞU
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

            // 2. SABİT: "HANGİSİ DAHA İYİ?" YAZISI
            Text(
                text = if (useScores) "Maç Skoru Girin" else "Hangisi daha iyi?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )

            // 3. SABİT: İLK TAKIM ETİKETİ
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

            // 4. SCROLL PENCERESİ: İLK TAKIM TABLOSU
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Available space'in yarısını al
                    .background(Color(0xFFE3F2FD))
                    .border(1.dp, Color.Gray)
                    .clickable {
                        if (!useScores) {
                            uiState.song1?.id?.let { songId ->
                                onMatchResult(match.id, songId)
                            }
                        }
                    }
            ) {
                uiState.song1?.let { song1 ->
                    // CSV data'yı önceden hesapla
                    val csvData = song1.csvData
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

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (jsonData != null) {
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
                        } else {
                            item {
                                Column {
                                    Text(
                                        text = song1.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    song1.artist?.let { artist ->
                                        Text(
                                            text = artist,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. SABİT: 3 BUTON SATIRI (BERABERLİK + SKOR İŞLE + KRİTER)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Beraberlik butonu
                Button(
                    onClick = {
                        onMatchResult(match.id, null) // null means draw
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text(
                        text = "BERABERLİK",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Skor İşle butonu
                Button(
                    onClick = {
                        // TODO: Skor işle dialog'unu aç
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Text(
                        text = "SKOR İŞLE",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Kriter Değerlendirmesi butonu
                Button(
                    onClick = {
                        onShowCriteriaDialog(true)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text(
                        text = "KRİTER",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // 6. SABİT: İKİNCİ TAKIM ETİKETİ
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

            // 7. SCROLL PENCERESİ: İKİNCİ TAKIM TABLOSU
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Available space'in yarısını al
                    .background(Color(0xFFE3F2FD))
                    .border(1.dp, Color.Gray)
                    .clickable {
                        if (!useScores) {
                            uiState.song2?.id?.let { songId ->
                                onMatchResult(match.id, songId)
                            }
                        }
                    }
            ) {
                uiState.song2?.let { song2 ->
                    // CSV data'yı önceden hesapla
                    val csvData = song2.csvData
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

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (jsonData != null) {
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
                        } else {
                            item {
                                Column {
                                    Text(
                                        text = song2.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    song2.artist?.let { artist ->
                                        Text(
                                            text = artist,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
