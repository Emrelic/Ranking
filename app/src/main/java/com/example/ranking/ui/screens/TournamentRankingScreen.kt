package com.example.ranking.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
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
    onNavigateToResults: (Long, String) -> Unit,
    onNavigateToRanking: (Long, String) -> Unit = { _, _ -> },
    viewModel: TournamentRankingViewModel = viewModel()
) {
    LaunchedEffect(tournamentId) {
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
                    TextButton(onClick = {
                        tournament?.let { onNavigateToResults(it.songListId, it.systemType) }
                    }) {
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
        } else if (uiState.errorMessage?.startsWith("REDIRECT_TO_RANKING_SCREEN") == true) {
            // Yönlendirme mesajı kullanıcıya hata olarak gösterilmez;
            // LaunchedEffect navigasyonu yapana kadar yükleme göstergesi kalır
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
                            onViewResults = {
                                tournament?.let { onNavigateToResults(it.songListId, it.systemType) }
                            }
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
    var showCriteriaEvaluation by remember { mutableStateOf(false) } // Varsayılan false
    
    // Tam ekran dialog - kenarlar ekran sınırlarına bitişik
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. SABİT BAŞLIK - Kriter Değerlendirmesi + Kapat Çarpısı
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Kriter Değerlendirmesi",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    
                    // Ufak çarpı butonu sağ üst köşe
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                // 2. SABİT TAKIM ETİKETLERİ - Yatay bant ekranın yarısı renk
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    // Sol yarı - Takım A
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFF1976D2)), // Mavi
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "TAKIM A",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    // Sağ yarı - Takım B  
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFFE57373)), // Kırmızı
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "TAKIM B",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                
                // 3. CHECKBOX KONTROL ALANI
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showCriteriaEvaluation,
                        onCheckedChange = { showCriteriaEvaluation = it }
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "Kriter değerlendirme çerçevelerini göster",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                // 4. SCROLL EDİLEBİLİR ORTA BÖLÜM
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showCriteriaEvaluation) {
                        items(5) { index ->
                            CriterionRow(
                                criterionName = "Kriter ${index + 1}",
                                team1Score = null,
                                team2Score = null,
                                onTeam1ScoreChange = { },
                                onTeam2ScoreChange = { }
                            )
                        }
                    } else {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Kriter değerlendirme çerçevelerini görmek için yukarıdaki kutucuğu işaretleyin",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                
                // 5. SABİT TOPLAM PUAN KUTUCUKLARI
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Takım A",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "0.0",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Takım B",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "0.0",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(60.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onMatchResult(1) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Takım A",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Kazandı",
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Button(
                        onClick = { onMatchResult(0) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Berabere",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Skor eşit",
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Button(
                        onClick = { onMatchResult(2) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Takım B",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Kazandı",
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 6. SABİT İPTAL VE KAYDET BUTONLARI - En altta
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("İptal")
                    }
                    
                    Button(
                        onClick = {
                            onSaveScores(emptyMap())
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Kaydet")
                    }
                }
            }
        }
    }
}

@Composable
private fun CriterionRow(
    criterionName: String,
    team1Score: Double?,
    team2Score: Double?,
    onTeam1ScoreChange: (Double?) -> Unit,
    onTeam2ScoreChange: (Double?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = criterionName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Takım A skoru
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Takım A",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    var team1Text by remember { mutableStateOf(team1Score?.toString() ?: "") }
                    
                    OutlinedTextField(
                        value = team1Text,
                        onValueChange = { 
                            team1Text = it
                            onTeam1ScoreChange(it.toDoubleOrNull())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Puan") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
                
                // Takım B skoru
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Takım B",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    var team2Text by remember { mutableStateOf(team2Score?.toString() ?: "") }
                    
                    OutlinedTextField(
                        value = team2Text,
                        onValueChange = { 
                            team2Text = it
                            onTeam2ScoreChange(it.toDoubleOrNull())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Puan") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }
        }
    }
}
