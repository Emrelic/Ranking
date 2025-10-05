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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
    isResuming: Boolean = false, // DEVAM EDEN TURNUVA: false = yeni, true = devam eden
    onNavigateBack: () -> Unit,
    onNavigateToResults: (Long, String) -> Unit,
    onNavigateToFixture: (Long, String) -> Unit = { _, _ -> },
    viewModel: RankingViewModel = viewModel()
) {
    LaunchedEffect(listId, method, pairingMethodName, isResuming) {
        // YENİ TURNUVA: Eski verileri temizle, DEVAM EDEN: Mevcut verileri koru
        viewModel.initializeRanking(listId, method, pairingMethodName, forceNew = !isResuming)
    }
    
    val uiState by viewModel.uiState.collectAsState()
    var showCriteriaDialog by remember { mutableStateOf(false) }
    var showStandingsDialog by remember { mutableStateOf(false) }
    var showScoreDialog by remember { mutableStateOf(false) }
    
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
                onShowStandingsDialog = { showStandingsDialog = it },
                showScoreDialog = showScoreDialog,
                onShowScoreDialog = { showScoreDialog = it }
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
        
        // Score Input Dialog
        if (showScoreDialog) {
            ScoreInputDialog(
                match = when (method) {
                    "LEAGUE", "SWISS", "EMRE_CORRECT" -> uiState.currentMatch
                    else -> null
                },
                song1 = uiState.song1,
                song2 = uiState.song2,
                onDismiss = { showScoreDialog = false },
                onSave = { team1Score, team2Score ->
                    showScoreDialog = false
                    // Skor girişini işle
                    when {
                        team1Score > team2Score -> uiState.song1?.id?.let { viewModel.submitMatchResult(uiState.currentMatch?.id ?: 0, it) }
                        team2Score > team1Score -> uiState.song2?.id?.let { viewModel.submitMatchResult(uiState.currentMatch?.id ?: 0, it) }
                        else -> {
                            // Beraberlik durumu - skor kaydet
                            viewModel.submitDrawResult(uiState.currentMatch?.id ?: 0, team1Score, team2Score)
                        }
                    }
                }
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
                        style = MaterialTheme.typography.titleMedium,
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
                
                // Butonlar kaldırıldı - Ana ekranda zaten mevcut
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
    viewModel: RankingViewModel,
    method: String = "EMRE_CORRECT" // Method parametresi eklendi
) {
    var isAdvancedView by remember { mutableStateOf(true) } // Büyük kartlar varsayılan

    // Debug logging
    android.util.Log.d("RANKING_DEBUG", "MatchingsListContent render - matchingsList.size: ${uiState.matchingsList.size}")
    android.util.Log.d("RANKING_DEBUG", "MatchingsList içeriği: ${uiState.matchingsList.map { "${it.id}:${it.matchNumber}" }}")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 8.dp) // Ekran kenarlarına daha yakın
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
                    text = "Komplike",
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
                    .weight(1f), // Take remaining space, push button to bottom
                verticalArrangement = Arrangement.spacedBy(2.dp) // Kartlar arası spacing azaltıldı
            ) {
                items(uiState.matchingsList) { match ->
                    android.util.Log.d("RANKING_DEBUG", "Rendering match card: ${match.id}:${match.matchNumber}")
                    if (isAdvancedView) {
                        // Büyük görünüm - Büyük tablo kartları
                        AdvancedMatchCard(
                            match = match,
                            songs = uiState.allSongs,
                            method = method,
                            emreState = uiState.emreState,
                            onClick = {
                                // Kart tıklama artık puanlama ekranına geçmiyor
                                android.util.Log.d("RANKING_DEBUG", "AdvancedMatchCard clicked but no action")
                            }
                        )
                    } else {
                        // Küçük görünüm - Küçük kartlar  
                        SimpleMatchCard(
                            match = match,
                            songs = uiState.allSongs,
                            onClick = { 
                                // Kart tıklama artık puanlama ekranına geçmiyor
                                android.util.Log.d("RANKING_DEBUG", "SimpleMatchCard clicked but no action")
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

private fun parseJsonToMap(jsonString: String): Map<String, String> {
    return try {
        // Basit JSON parsing - bracket'ları temizle ve key-value çiftlerini parse et
        val cleanJson = jsonString.trim().removePrefix("{").removeSuffix("}")
        val pairs = cleanJson.split(",")
        val map = mutableMapOf<String, String>()
        
        pairs.forEach { pair ->
            val keyValue = pair.split(":")
            if (keyValue.size == 2) {
                val key = keyValue[0].trim().removePrefix("\"").removeSuffix("\"")
                val value = keyValue[1].trim().removePrefix("\"").removeSuffix("\"")
                map[key] = value
            }
        }
        map
    } catch (e: Exception) {
        emptyMap()
    }
}

@Composable
private fun TableRow(
    label: String,
    value: String
) {
    // Resimdeki format: Etiket üstte, veri altında
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally // Center alignment
    ) {
        // Etiket üstte - koyu arka plan ile vurgulu
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color(0xFF0277BD).copy(alpha = 0.1f), // Açık mavi arka plan
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(vertical = 2.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF0277BD),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        
        // Veri altında - büyük ve siyah, center aligned
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
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
    method: String = "EMRE_CORRECT",
    emreState: com.example.ranking.ranking.EmreSystemCorrect.EmreState? = null,
    onClick: () -> Unit
) {
    val song1 = songs.find { it.id == match.songId1 }
    val song2 = songs.find { it.id == match.songId2 }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }, // Padding tamamen kaldırıldı
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 1.dp, vertical = 2.dp) // Daha da minimal padding
        ) {
            // Maç numarası başlığı
            Text(
                text = "${match.matchNumber}. Eşleşme",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Resimdeki gibi side-by-side tablo formatı - TAM EŞLEŞTİRME
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp), // Daha minimal boşluk
                verticalAlignment = Alignment.Top // Kartları üstten hizala
            ) {
                // Sol takım kartı - AFGANİSTAN formatı (Box ile wrap - puan rozeti için)
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column {
                            // Başlık - Ana tema ile uyumlu
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = song1?.name?.uppercase() ?: "AFGANİSTAN",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Tablo verileri - Resimdeki format
                            if (song1 != null && song1.csvData != null) {
                                val csvMap = parseJsonToMap(song1.csvData!!)
                                Column(modifier = Modifier.padding(8.dp)) {
                                    if (csvMap.isNotEmpty()) {
                                        csvMap.forEach { (key, value) ->
                                            TableRow(
                                                label = key,
                                                value = value
                                            )
                                        }
                                    } else {
                                        TableRow("Name", song1.name)
                                        TableRow("Artist", song1.artist)
                                    }
                                }
                            } else {
                                // Placeholder data resimdeki gibi
                                Column(modifier = Modifier.padding(8.dp)) {
                                    TableRow("Kıta", "Asya")
                                    TableRow("Nüfus (Milyon)", "438")
                                    TableRow("Yüzölçümü (km²)", "652230")
                                    TableRow("GSYİH (Milyar USD)", "18")
                                    TableRow("Kişi Başına GSYİH (USD)", "411")
                                }
                            }
                        }
                    }

                    // PUAN ROZETİ - SAĞ ALT KÖŞE (TÜM USULLER)
                    if (song1 != null) {
                        val currentPoints = when (method) {
                            "EMRE_CORRECT" -> emreState?.teams?.find { it.song.id == song1.id }?.points ?: 0.0
                            else -> 0.0 // Diğer usuller için de genişletilebilir
                        }

                        if (currentPoints > 0.0 || method == "EMRE_CORRECT") {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 8.dp, y = 8.dp)  // Offset - merkezini köşeye yerleştir
                                    .size(32.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (currentPoints % 1.0 == 0.0) "${currentPoints.toInt()}" else "${currentPoints}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }

                // VS yazısı kartlar arasında - Horizontal ortada
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 2.dp) // Daha az padding
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "V",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "S",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Sağ takım kartı - ARNAVUTLUK formatı (Box ile wrap - puan rozeti için)
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column {
                            // Başlık - Ana tema ile uyumlu
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = song2?.name?.uppercase() ?: "ARNAVUTLUK",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Tablo verileri - Resimdeki format
                            if (song2 != null && song2.csvData != null) {
                                val csvMap = parseJsonToMap(song2.csvData!!)
                                Column(modifier = Modifier.padding(8.dp)) {
                                    if (csvMap.isNotEmpty()) {
                                        csvMap.forEach { (key, value) ->
                                            TableRow(
                                                label = key,
                                                value = value
                                            )
                                        }
                                    } else {
                                        TableRow("Name", song2.name)
                                        TableRow("Artist", song2.artist)
                                    }
                                }
                            } else {
                                // Placeholder data resimdeki gibi
                                Column(modifier = Modifier.padding(8.dp)) {
                                    TableRow("Kıta", "Avrupa")
                                    TableRow("Nüfus (Milyon)", "28")
                                    TableRow("Yüzölçümü (km²)", "28748")
                                    TableRow("GSYİH (Milyar USD)", "28")
                                    TableRow("Kişi Başına GSYİH (USD)", "10000")
                                }
                            }
                        }
                    }

                    // PUAN ROZETİ - SAĞ ALT KÖŞE (TÜM USULLER)
                    if (song2 != null) {
                        val currentPoints = when (method) {
                            "EMRE_CORRECT" -> emreState?.teams?.find { it.song.id == song2.id }?.points ?: 0.0
                            else -> 0.0 // Diğer usuller için de genişletilebilir
                        }

                        if (currentPoints > 0.0 || method == "EMRE_CORRECT") {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 8.dp, y = 8.dp)  // Offset - merkezini köşeye yerleştir
                                    .size(32.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (currentPoints % 1.0 == 0.0) "${currentPoints.toInt()}" else "${currentPoints}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
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
    // Sistem barlarını gizle (immersive mode)
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window
        window?.let {
            val insetsController = WindowCompat.getInsetsController(it, view)
            insetsController.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    // Dialog kapandığında sistem barlarını geri göster
    DisposableEffect(Unit) {
        onDispose {
            val window = (view.context as? android.app.Activity)?.window
            window?.let {
                val insetsController = WindowCompat.getInsetsController(it, view)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .height(2330.dp)
                .offset(y = 0.dp),
            shape = RoundedCornerShape(0.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f))
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f))
                )
            }
            
            Box(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 0.dp) // HORIZONTAL PADDİNG KALDIRILDI - EKRAN SINIRLARINI KULLAN
                ) {
                    // BAŞLIK - Kompakt hale getirildi
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 16.dp), // Padding azaltıldı
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Kriter Değerlendirmesi",
                            style = MaterialTheme.typography.titleLarge, // Daha küçük font
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // TAKIM ETİKETLERİ - Kompakt hale getirildi
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // SOL TAKIM ETİKETİ
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(vertical = 8.dp), // Padding azaltıldı
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = song1?.name?.uppercase() ?: "TAKIM 1",
                                style = MaterialTheme.typography.titleMedium, // Daha küçük font
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center
                            )
                        }

                        // SAĞ TAKIM ETİKETİ
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(vertical = 8.dp), // Padding azaltıldı
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = song2?.name?.uppercase() ?: "TAKIM 2",
                                style = MaterialTheme.typography.titleMedium, // Daha küçük font
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // SCROLL ALAN - Kriterler
                    val criteriaScores = remember { mutableStateMapOf<String, Pair<Double?, Double?>>() }
                    val expandedCriteria = remember { mutableStateMapOf<String, Boolean>() }
                    var criteria by remember { mutableStateOf<List<String>>(emptyList()) }
                    var criteriaSettings by remember { mutableStateOf<Map<String, Any>?>(null) }
                    
                    // Turnuva ID'sinden criteria'ları al
                    LaunchedEffect(tournamentId) {
                        criteria = viewModel.getCriteriaForTournament(tournamentId)
                        criteriaSettings = viewModel.getCriteriaSettingsForTournament(tournamentId)
                    }

                    // Eğer criteria bulunamazsa demo data
                    val finalCriteria = if (criteria.isNotEmpty()) criteria else listOf(
                        "Teknik Yetenek",
                        "Yaratıcılık", 
                        "Performans",
                        "Orijinallik",
                        "Sahne Hâkimiyeti"
                    )
                    
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 0.dp) // HORIZONTAL PADDİNG KALDIRILDI - MAKSİMUM GENİŞLİK
                    ) {
                        items(finalCriteria) { criterion ->
                            CriterionBox(
                                criterionName = criterion,
                                team1Name = song1?.name ?: "Takım 1",
                                team2Name = song2?.name ?: "Takım 2",
                                isExpanded = expandedCriteria[criterion] ?: false,
                                currentScores = criteriaScores[criterion] ?: Pair(null, null),
                                criteriaSettings = criteriaSettings,
                                onExpandToggle = { expanded ->
                                    expandedCriteria[criterion] = expanded
                                },
                                onScoresChanged = { team1Score, team2Score ->
                                    criteriaScores[criterion] = Pair(team1Score, team2Score)
                                }
                            )
                        }
                    }
                    
                    // FOOTER - Toplam puanlar ve butonlar
                    val team1Total = criteriaScores.values
                        .filter { it.first != null && expandedCriteria.any { expanded -> expanded.value } }
                        .sumOf { it.first ?: 0.0 }
                        
                    val team2Total = criteriaScores.values
                        .filter { it.second != null && expandedCriteria.any { expanded -> expanded.value } }
                        .sumOf { it.second ?: 0.0 }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                3.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(16.dp)
                            ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp) // Padding azaltıldı
                        ) {
                            // TOPLAM KUTUCUKLARI - Güzel tasarımla
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.weight(1f).padding(end = 0.dp)
                                ) {
                                    Text(
                                        text = "Toplam ${team1Total.toInt()}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    )
                                }

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.weight(1f).padding(start = 0.dp)
                                ) {
                                    Text(
                                        text = "Toplam ${team2Total.toInt()}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            // GALİB/BERABERE BUTONLARI - TAM YAY VE BİTİŞİK
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                // Takım 1 Galib - Sol köşe yuvarlak
                                Button(
                                    onClick = { onSave(criteriaScores.toMap(), "team1_wins") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp)
                                ) {
                                    Text(
                                        text = "${song1?.name ?: "Takım 1"}\nGalib",
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                // Berabere - Ortada köşesiz
                                Button(
                                    onClick = { onSave(criteriaScores.toMap(), "draw") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    shape = RectangleShape
                                ) {
                                    Text(
                                        text = "Berabere",
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }

                                // Takım 2 Galib - Sağ köşe yuvarlak
                                Button(
                                    onClick = { onSave(criteriaScores.toMap(), "team2_wins") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp)
                                ) {
                                    Text(
                                        text = "${song2?.name ?: "Takım 2"}\nGalib",
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // İPTAL/KAYDET BUTONLARI
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 0.dp), // HORIZONTAL PADDİNG KALDIRILDI
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f).padding(end = 2.dp) // PADDİNG AZALTILDI
                                ) {
                                    Text("İptal")
                                }
                                Button(
                                    onClick = { onSave(criteriaScores.toMap(), "save_only") },
                                    modifier = Modifier.weight(1f).padding(start = 2.dp) // PADDİNG AZALTILDI
                                ) {
                                    Text("Kaydet")
                                }
                            }
                        }
                    }

                }
            }
        }
    }
}

@Composable
private fun CriterionBox(
    criterionName: String,
    team1Name: String,
    team2Name: String,
    isExpanded: Boolean,
    currentScores: Pair<Double?, Double?>,
    criteriaSettings: Map<String, Any>?,
    onExpandToggle: (Boolean) -> Unit,
    onScoresChanged: (Double?, Double?) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 0.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(
            width = 2.dp,
            color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        // ARKAPLAN: Ana tema ile uyumlu yarı yarıya renkli
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))
            )
        }

        Column {
            // Kriter başlığı ve checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandToggle(!isExpanded) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = criterionName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Checkbox(
                    checked = isExpanded,
                    onCheckedChange = onExpandToggle,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.secondary
                    )
                )
            }
            
            // Açılır pencerecik - Ayarlara göre değişken
            if (isExpanded) {
                val scoringType = criteriaSettings?.get("scoringType") as? String ?: "separate"
                val scoreScale = (criteriaSettings?.get("scoreScale") as? Number)?.toInt() ?: 10
                val useSeparateScoring = criteriaSettings?.get("useSeparateScoring") as? Boolean ?: true
                
                if (useSeparateScoring || scoringType == "separate") {
                    // AYRI AYRI DEĞERLENDİRME - Dropdown'lar
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Sol yarı - Takım 1
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = team1Name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                ScoreDropdown(
                                    score = currentScores.first,
                                    maxScore = scoreScale,
                                    onScoreChange = { newScore ->
                                        onScoresChanged(newScore, currentScores.second)
                                    }
                                )
                            }
                        }
                        
                        // Sağ yarı - Takım 2
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = team2Name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                ScoreDropdown(
                                    score = currentScores.second,
                                    maxScore = scoreScale,
                                    onScoreChange = { newScore ->
                                        onScoresChanged(currentScores.first, newScore)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // KIYASLAMALI DEĞERLENDİRME - Slider sistemi
                    ComparativeSlider(
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

// KIYASLAMALI SLIDER SİSTEMİ
@Composable
private fun ComparativeSlider(
    team1Name: String,
    team2Name: String,
    scoreScale: Int,
    currentScores: Pair<Double?, Double?>,
    onScoresChanged: (Double?, Double?) -> Unit
) {
    var sliderValue by remember { mutableStateOf(50f) } // Başlangıç 50% (5-5 dağılım)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Takım isimleri ve puanlar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val team1Score = (sliderValue * scoreScale / 100f).toInt()
                val team2Score = ((100f - sliderValue) * scoreScale / 100f).toInt()
                
                Text(
                    text = "$team1Name: $team1Score",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
                Text(
                    text = "$team2Name: $team2Score",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7B1FA2)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Çubuk
            Slider(
                value = sliderValue,
                onValueChange = { newValue ->
                    sliderValue = newValue
                    val team1Score = (newValue * scoreScale / 100f).toDouble()
                    val team2Score = ((100f - newValue) * scoreScale / 100f).toDouble()
                    onScoresChanged(team1Score, team2Score)
                },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF1976D2),
                    activeTrackColor = Color(0xFF1976D2),
                    inactiveTrackColor = Color(0xFF7B1FA2)
                )
            )
        }
    }
}

// PUAN DROPDOWN SİSTEMİ

@Composable
private fun ScoreDropdown(
    score: Double?,
    maxScore: Int = 10,
    onScoreChange: (Double?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = score?.toInt()?.toString() ?: "Seç",
                style = MaterialTheme.typography.bodyLarge, // BÜYÜK FONT
                fontWeight = FontWeight.Bold // BOLD FONT
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            (0..maxScore).forEach { scoreValue ->
                DropdownMenuItem(
                    text = { 
                        Text(
                            text = scoreValue.toString(),
                            style = MaterialTheme.typography.bodyLarge, // BÜYÜK FONT
                            fontWeight = FontWeight.Bold // BOLD FONT
                        ) 
                    },
                    onClick = {
                        onScoreChange(scoreValue.toDouble())
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
    onShowStandingsDialog: (Boolean) -> Unit = {},
    showScoreDialog: Boolean = false,
    onShowScoreDialog: (Boolean) -> Unit = {}
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
            viewModel = viewModel,
            method = method
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

            // 2. SABİT: "HANGİSİ DAHA İYİ?" YAZISI - ANA SAYFA TEMASIYLA UYUMLU
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp), // ANA SAYFA KÖŞE YUVARLAKLIGI
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), // ANA SAYFA ELEVATION
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = if (useScores) "Maç Skoru Girin" else "Hangisi daha iyi?",
                    style = MaterialTheme.typography.headlineSmall, // ANA SAYFA FONT STİLİ
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp), // ANA SAYFA PADDİNG
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer // ANA SAYFA RENK UYUMU
                )
            }

            // 3. SABİT: İLK TAKIM ETİKETİ - ANA TEMA İLE UYUMLU
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = uiState.song1?.name?.uppercase() ?: "TAKIM 1",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }

            // 4. SCROLL PENCERESİ: İLK TAKIM TABLOSU - Çerçeveli ve yuvarlak
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Available space'in yarısını al
                    .padding(horizontal = 16.dp, vertical = 4.dp) // Çerçeve için boşluk
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        3.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        if (!useScores) {
                            uiState.song1?.id?.let { songId ->
                                onMatchResult(match.id, songId)
                            }
                        }
                    }
            ) {
                uiState.song1?.let { song1 ->
                    // PUAN ROZETİ - SAĞ ALT KÖŞE (TÜM USULLER)
                    val currentPoints = when (method) {
                        "EMRE_CORRECT" -> {
                            if (uiState.emreState?.teams?.isNotEmpty() == true) {
                                uiState.emreState.teams.find { it.song.id == song1.id }?.points ?: 0.0
                            } else {
                                0.0
                            }
                        }
                        else -> 0.0 // Diğer usuller için genişletilebilir
                    }

                    if (currentPoints > 0.0 || method == "EMRE_CORRECT") {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 8.dp, y = 8.dp)  // Offset - merkezini köşeye yerleştir
                                .size(32.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (currentPoints % 1.0 == 0.0) "${currentPoints.toInt()}" else "${currentPoints}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
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
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
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

            // 5. SABİT: 3 BUTON SATIRI - TAM YAY VE BİTİŞİK
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(vertical = 8.dp), // Sadece vertical padding
                horizontalArrangement = Arrangement.Start // Bitişik olmak için
            ) {
                // Beraberlik butonu - Sol köşe yuvarlak
                Button(
                    onClick = {
                        onMatchResult(match.id, null) // null means draw
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp)
                ) {
                    Text(
                        text = "BERABERLİK",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                // Skor İşle butonu - Ortada köşesiz
                Button(
                    onClick = {
                        onShowScoreDialog(true)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RectangleShape // Köşesiz, bitişik
                ) {
                    Text(
                        text = "SKOR İŞLE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Kriter Değerlendirmesi butonu - Sağ köşe yuvarlak
                Button(
                    onClick = {
                        onShowCriteriaDialog(true)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp)
                ) {
                    Text(
                        text = "KRİTER",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            // 6. SABİT: İKİNCİ TAKIM ETİKETİ - ANA TEMA İLE UYUMLU
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = uiState.song2?.name?.uppercase() ?: "TAKIM 2",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }

            // 7. SCROLL PENCERESİ: İKİNCİ TAKIM TABLOSU - Çerçeveli ve yuvarlak
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Available space'in yarısını al
                    .padding(horizontal = 16.dp, vertical = 4.dp) // Çerçeve için boşluk
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        3.dp,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        if (!useScores) {
                            uiState.song2?.id?.let { songId ->
                                onMatchResult(match.id, songId)
                            }
                        }
                    }
            ) {
                uiState.song2?.let { song2 ->
                    // PUAN ROZETİ - SAĞ ALT KÖŞE (TÜM USULLER)
                    val currentPoints = when (method) {
                        "EMRE_CORRECT" -> {
                            if (uiState.emreState?.teams?.isNotEmpty() == true) {
                                uiState.emreState.teams.find { it.song.id == song2.id }?.points ?: 0.0
                            } else {
                                0.0
                            }
                        }
                        else -> 0.0 // Diğer usuller için genişletilebilir
                    }

                    if (currentPoints > 0.0 || method == "EMRE_CORRECT") {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 8.dp, y = 8.dp)  // Offset - merkezini köşeye yerleştir
                                .size(32.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (currentPoints % 1.0 == 0.0) "${currentPoints.toInt()}" else "${currentPoints}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
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
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
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



// SKOR GİRİŞ DIALOG'U
@Composable
private fun ScoreInputDialog(
    match: Match?,
    song1: Song?,
    song2: Song?,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit
) {
    var team1Score by remember { mutableStateOf("") }
    var team2Score by remember { mutableStateOf("") }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            // ARKAPLAN: Yarı yarıya renk sistemi
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFFE3F2FD))
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFFF3E5F5))
                )
            }
            
            Column(modifier = Modifier.fillMaxSize()) {
                // BAŞLIK
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Skor Girişi",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // CLOSE BUTTON
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(8.dp)
                        .size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                
                // TAKIM ETİKETLERİ
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF1976D2))
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = song1?.name?.uppercase() ?: "TAKIM 1",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF7B1FA2))
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = song2?.name?.uppercase() ?: "TAKIM 2",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // SKOR GİRİŞ ALANLARI
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Takım 1 Skor
                    OutlinedTextField(
                        value = team1Score,
                        onValueChange = { team1Score = it },
                        label = { Text("${song1?.name ?: "Takım 1"} Skoru") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1976D2),
                            focusedLabelColor = Color(0xFF1976D2)
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Text(
                        text = "-",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Takım 2 Skor
                    OutlinedTextField(
                        value = team2Score,
                        onValueChange = { team2Score = it },
                        label = { Text("${song2?.name ?: "Takım 2"} Skoru") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF7B1FA2),
                            focusedLabelColor = Color(0xFF7B1FA2)
                        )
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // BUTONLAR
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("İptal")
                        }
                        
                        Button(
                            onClick = {
                                val score1 = team1Score.toIntOrNull() ?: 0
                                val score2 = team2Score.toIntOrNull() ?: 0
                                onSave(score1, score2)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = team1Score.isNotBlank() && team2Score.isNotBlank()
                        ) {
                            Text("Kaydet")
                        }
                    }
                }
            }
        }
    }
}
