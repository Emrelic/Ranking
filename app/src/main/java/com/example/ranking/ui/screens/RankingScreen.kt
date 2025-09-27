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
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(420.dp) // 3x büyütülmüş yükseklik (140dp * 3)
                                        .clickable {
                                            onMatchResult(match.id, song1.id)
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp)
                                    ) {
                                        // Header - Takım adı
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF2196F3))
                                                .padding(12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = song1.name.uppercase(),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // BÜYÜK TABLO ALANI: 3x genişlik
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                        ) {
                                            TeamCardContent(
                                                song = song1,
                                                modifier = Modifier.fillMaxSize()
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
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(420.dp) // 3x büyütülmüş yükseklik (140dp * 3)
                                        .clickable {
                                            onMatchResult(match.id, song2.id)
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp)
                                    ) {
                                        // Header - Takım adı
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF2196F3))
                                                .padding(12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = song2.name.uppercase(),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // BÜYÜK TABLO ALANI: 3x genişlik
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                        ) {
                                            TeamCardContent(
                                                song = song2,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            // Draw button
                            Button(
                                onClick = {
                                    onMatchResult(match.id, null) // null means draw
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Text(
                                    text = "BERABERLİK",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
