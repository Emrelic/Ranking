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
import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ui.viewmodel.FixtureViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixtureScreen(
    listId: Long,
    method: String,
    onNavigateBack: () -> Unit,
    onNavigateToRanking: (Long, String) -> Unit,
    viewModel: FixtureViewModel = viewModel()
) {
    LaunchedEffect(listId, method) {
        viewModel.loadFixture(listId, method)
    }
    
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TopAppBar(
            title = { 
                Text("Fikstür - ${getMethodTitle(method)}")
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                }
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.matches.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Henüz fikstür oluşturulmamış",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onNavigateToRanking(listId, method) }
                ) {
                    Text("Sıralamaya Başla")
                }
            }
        } else {
            // Progress info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${uiState.completedMatches}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tamamlanan",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${uiState.totalMatches}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Toplam",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${((uiState.completedMatches.toFloat() / uiState.totalMatches.toFloat()) * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tamamlandı",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Group matches by round for all methods
            val matchesByRound = uiState.matches.groupBy { it.round }.toSortedMap()
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                matchesByRound.forEach { (round, matches) ->
                    item {
                        val roundTitle = when (method) {
                            "LEAGUE" -> {
                                val maxRound = matchesByRound.keys.maxOrNull() ?: 1
                                val firstHalfRounds = (maxRound + 1) / 2
                                if (round <= firstHalfRounds) {
                                    "Hafta $round"
                                } else {
                                    "Hafta ${round - firstHalfRounds} (Rövanş)"
                                }
                            }
                            "SWISS", "EMRE" -> "Tur $round"
                            else -> "Tur $round"
                        }
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                text = roundTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    
                    items(matches) { match ->
                        MatchCard(
                            match = match,
                            songs = uiState.songs,
                            useScores = uiState.leagueSettings?.useScores ?: false,
                            onEditMatch = { selectedMatch ->
                                viewModel.selectMatchForEdit(selectedMatch)
                            }
                        )
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onNavigateToRanking(listId, method) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (uiState.completedMatches < uiState.totalMatches) 
                                "Sıralamaya Devam Et" 
                            else 
                                "Sonuçları Görüntüle"
                        )
                    }
                }
            }
        }
        
        // Edit Match Dialog
        if (uiState.editingMatch != null) {
            EditMatchDialog(
                match = uiState.editingMatch!!,
                songs = uiState.songs,
                useScores = uiState.leagueSettings?.useScores ?: false,
                allowDraws = uiState.leagueSettings?.allowDraws ?: true,
                onDismiss = { viewModel.cancelEdit() },
                onSave = { updatedMatch ->
                    viewModel.saveMatchEdit(updatedMatch)
                }
            )
        }
    }
}

@Composable
private fun MatchCard(
    match: Match,
    songs: List<Song>,
    useScores: Boolean,
    onEditMatch: (Match) -> Unit = {}
) {
    val song1 = songs.find { it.id == match.songId1 }
    val song2 = songs.find { it.id == match.songId2 }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (match.isCompleted) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Edit button for completed matches
            if (match.isCompleted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { onEditMatch(match) }
                    ) {
                        Text("Düzenle", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team 1
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = song1?.name ?: "Bilinmeyen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (match.winnerId == song1?.id) FontWeight.Bold else FontWeight.Normal
                    )
                    if (song1?.artist?.isNotBlank() == true) {
                        Text(
                            text = song1.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Score/Result
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(0.5f)
                ) {
                    if (match.isCompleted) {
                        if (useScores && match.score1 != null && match.score2 != null) {
                            Text(
                                text = "${match.score1} - ${match.score2}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = when (match.winnerId) {
                                    song1?.id -> "1-0"
                                    song2?.id -> "0-1"
                                    null -> "0-0"
                                    else -> "-"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Text(
                            text = when (match.winnerId) {
                                song1?.id -> "${song1?.name ?: "Bilinmeyen"} Kazandı"
                                song2?.id -> "${song2?.name ?: "Bilinmeyen"} Kazandı"
                                null -> "Berabere"
                                else -> "Sonuç Belirsiz"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "VS",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Oynanacak",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Team 2
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = song2?.name ?: "Bilinmeyen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (match.winnerId == song2?.id) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.End
                    )
                    if (song2?.artist?.isNotBlank() == true) {
                        Text(
                            text = song2.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditMatchDialog(
    match: Match,
    songs: List<Song>,
    useScores: Boolean,
    allowDraws: Boolean,
    onDismiss: () -> Unit,
    onSave: (Match) -> Unit
) {
    val song1 = songs.find { it.id == match.songId1 }
    val song2 = songs.find { it.id == match.songId2 }
    
    var selectedWinner by remember { mutableStateOf(match.winnerId) }
    var score1Text by remember { mutableStateOf(match.score1?.toString() ?: "") }
    var score2Text by remember { mutableStateOf(match.score2?.toString() ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Maç Sonucunu Düzenle") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Team names
                Text(
                    text = "${song1?.name ?: "Bilinmeyen"} vs ${song2?.name ?: "Bilinmeyen"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (useScores) {
                    // Score input mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(song1?.name ?: "Takım 1", modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = score1Text,
                            onValueChange = { 
                                if (it.all { char -> char.isDigit() }) {
                                    score1Text = it
                                    // Auto-calculate winner based on scores
                                    val s1 = it.toIntOrNull()
                                    val s2 = score2Text.toIntOrNull()
                                    if (s1 != null && s2 != null) {
                                        selectedWinner = when {
                                            s1 > s2 -> song1?.id
                                            s2 > s1 -> song2?.id
                                            else -> null
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.width(60.dp),
                            singleLine = true
                        )
                        Text(" - ")
                        OutlinedTextField(
                            value = score2Text,
                            onValueChange = { 
                                if (it.all { char -> char.isDigit() }) {
                                    score2Text = it
                                    // Auto-calculate winner based on scores
                                    val s1 = score1Text.toIntOrNull()
                                    val s2 = it.toIntOrNull()
                                    if (s1 != null && s2 != null) {
                                        selectedWinner = when {
                                            s1 > s2 -> song1?.id
                                            s2 > s1 -> song2?.id
                                            else -> null
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.width(60.dp),
                            singleLine = true
                        )
                        Text(song2?.name ?: "Takım 2", modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                } else {
                    // Winner selection mode
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Kazanan:")
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedWinner == song1?.id,
                                onClick = { selectedWinner = song1?.id }
                            )
                            Text(
                                text = song1?.name ?: "Takım 1",
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedWinner == song2?.id,
                                onClick = { selectedWinner = song2?.id }
                            )
                            Text(
                                text = song2?.name ?: "Takım 2",
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        
                        if (allowDraws) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedWinner == null,
                                    onClick = { selectedWinner = null }
                                )
                                Text(
                                    text = "Berabere",
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedMatch = if (useScores) {
                        val s1 = score1Text.toIntOrNull()
                        val s2 = score2Text.toIntOrNull()
                        if (s1 != null && s2 != null) {
                            match.copy(
                                winnerId = selectedWinner,
                                score1 = s1,
                                score2 = s2
                            )
                        } else {
                            match // Invalid scores, don't update
                        }
                    } else {
                        match.copy(
                            winnerId = selectedWinner,
                            score1 = null,
                            score2 = null
                        )
                    }
                    onSave(updatedMatch)
                },
                enabled = if (useScores) {
                    score1Text.toIntOrNull() != null && score2Text.toIntOrNull() != null
                } else {
                    selectedWinner != null || allowDraws
                }
            ) {
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

private fun getMethodTitle(method: String): String {
    return when (method) {
        "DIRECT_SCORING" -> "Direkt Puanlama"
        "LEAGUE" -> "Lig Sistemi"
        "ELIMINATION" -> "Eleme Sistemi"
        "SWISS" -> "İsviçre Sistemi"
        "EMRE" -> "Emre Usulü"
        else -> "Sıralama"
    }
}