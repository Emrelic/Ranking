package com.example.ranking.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ranking.ui.viewmodel.ResultsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    listId: Long,
    method: String,
    onNavigateBack: () -> Unit,
    onNavigateToFixture: (Long, String) -> Unit = { _, _ -> },
    viewModel: ResultsViewModel = viewModel()
) {
    LaunchedEffect(listId, method) {
        viewModel.loadResults(listId, method)
    }
    
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val archiveStatus by viewModel.archiveStatus.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TopAppBar(
            title = { 
                Text("${getMethodTitle(method)} Sonuçları")
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                }
            },
            actions = {
                if (method in listOf("LEAGUE", "SWISS", "EMRE_CORRECT", "ELIMINATION")) {
                    TextButton(
                        onClick = { onNavigateToFixture(listId, method) }
                    ) {
                        Text("Fikstür")
                    }
                }
                
                var showArchiveDialog by remember { mutableStateOf(false) }
                TextButton(
                    onClick = { showArchiveDialog = true }
                ) {
                    Text("Arşive Kaydet")
                }
                
                if (showArchiveDialog) {
                    ArchiveDialog(
                        listId = listId,
                        method = method,
                        viewModel = viewModel,
                        onDismiss = { showArchiveDialog = false },
                        onConfirm = { name ->
                            viewModel.archiveResults(listId, method, name)
                            showArchiveDialog = false
                        }
                    )
                }
            }
        )
        
        // Archive status handling
        archiveStatus?.let { status ->
            when (status) {
                is ResultsViewModel.ArchiveStatus.Loading -> {
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text("Arşivleniyor...") },
                        text = { 
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator()
                                Text("Sonuçlar arşive kaydediliyor...")
                            }
                        },
                        confirmButton = { }
                    )
                }
                is ResultsViewModel.ArchiveStatus.Success -> {
                    AlertDialog(
                        onDismissRequest = { viewModel.clearArchiveStatus() },
                        title = { Text("Başarılı!") },
                        text = { Text("\"${status.archiveName}\" başarıyla arşive kaydedildi.") },
                        confirmButton = {
                            TextButton(
                                onClick = { viewModel.clearArchiveStatus() }
                            ) {
                                Text("Tamam")
                            }
                        }
                    )
                }
                is ResultsViewModel.ArchiveStatus.Error -> {
                    AlertDialog(
                        onDismissRequest = { viewModel.clearArchiveStatus() },
                        title = { Text("Hata!") },
                        text = { Text(status.message) },
                        confirmButton = {
                            TextButton(
                                onClick = { viewModel.clearArchiveStatus() }
                            ) {
                                Text("Tamam")
                            }
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Henüz sonuç bulunmuyor",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            if (method == "LEAGUE") {
                LeagueResultsTabs(
                    listId = listId,
                    results = results,
                    method = method,
                    viewModel = viewModel
                )
            } else {
                Text(
                    text = "Final Sıralaması",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn {
                    itemsIndexed(results) { index, resultWithSong ->
                        ResultCard(
                            position = index + 1,
                            song = resultWithSong.second,
                            score = resultWithSong.first.score,
                            method = method,
                            teamId = if (method == "EMRE_CORRECT") resultWithSong.second.id else null,
                            headToHeadInfo = if (method == "EMRE_CORRECT") {
                                // Aynı puanlı takımlar için head-to-head bilgisi
                                val currentScore = resultWithSong.first.score
                                val samePointTeams = results.filter { it.first.score == currentScore }
                                if (samePointTeams.size > 1) {
                                    "H2H: ${index + 1}/${samePointTeams.size}"
                                } else null
                            } else null
                        )
                        
                        if (index < results.size - 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeagueResultsTabs(
    listId: Long,
    results: List<Pair<com.example.ranking.data.RankingResult, com.example.ranking.data.Song>>,
    method: String,
    viewModel: ResultsViewModel
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Final Sıralaması", "Puan Durumu", "Maç Özeti")
    
    Column {
        TabRow(selectedTabIndex = selectedTab) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        when (selectedTab) {
            0 -> {
                // Final Sıralaması
                Text(
                    text = "Final Sıralaması",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn {
                    itemsIndexed(results) { index, resultWithSong ->
                        ResultCard(
                            position = index + 1,
                            song = resultWithSong.second,
                            score = resultWithSong.first.score,
                            method = method,
                            teamId = if (method == "EMRE_CORRECT") resultWithSong.second.id else null,
                            headToHeadInfo = if (method == "EMRE_CORRECT") {
                                // Aynı puanlı takımlar için head-to-head bilgisi
                                val currentScore = resultWithSong.first.score
                                val samePointTeams = results.filter { it.first.score == currentScore }
                                if (samePointTeams.size > 1) {
                                    "H2H: ${index + 1}/${samePointTeams.size}"
                                } else null
                            } else null
                        )
                        
                        if (index < results.size - 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
            1 -> {
                // Puan Durumu - detailed league table
                LeagueTable(
                    listId = listId,
                    viewModel = viewModel
                )
            }
            2 -> {
                // Maç Özeti
                MatchSummary(
                    listId = listId,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun LeagueTable(
    listId: Long,
    viewModel: ResultsViewModel
) {
    LaunchedEffect(listId) {
        viewModel.loadLeagueTable(listId)
    }
    
    val leagueTable by viewModel.leagueTable.collectAsState()
    
    if (leagueTable.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        Text(
            text = "Detaylı Puan Durumu",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Header row
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sıra", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold)
                Text("Takım", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold)
                Text("O", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold)
                Text("G", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold)
                Text("B", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold)
                Text("M", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold)
                Text("A", modifier = Modifier.width(35.dp), fontWeight = FontWeight.Bold)
                Text("Y", modifier = Modifier.width(35.dp), fontWeight = FontWeight.Bold)
                Text("P", modifier = Modifier.width(35.dp), fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn {
            itemsIndexed(leagueTable) { index, tableEntry ->
                LeagueTableRow(
                    position = index + 1,
                    entry = tableEntry
                )
                
                if (index < leagueTable.size - 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable  
private fun LeagueTableRow(
    position: Int,
    entry: ResultsViewModel.LeagueTableEntry
) {
    val backgroundColor = when (position) {
        1 -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$position", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold)
            Text(entry.teamName, modifier = Modifier.weight(2f))
            Text("${entry.played}", modifier = Modifier.width(30.dp))
            Text("${entry.won}", modifier = Modifier.width(30.dp))
            Text("${entry.drawn}", modifier = Modifier.width(30.dp))
            Text("${entry.lost}", modifier = Modifier.width(30.dp))
            Text("${entry.goalsFor}", modifier = Modifier.width(35.dp))
            Text("${entry.goalsAgainst}", modifier = Modifier.width(35.dp))
            Text("${entry.points}", modifier = Modifier.width(35.dp), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MatchSummary(
    listId: Long,
    viewModel: ResultsViewModel
) {
    LaunchedEffect(listId) {
        viewModel.loadMatchSummary(listId)
    }
    
    val matchSummary by viewModel.matchSummary.collectAsState()
    
    Text(
        text = "Maç Özeti",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    if (matchSummary.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn {
            items(matchSummary.size) { index ->
                val match = matchSummary[index]
                MatchSummaryCard(match = match)
                if (index < matchSummary.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun MatchSummaryCard(
    match: ResultsViewModel.MatchSummaryItem
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = match.team1Name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (match.winnerId == match.team1Id) FontWeight.Bold else FontWeight.Normal
                )
                
                Text(
                    text = if (match.score1 != null && match.score2 != null) {
                        "${match.score1} - ${match.score2}"
                    } else {
                        when (match.winnerId) {
                            match.team1Id -> "1 - 0"
                            match.team2Id -> "0 - 1" 
                            null -> "0 - 0"
                            else -> "- - -"
                        }
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = match.team2Name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (match.winnerId == match.team2Id) FontWeight.Bold else FontWeight.Normal
                )
            }
            
            if (match.winnerId != null || (match.score1 == match.score2 && match.score1 != null)) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        match.winnerId == match.team1Id -> "${match.team1Name} Kazandı"
                        match.winnerId == match.team2Id -> "${match.team2Name} Kazandı"
                        else -> "Berabere"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ResultCard(
    position: Int,
    song: com.example.ranking.data.Song,
    score: Double,
    method: String,
    teamId: Long? = null,
    headToHeadInfo: String? = null
) {
    val backgroundColor = when (position) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> MaterialTheme.colorScheme.surface
    }
    
    val textColor = when (position) {
        1, 2, 3 -> Color.Black
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position with trophy icon for top 3 and Team ID
            Column(
                modifier = Modifier.width(60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    if (position <= 3) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Trophy",
                            tint = textColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "$position",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
                
                // Team ID (sadece EMRE_CORRECT için)
                if (method == "EMRE_CORRECT" && teamId != null) {
                    Text(
                        text = "T$teamId",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Song info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
                if (song.artist.isNotBlank()) {
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
                if (song.album.isNotBlank()) {
                    Text(
                        text = song.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.5f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Score and Head-to-Head info
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = formatScore(score, method),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = getScoreLabel(method),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f)
                )
                
                // Head-to-head bilgisi (sadece EMRE_CORRECT için ve aynı puanlı takımlar için)
                if (method == "EMRE_CORRECT" && !headToHeadInfo.isNullOrBlank()) {
                    Text(
                        text = headToHeadInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun formatScore(score: Double, method: String): String {
    return when (method) {
        "DIRECT_SCORING" -> "${score.toInt()}/100"
        "LEAGUE", "SWISS" -> "${score.toInt()} puan"
        "EMRE_CORRECT" -> "${score.toInt()}"
        else -> score.toString()
    }
}

private fun getScoreLabel(method: String): String {
    return when (method) {
        "DIRECT_SCORING" -> "puan"
        "LEAGUE" -> "lig puanı"
        "SWISS" -> "turnuva puanı"
        "EMRE_CORRECT" -> "sıra puanı"
        else -> ""
    }
}

@Composable
private fun ArchiveDialog(
    listId: Long,
    method: String,
    viewModel: ResultsViewModel,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var archiveName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Arşive Kaydet") },
        text = {
            Column {
                Text("Bu sıralamanın sonuçlarını arşive kaydetmek için bir isim girin:")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = archiveName,
                    onValueChange = { archiveName = it },
                    label = { Text("Arşiv İsmi") },
                    placeholder = { Text("Örn: Yılbaşı Listesi 2024") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(archiveName) },
                enabled = archiveName.isNotBlank()
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
        "EMRE_CORRECT" -> "Geliştirilmiş İsviçre Sistemi"
        else -> "Sıralama"
    }
}